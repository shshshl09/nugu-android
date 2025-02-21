package com.skt.nugu.sdk.agent.asr.realtime

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.AbstractCapabilityAgent
import com.skt.nugu.sdk.agent.DefaultASRAgent
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.asr.AsrNotifyResultPayload
import com.skt.nugu.sdk.agent.asr.EndPointDetectorParam
import com.skt.nugu.sdk.agent.asr.RequestType
import com.skt.nugu.sdk.agent.asr.SpeechRecognizer
import com.skt.nugu.sdk.agent.asr.WakeupInfo
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.BaseContextState
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextType
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandlerResult
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.util.concurrent.Executors

class TestRealtimeASRAgent(
    private val messageSender: MessageSender,
    private val contextManager: ContextManagerInterface,
): AbstractCapabilityAgent(NAMESPACE),
    ASRAgentInterface,
    SpeechRecognizer.OnStateChangeListener{
    companion object {
        private const val TAG = "TestRealtimeASRAgent"

        const val NAMESPACE = "ASR"
        val VERSION = Version(1,9)

        private fun buildCompactContext(): JsonObject = JsonObject().apply {
            addProperty("version", DefaultASRAgent.VERSION.toString())
        }

        const val NAME_EXPECT_SPEECH = "ExpectSpeech"
        const val NAME_RECOGNIZE = "Recognize"
        const val NAME_NOTIFY_RESULT = "NotifyResult"

        const val EVENT_STOP_RECOGNIZE = "StopRecognize"

        val EXPECT_SPEECH = NamespaceAndName(
            NAMESPACE,
            NAME_EXPECT_SPEECH
        )
        val RECOGNIZE = NamespaceAndName(
            NAMESPACE,
            NAME_RECOGNIZE
        )
        val NOTIFY_RESULT = NamespaceAndName(
            NAMESPACE,
            NAME_NOTIFY_RESULT
        )

        private val COMPACT_STATE: String = buildCompactContext().toString()
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var state: ASRAgentInterface.State = ASRAgentInterface.State.IDLE
    private var currentRequest: SpeechRecognizer.Request? = null

    private val speechToTextConverterEventObserver =
        object : ASRAgentInterface.OnResultListener {
            override fun onNoneResult(header: Header) {
                Logger.d(TAG, "[onNoneResult] $header")
                onResultListeners.forEach {
                    it.onNoneResult(header)
                }
            }

            override fun onPartialResult(result: String, header: Header) {
                Logger.d(TAG, "[onPartialResult] $result, $header")
                onResultListeners.forEach {
                    it.onPartialResult(result, header)
                }
            }

            override fun onCompleteResult(result: String, header: Header) {
                Logger.d(TAG, "[onCompleteResult] $result, $header")
                onResultListeners.forEach {
                    it.onCompleteResult(result, header)
                }
            }

            override fun onError(
                type: ASRAgentInterface.ErrorType,
                header: Header,
                allowEffectBeep: Boolean
            ) {
                Logger.w(TAG, "[onError] $type, $header, $allowEffectBeep")
                onResultListeners.forEach {
                    it.onError(type, header, allowEffectBeep)
                }
            }

            override fun onCancel(cause: ASRAgentInterface.CancelCause, header: Header) {
                Logger.d(TAG, "[onCancel] $cause, $header")
                onResultListeners.forEach {
                    it.onCancel(cause, header)
                }
            }
        }

    private val contextState = object : BaseContextState {
        override fun value(): String = COMPACT_STATE
    }

    private val onStateChangeListeners = HashSet<ASRAgentInterface.OnStateChangeListener>()
    private val onResultListeners = HashSet<ASRAgentInterface.OnResultListener>()
    private val recognizer = TestRealtimeSpeechRecognizer(TestRealtimeAudioEncoder(), messageSender).apply {
        addListener(this@TestRealtimeASRAgent)
    }

    internal data class StateContext(
        private val state: ASRAgentInterface.State,
        private val initiator: ASRAgentInterface.Initiator?
    ): BaseContextState {
        override fun value(): String = buildCompactContext().apply {
            addProperty("state", state.name)
            initiator?.let {
                addProperty("initiator",initiator.name)
            }
        }.toString()
    }

    override fun startRecognition(
        audioInputStream: SharedDataStream?,
        audioFormat: AudioFormat?,
        wakeupInfo: WakeupInfo?,    // ignored
        param: EndPointDetectorParam?,  // ignored
        service: String?,
        callback: ASRAgentInterface.StartRecognitionCallback?,
        initiator: ASRAgentInterface.Initiator,
        requestType: RequestType?
    ) {
        Logger.d(TAG, "[startRecognition] audioInputStream: $audioInputStream, initiator: $initiator, requestType: $requestType")

        executor.submit {
            if(audioInputStream == null || audioFormat == null) {
                Logger.w(TAG, "[startRecognition] cannot start recognize when audioInputStream: $audioInputStream, audioFormat: $audioFormat")
                callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_CANNOT_START_RECOGNIZER)
                return@submit
            }

            if(state == ASRAgentInterface.State.EXPECTING_SPEECH) {
                Logger.w(TAG, "[startRecognition] cannot start recognize when EXPECT_SPEECH state.")
                callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_ALREADY_RECOGNIZING)
                return@submit
            }

            Logger.d(TAG, "[executeStartRecognition] state: $state, initiator: $initiator")
            if (!canRecognizing()) {
                Logger.w(
                    TAG,
                    "[executeStartRecognition] StartRecognizing allowed in IDLE or BUSY state."
                )
                callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_ALREADY_RECOGNIZING)
                return@submit
            }

            contextManager.getContext(contextRequester = object : IgnoreErrorContextRequestor() {
                override fun onContext(jsonContext: String) {
                    executor.submit submitInner@{
                        Logger.d(TAG, "[executeStartRecognition::getContext::onContext] state: $state")

                        if (state == ASRAgentInterface.State.RECOGNIZING) {
                            Logger.e(
                                TAG,
                                "[executeStartRecognitionOnContextAvailable] Not permmited in current state: $state"
                            )

                            callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_ALREADY_RECOGNIZING)
                            return@submitInner
                        }

                        recognizer.start(
                            audioInputStream,
                            audioFormat,
                            jsonContext,
                            null,
                            null,
                            null,
                            EndPointDetectorParam(10), // will be ignord
                            service,
                            requestType,
                            speechToTextConverterEventObserver
                        ).also {
                            if(it == null) {
                                currentRequest = null
                                callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_CANNOT_START_RECOGNIZER)
                            } else {
                                currentRequest = it
                                callback?.onSuccess(it.eventMessage.dialogRequestId)
                            }
                        }
                    }
                }
            }, timeoutInMillis = 2000L, given = HashMap<NamespaceAndName, BaseContextState>().apply {
                put(namespaceAndName,
                    StateContext(state, initiator)
                )
            })
        }
    }

    private fun canRecognizing(): Boolean {
        // It is possible to start recognition internally when
        // * ASR State == IDLE
        // * ASR State == BUSY
        // * ASR State == EXPECTING_SPEECH (prepared state for DM)
        return !state.isRecognizing() || state == ASRAgentInterface.State.BUSY || state == ASRAgentInterface.State.EXPECTING_SPEECH
    }

    override fun stopRecognition(
        cancel: Boolean,
        cause: ASRAgentInterface.CancelCause,
        cancelPolicy: DirectiveHandlerResult.CancelPolicy
    ) {
        TODO("Not yet implemented")
    }

    override fun addOnStateChangeListener(listener: ASRAgentInterface.OnStateChangeListener) {
        Logger.d(TAG, "[addOnStateChangeListener] listener: $listener")
        executor.submit {
            onStateChangeListeners.add(listener)
        }
    }

    override fun removeOnStateChangeListener(listener: ASRAgentInterface.OnStateChangeListener) {
        Logger.d(TAG, "[removeOnStateChangeListener] listener: $listener")
        executor.submit {
            onStateChangeListeners.remove(listener)
        }
    }

    override fun addOnResultListener(listener: ASRAgentInterface.OnResultListener) {
        Logger.d(TAG, "[addOnResultListener] listener: $listener")
        executor.submit {
            onResultListeners.add(listener)
        }
    }

    override fun removeOnResultListener(listener: ASRAgentInterface.OnResultListener) {
        Logger.d(TAG, "[removeOnResultListener] listener: $listener")
        executor.submit {
            onResultListeners.remove(listener)
        }
    }

    override fun addOnMultiturnListener(listener: ASRAgentInterface.OnMultiturnListener) {
        // nothing.
    }

    override fun removeOnMultiturnListener(listener: ASRAgentInterface.OnMultiturnListener) {
        // nothing.
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // nothing
    }

    override fun handleDirective(info: DirectiveInfo) {
        when (info.directive.getNamespaceAndName()) {
            DefaultASRAgent.NOTIFY_RESULT -> handleNotifyResult(info)
            else -> {
                handleDirectiveException(info)
            }
        }
    }

    private fun handleNotifyResult(info: DirectiveInfo) {
        Logger.d(TAG, "[handleNotifyResult] $info")
        val directive = info.directive

        val notifyResultPayload =
            MessageFactory.create(directive.payload, AsrNotifyResultPayload::class.java)
        if (notifyResultPayload == null) {
            Logger.e(TAG, "[handleNotifyResult] invalid payload: ${directive.payload}")
            setHandlingFailed(
                info,
                "[handleNotifyResult] invalid payload: ${directive.payload}"
            )
            return
        }

        if (!notifyResultPayload.isValidPayload()) {
            Logger.e(TAG, "[handleNotifyResult] invalid payload : $notifyResultPayload")
            setHandlingFailed(
                info,
                "[handleNotifyResult] invalid payload : $notifyResultPayload"
            )
            return
        }

        executor.submit {
            recognizer.notifyResult(info.directive, notifyResultPayload)
            setHandlingCompleted(info)
        }
    }

    private fun handleDirectiveException(info: DirectiveInfo) {
        setHandlingFailed(info, "invalid directive")
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        Logger.d(TAG, "[executeSetHandlingCompleted] info: $info")
        info.result.setCompleted()
    }

    private fun setHandlingFailed(info: DirectiveInfo, msg: String) {
        Logger.d(TAG, "[executeSetHandlingFailed] info: $info")
        info.result.setFailed(msg)
    }

    override fun cancelDirective(info: DirectiveInfo) {
        // nothing.
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[EXPECT_SPEECH] = BlockingPolicy.sharedInstanceFactory.get(
            BlockingPolicy.MEDIUM_AUDIO,
            BlockingPolicy.MEDIUM_AUDIO_ONLY
        )
        this[NOTIFY_RESULT] = BlockingPolicy.sharedInstanceFactory.get()
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        Logger.d(TAG, "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken")

        if(contextType == ContextType.COMPACT) {
            contextSetter.setState(
                namespaceAndName,
                contextState,
                StateRefreshPolicy.ALWAYS,
                contextType,
                stateRequestToken
            )
        } else {
            contextSetter.setState(
                namespaceAndName,
                DefaultASRAgent.StateContext(
                    state, null
                ),
                StateRefreshPolicy.ALWAYS,
                contextType,
                stateRequestToken
            )
        }
    }

    override fun onStateChanged(state: SpeechRecognizer.State, request: SpeechRecognizer.Request) {
        Logger.d(TAG, "[SpeechProcessorInterface] state: $state, request: $request")
        executor.submit {
            val aipState = when (state) {
                SpeechRecognizer.State.EXPECTING_SPEECH -> {
                    ASRAgentInterface.State.LISTENING(ASRAgentInterface.Initiator.TAP)
                }
                SpeechRecognizer.State.SPEECH_START -> ASRAgentInterface.State.RECOGNIZING
                SpeechRecognizer.State.SPEECH_END -> {
                    ASRAgentInterface.State.BUSY
                }
                SpeechRecognizer.State.STOP -> {
                    currentRequest = null
                    ASRAgentInterface.State.IDLE
                }
            }
            setState(aipState)
        }
    }

    private fun setState(state: ASRAgentInterface.State) {
        if (this.state == state) {
            return
        }

        Logger.d(TAG, "[setState] $state")

        this.state = state

        for (listener in onStateChangeListeners) {
            listener.onStateChanged(state)
        }
    }
}
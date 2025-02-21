package com.skt.nugu.sdk.agent.asr.realtime

import com.google.gson.JsonParser
import com.skt.nugu.sdk.agent.DefaultASRAgent
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.asr.AsrNotifyResultPayload
import com.skt.nugu.sdk.agent.asr.AsrRecognizeEventPayload
import com.skt.nugu.sdk.agent.asr.EndPointDetectorParam
import com.skt.nugu.sdk.agent.asr.RequestType
import com.skt.nugu.sdk.agent.asr.SpeechRecognizer
import com.skt.nugu.sdk.agent.asr.WakeupInfo
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.asr.audio.Encoder
import com.skt.nugu.sdk.agent.asr.impl.SpeechRecognizeAttachmentSenderThread
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.core.interfaces.dialog.DialogAttribute
import com.skt.nugu.sdk.core.interfaces.message.Call
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.Preferences
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class TestRealtimeSpeechRecognizer(
    private val audioEncoder: Encoder,
    private val messageSender: MessageSender
): SpeechRecognizer {
    companion object {
        private const val TAG = "TestRealtimeSpeechRecognizer"

        private val dateFormat: DateFormat by lazy {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("GMT")
            }
        }
    }

    private val listeners = HashSet<SpeechRecognizer.OnStateChangeListener>()
    private var state: SpeechRecognizer.State = SpeechRecognizer.State.STOP
    private var currentRequest: RecognizeRequest? = null

    override var enablePartialResult: Boolean = true
        set(value) {
            if (field == value) {
                return
            }

            field = value
        }

    private data class RecognizeRequest(
        val senderThread: SpeechRecognizeAttachmentSenderThread,
        override val eventMessage: EventMessageRequest,
        override val attributeKey: String? = null,
        val resultListener: ASRAgentInterface.OnResultListener?
    ) : SpeechRecognizer.Request {
        var cancelCause: ASRAgentInterface.CancelCause? = null
        var recognizeEventCall: Call? = null

        val eventMessageHeader = with(eventMessage) {
            Header(dialogRequestId, messageId, name, namespace, version, referrerDialogRequestId)
        }

        override fun cancelRequest() {
            recognizeEventCall?.let {
                if(!it.isCanceled()) {
                    it.cancel()
                }
            }
        }
    }

    override fun start(
        audioInputStream: SharedDataStream,
        audioFormat: AudioFormat,
        context: String,
        wakeupInfo: WakeupInfo?,
        expectSpeechDirectiveParam: DefaultASRAgent.ExpectSpeechDirectiveParam?,
        attribute: DialogAttribute?,
        epdParam: EndPointDetectorParam,
        service: String?,
        requestType: RequestType?,
        resultListener: ASRAgentInterface.OnResultListener?
    ): SpeechRecognizer.Request? {
        Logger.d(
            TAG,
            "[start] currentInputPosition: ${audioInputStream.getPosition()}"
        )
        if(currentRequest != null) {
            return null
        }

        val jsonService = if (service != null) {
            JsonParser.parseString(service).asJsonObject
        } else {
            null
        }

        val eventMessage = EventMessageRequest.Builder(
            context,
            TestRealtimeASRAgent.RECOGNIZE.namespace,
            TestRealtimeASRAgent.RECOGNIZE.name,
            TestRealtimeASRAgent.VERSION.toString()
        ).payload(
            AsrRecognizeEventPayload(
                codec = audioEncoder.getCodecName().uppercase(Locale.getDefault()),
                playServiceId = attribute?.playServiceId,
                domainTypes = attribute?.domainTypes,
                endpointing = AsrRecognizeEventPayload.ENDPOINTING_SERVER,
                encoding = if (enablePartialResult) AsrRecognizeEventPayload.ENCODING_PARTIAL else AsrRecognizeEventPayload.ENCODING_COMPLETE,
                asrContext = attribute?.asrContext?.let { JsonParser.parseString(it).asJsonObject },
                timeout = null,
                service = jsonService,
                requestType = requestType
            ).toJsonString()
        ).isStreaming(true)
            .build()

        val thread = createSenderThread(
            audioInputStream,
            audioFormat,
            eventMessage,
        )

        val call = messageSender.newCall(
            eventMessage, hashMapOf("Last-Asr-Event-Time" to Preferences.get("Last-Asr-Event-Time").toString())
        )

        val recognizeRequest =
            RecognizeRequest(senderThread = thread, eventMessage = eventMessage, resultListener = resultListener)

        recognizeRequest.recognizeEventCall = call
        currentRequest = recognizeRequest

        call.enqueue(object : MessageSender.Callback {
            override fun onFailure(request: MessageRequest, status: Status) {
                Logger.w(TAG, "[start] failed to send recognize event")
                val errorType = when (status.error) {
                    Status.StatusError.OK,
                    Status.StatusError.TIMEOUT -> return
                    Status.StatusError.NETWORK -> ASRAgentInterface.ErrorType.ERROR_NETWORK
                    else -> ASRAgentInterface.ErrorType.ERROR_UNKNOWN
                }
                handleError(errorType, recognizeRequest.eventMessageHeader)
            }

            override fun onSuccess(request: MessageRequest) {
            }

            override fun onResponseStart(request: MessageRequest) {
                Preferences.set("Last-Asr-Event-Time", dateFormat.format(Calendar.getInstance().time))
            }
        }).also {
            thread.start()
            setState(SpeechRecognizer.State.EXPECTING_SPEECH, recognizeRequest)
        }

        return recognizeRequest
    }

    override fun stop(cancel: Boolean, cause: ASRAgentInterface.CancelCause) {
        if(cancel) {
            currentRequest?.cancelCause = cause
            currentRequest?.senderThread?.requestStop()
            currentRequest?.recognizeEventCall?.cancel()
        } else {
            currentRequest?.senderThread?.requestFinish()
        }
    }

    override fun isRecognizing(): Boolean = currentRequest != null

    override fun addListener(listener: SpeechRecognizer.OnStateChangeListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: SpeechRecognizer.OnStateChangeListener) {
        listeners.remove(listener)
    }

    override fun notifyResult(directive: Directive, payload: AsrNotifyResultPayload) {
        val request = currentRequest ?: return

        Logger.d(TAG, "[notifyResult] $payload, listener: $request")

        val resultListener: ASRAgentInterface.OnResultListener? = request.resultListener

        when (payload.state) {
            AsrNotifyResultPayload.State.PARTIAL -> {
                resultListener?.onPartialResult(payload.result ?: "", directive.header)
            }
            AsrNotifyResultPayload.State.COMPLETE -> {
                resultListener?.onCompleteResult(payload.result ?: "", directive.header)
            }
            AsrNotifyResultPayload.State.NONE -> {
                resultListener?.onNoneResult(directive.header)
            }
            AsrNotifyResultPayload.State.ERROR -> {
                request.senderThread.requestStop()
                handleError(ASRAgentInterface.ErrorType.ERROR_UNKNOWN, directive.header)
            }
            AsrNotifyResultPayload.State.FA -> {
                // TODO : Impl
            }
            AsrNotifyResultPayload.State.SOS -> {
                setState(SpeechRecognizer.State.SPEECH_START, request)
            }
            AsrNotifyResultPayload.State.EOS -> {
                setState(SpeechRecognizer.State.SPEECH_END, request)
            }
        }
    }

    private fun createSenderThread(
        audioInputStream: SharedDataStream,
        audioFormat: AudioFormat,
        eventMessage: EventMessageRequest
    ): SpeechRecognizeAttachmentSenderThread {
        Logger.d(
            TAG,
            "[createSenderThread] bytesPerMillis : ${audioFormat.getBytesPerMillis()}"
        )

        return SpeechRecognizeAttachmentSenderThread(
            audioInputStream.createReader(),
            audioFormat,
            messageSender,
            object :
                SpeechRecognizeAttachmentSenderThread.RecognizeSenderObserver {
                override fun onFinish() {
                    // ignore
                }

                override fun onStop() {
                    handleCancel()
                    sendStopRecognizeEvent(eventMessage)
                }

                override fun onError(errorType: ASRAgentInterface.ErrorType) {
                    handleError(errorType, with(eventMessage) {
                        Header(dialogRequestId, messageId, name, namespace, version, referrerDialogRequestId)
                    })
                }
            },
            audioEncoder,
            eventMessage
        )
    }

    private fun sendStopRecognizeEvent(request: EventMessageRequest): Boolean {
        Logger.d(TAG, "[sendStopRecognizeEvent] $this")
        return messageSender.newCall(
            EventMessageRequest.Builder(
                request.context,
                TestRealtimeASRAgent.NAMESPACE,
                TestRealtimeASRAgent.EVENT_STOP_RECOGNIZE,
                TestRealtimeASRAgent.VERSION.toString()
            ).referrerDialogRequestId(request.dialogRequestId).build()
        ).enqueue(null)
    }

    private fun handleError(errorType: ASRAgentInterface.ErrorType, header: Header) {
        currentRequest?.let {
            it.resultListener?.onError(errorType, header)
            currentRequest = null
            setState(SpeechRecognizer.State.STOP, it)
        }
    }

    private fun handleCancel() {
        currentRequest?.let {
            it.resultListener?.onCancel(it.cancelCause ?: ASRAgentInterface.CancelCause.LOCAL_API, it.eventMessageHeader)
            currentRequest = null
            setState(SpeechRecognizer.State.STOP, it)
        }
    }

    private fun setState(state: SpeechRecognizer.State, request: SpeechRecognizer.Request) {
        Logger.d(TAG, "[setState] prev: ${this.state} / next: $state")

        this.state = state

        notifyObservers(state, request)
    }

    private fun notifyObservers(state: SpeechRecognizer.State, request: SpeechRecognizer.Request) {
        listeners.forEach {
            it.onStateChanged(state, request)
        }
    }
}
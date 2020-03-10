package com.skt.nugu.sdk.agent.audioplayer.playback

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.DefaultAudioPlayerAgent
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerPlaybackInfoProvider
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest

class AudioPlayerRequestPlaybackCommandDirectiveHandler(
    private val messageSender: MessageSender,
    private val contextGetter: ContextGetterInterface,
    private val playbackInfoProvider: AudioPlayerPlaybackInfoProvider
): AbstractDirectiveHandler() {
    companion object {
        const val NAMESPACE =
            DefaultAudioPlayerAgent.NAMESPACE
        const val VERSION =
            DefaultAudioPlayerAgent.VERSION

        // v1.2
        private const val NAME_REQUEST = "Request"

        private const val NAME_RESUME = "Resume"
        private const val NAME_NEXT = "Next"
        private const val NAME_PREVIOUS = "Previous"
        private const val NAME_PAUSE = "Pause"
        private const val NAME_STOP = "Stop"

        private const val NAME_COMMAND = "Command"

        private const val NAME_ISSUED = "Issued"

        private val REQUEST_RESUME_COMMAND = NamespaceAndName(NAMESPACE, "$NAME_REQUEST$NAME_RESUME$NAME_COMMAND")
        private val REQUEST_NEXT_COMMAND = NamespaceAndName(NAMESPACE, "$NAME_REQUEST$NAME_NEXT$NAME_COMMAND")
        private val REQUEST_PREVIOUS_COMMAND = NamespaceAndName(NAMESPACE, "$NAME_REQUEST$NAME_PREVIOUS$NAME_COMMAND")
        private val REQUEST_PAUSE_COMMAND = NamespaceAndName(NAMESPACE, "$NAME_REQUEST$NAME_PAUSE$NAME_COMMAND")
        private val REQUEST_STOP_COMMAND = NamespaceAndName(NAMESPACE, "$NAME_REQUEST$NAME_STOP$NAME_COMMAND")

    }

    private var handler: AudioPlayerAgentInterface.RequestCommandHandler? = null

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        setHandlingCompleted(info)
        contextGetter.getContext(object: ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                val token = playbackInfoProvider.getToken()
                val offsetInMilliseconds = playbackInfoProvider.getOffsetInMilliseconds()
                val playServiceId = playbackInfoProvider.getPlayServiceId()

                if(token.isNullOrBlank() || offsetInMilliseconds == null || playServiceId.isNullOrBlank()) {
                    return
                }

                if(handler?.handleRequestCommand(info.directive.payload, info.directive.header) != true) {
                    val header = info.directive.header
                    val message = EventMessageRequest.Builder(
                            jsonContext,
                            header.namespace,
                            "${header.name}$NAME_ISSUED",
                            VERSION
                        )
                        .payload(JsonObject().apply {
                            addProperty("token", token)
                            addProperty("offsetInMilliseconds", offsetInMilliseconds)
                            addProperty("playServiceId", playServiceId)
                        }.toString())
                        .build()

                    messageSender.sendMessage(message)
                }
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
            }
        }, NamespaceAndName("supportedInterfaces", NAMESPACE))
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
        removeDirective(info.directive.getMessageId())
    }

    override fun cancelDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val configurations = HashMap<NamespaceAndName, BlockingPolicy>()
        val nonBlockingPolicy = BlockingPolicy()

        configurations[REQUEST_RESUME_COMMAND] = nonBlockingPolicy
        configurations[REQUEST_NEXT_COMMAND] = nonBlockingPolicy
        configurations[REQUEST_PREVIOUS_COMMAND] = nonBlockingPolicy
        configurations[REQUEST_PAUSE_COMMAND] = nonBlockingPolicy
        configurations[REQUEST_STOP_COMMAND] = nonBlockingPolicy

        return configurations
    }

    fun setRequestCommandHandler(handler: AudioPlayerAgentInterface.RequestCommandHandler) {
        this.handler = handler
    }
}
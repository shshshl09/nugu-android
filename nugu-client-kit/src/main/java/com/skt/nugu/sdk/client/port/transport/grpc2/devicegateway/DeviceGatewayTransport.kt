package com.skt.nugu.sdk.client.port.transport.grpc2.devicegateway

import com.google.protobuf.ByteString
import com.skt.nugu.sdk.client.port.transport.grpc.Backoff
import com.skt.nugu.sdk.client.port.transport.grpc.Options
import com.skt.nugu.sdk.client.port.transport.grpc2.utils.ChannelBuilderUtils
import com.skt.nugu.sdk.core.interfaces.message.MessageConsumer
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.interfaces.transport.TransportListener
import com.skt.nugu.sdk.core.network.request.AttachmentMessageRequest
import com.skt.nugu.sdk.core.network.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.*
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.util.concurrent.ConcurrentLinkedQueue

class DeviceGatewayTransport(policyResponse: PolicyResponse,
                             private val messageConsumer: MessageConsumer,
                             private val transportObserver: TransportListener,
                             private val authorization: String)
    : Transport
    , PingService.Observer
    , EventStreamService.Observer {
    companion object {
        private const val TAG = "DeviceGatewayTransport"
    }

    private val policies = ConcurrentLinkedQueue(policyResponse.serverPolicyList)
    private val backoff = Backoff().apply {
        healthCheckTimeout = policyResponse.healthCheckPolicy.healthCheckTimeout.toLong()
        retryDelay = policyResponse.healthCheckPolicy.retryDelay.toLong()
        maxAttempts = policyResponse.healthCheckPolicy.retryCountLimit
    }

    private var currentChannel: ManagedChannel? = null
    private var pingService: PingService? = null
    private var eventStreamService: EventStreamService? = null
    @Volatile
    private var isConnected = false

    override fun connect(): Boolean {
        if(isConnected) {
            return false
        }

        val policy = policies.poll()
        val option = Options(
            address = policy.address,
            port = policy.port,
            retryCountLimit = policy.retryCountLimit,
            connectionTimeout = policy.connectionTimeout,
            hostname = policy.hostName
        )
        with(backoff) {
            maxAttempts = policy.retryCountLimit
            reset()
        }

        currentChannel = ChannelBuilderUtils.createChannelBuilderWith(option, authorization).build().also {
            pingService = PingService(VoiceServiceGrpc.newBlockingStub(it), backoff.healthCheckTimeout, backoff.retryDelay, this@DeviceGatewayTransport)
            eventStreamService = EventStreamService(VoiceServiceGrpc.newStub(it), this@DeviceGatewayTransport)
        }

        return true
    }

    override fun disconnect() {
        currentChannel?.shutdownNow()
        currentChannel = null
        pingService?.shutdown()
        pingService = null
        eventStreamService?.shutdown()
        eventStreamService = null
        isConnected = false
    }

    override fun isConnected(): Boolean = isConnected

    override fun send(request: MessageRequest) {
        eventStreamService?.let {
            when(request) {
                is AttachmentMessageRequest -> it.sendAttachmentMessage(toProtobufMessage(request))
                is EventMessageRequest -> it.sendEventMessage(toProtobufMessage(request))
            }
        }
    }

    override fun sendCompleted() {
        // nothing
    }

    override fun sendPostConnectMessage(request: MessageRequest) {
        // nothing
    }

    override fun shutdown() {
        disconnect()
    }

    override fun onHandoffConnection(
        protocol: String,
        domain: String,
        hostname: String,
        port: Int,
        retryCountLimit: Int,
        connectionTimeout: Int,
        charge: String
    ) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onPingRequestAcknowledged(success: Boolean) {
        if(success) {
            // connected
            if(!isConnected) {
                isConnected = true
                transportObserver.onConnected(this)
            }
        } else {
            if(isConnected) {
                isConnected = false
                //transportObserver.onDis
            }
        }

        Logger.d(TAG, "onPingRequestAcknowledged $success")
    }

    override fun onReceiveDirectives(json: String) {
        messageConsumer.consumeMessage(json)
    }

    override fun onReceiveAttachment(json: String) {
        messageConsumer.consumeMessage(json)
    }

    private fun toProtobufMessage(request: AttachmentMessageRequest): AttachmentMessage {
        with(request) {
            val attachment = Attachment.newBuilder()
                .setHeader(
                    Header.newBuilder()
                        .setNamespace(namespace)
                        .setName(name)
                        .setMessageId(messageId)
                        .setDialogRequestId(dialogRequestId)
                        .setVersion(version)
                        .build()
                )
                .setSeq(seq)
                .setIsEnd(isEnd)
                .setContent(
                    if (byteArray != null) {
                        ByteString.copyFrom(byteArray)
                    } else {
                        ByteString.EMPTY
                    }
                )
                .build()

            return AttachmentMessage.newBuilder()
                .setAttachment(attachment).build()
        }
    }

    private fun toProtobufMessage(request: EventMessageRequest): EventMessage {
        with(request) {
            val event = Event.newBuilder()
                .setHeader(
                    Header.newBuilder()
                        .setNamespace(namespace)
                        .setName(name)
                        .setMessageId(messageId)
                        .setDialogRequestId(dialogRequestId)
                        .setVersion(version)
                        .build()
                )
                .setPayload(payload)
                .build()

            return EventMessage.newBuilder()
                .setContext(context)
                .setEvent(event)
                .build()
        }
    }
}
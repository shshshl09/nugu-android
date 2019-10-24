package com.skt.nugu.sdk.client.port.transport.grpc2

import com.skt.nugu.sdk.client.port.transport.grpc.Options
import com.skt.nugu.sdk.client.port.transport.grpc2.devicegateway.DeviceGatewayTransport
import com.skt.nugu.sdk.client.port.transport.grpc2.utils.ChannelBuilderUtils
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.auth.AuthStateListener
import com.skt.nugu.sdk.core.interfaces.message.MessageConsumer
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.interfaces.transport.TransportListener
import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.PolicyRequest
import devicegateway.grpc.PolicyResponse
import devicegateway.grpc.RegistryGrpc
import io.grpc.Status
import io.grpc.stub.StreamObserver

class GrpcTransport2 private constructor(
    private val registryServerOption: Options,
    private val authDelegate: AuthDelegate,
    private val messageConsumer: MessageConsumer,
    private val transportObserver: TransportListener
) : Transport, AuthStateListener {

    /**
     * Transport Constructor.
     */
    companion object {
        private const val TAG = "GrpcTransport2"

        fun create(
            opts: Options,
            authDelegate: AuthDelegate,
            messageConsumer: MessageConsumer,
            transportObserver: TransportListener
        ): Transport {
            return GrpcTransport2(
                opts,
                authDelegate,
                messageConsumer,
                transportObserver
            )
        }
    }

    /**
     * Enum to Connection State of Transport
     */
    private enum class State {
        INIT,
        WAITING_POLICY,
        CONNECTING,
        CONNECTED,
    }

    private var currentPolicy: PolicyResponse? = null
    private var state = State.INIT

    private var deviceGatewayTransport: DeviceGatewayTransport? = null

    override fun connect(): Boolean {
        if (state == State.CONNECTED || state == State.WAITING_POLICY || state == State.CONNECTING) {
            return false
        }

        val authorization = authDelegate.getAuthorization()
        if (authorization.isNullOrBlank()) {
            Logger.w(TAG, "empty authorization")
            authDelegate.onAuthFailure(authorization)
            return false
        }

        val policy = currentPolicy
        if (policy == null || policy.serverPolicyCount == 0) {
            tryGetPolicy(authorization)
        } else {
            tryConnectToDeviceGateway(policy, authorization)
        }

        return true
    }

    private fun tryGetPolicy(authorization: String) {
        state = State.WAITING_POLICY

        val registryChannel = ChannelBuilderUtils.createChannelBuilderWith(registryServerOption, authorization).build()
        RegistryGrpc.newStub(registryChannel).getPolicy(
            PolicyRequest.newBuilder().build(),
            object : StreamObserver<PolicyResponse> {
                override fun onNext(value: PolicyResponse?) {
                    Logger.d(TAG, "[onNext] $value")
                    currentPolicy = value
                }

                override fun onError(t: Throwable?) {
                    registryChannel.shutdownNow()
                    Logger.e(TAG, "[onError] error on getPolicy()", t)
                    val status = Status.fromThrowable(t)
                    if (status.code == Status.Code.UNAUTHENTICATED) {
                        // TODO : XXX 상태를 초기화해야할 듯.
                        authDelegate.onAuthFailure(authorization)
                    } else {
                        // TODO : RETRY??? CHECK IT!
                    }
                }

                override fun onCompleted() {
                    Logger.d(TAG, "[onCompleted]")
                    registryChannel.shutdownNow()
                    val policy = currentPolicy
                    if (policy == null) {
                        // TODO : RETRY???
                    } else {
                        tryConnectToDeviceGateway(policy, authorization)
                    }
                }
            })
    }

    private fun tryConnectToDeviceGateway(policy: PolicyResponse, authorization: String): Boolean {
        state = State.CONNECTING

        return DeviceGatewayTransport(
            policy,
            messageConsumer,
            transportObserver,
            authorization
        ).let {
            deviceGatewayTransport = it
            return it.connect()
        }
    }

    override fun disconnect() {
        deviceGatewayTransport?.disconnect()
        deviceGatewayTransport = null
    }

    override fun isConnected(): Boolean = deviceGatewayTransport?.isConnected() ?: false

    override fun send(request: MessageRequest) {
        deviceGatewayTransport?.send(request)
    }

    override fun sendCompleted() {
        deviceGatewayTransport?.sendCompleted()
    }

    override fun sendPostConnectMessage(request: MessageRequest) {
        deviceGatewayTransport?.sendPostConnectMessage(request)
    }

    override fun shutdown() {
        deviceGatewayTransport?.shutdown()
        deviceGatewayTransport = null
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
        val transport = deviceGatewayTransport
        if (transport != null) {
            transport.onHandoffConnection(
                protocol,
                domain,
                hostname,
                port,
                retryCountLimit,
                connectionTimeout,
                charge
            )
        } else {
            val policyResponse = PolicyResponse.newBuilder()
                .addServerPolicy(
                    PolicyResponse.ServerPolicy.newBuilder()
                        .setPort(port)
                        .setHostName(domain)
                        .setAddress(hostname)
                        .setRetryCountLimit(retryCountLimit)
                        .setConnectionTimeout(connectionTimeout)
                ).build()

            val authorization = authDelegate.getAuthorization()
            if (authorization.isNullOrBlank()) {
                Logger.w(TAG, "empty authorization")
                authDelegate.onAuthFailure(authorization)
                return
            }

            tryConnectToDeviceGateway(policyResponse, authorization)
        }
    }

    override fun onAuthStateChanged(newState: AuthStateListener.State): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
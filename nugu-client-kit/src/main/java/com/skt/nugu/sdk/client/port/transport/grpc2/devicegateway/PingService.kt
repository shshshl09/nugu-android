package com.skt.nugu.sdk.client.port.transport.grpc2.devicegateway

import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.PingRequest
import devicegateway.grpc.VoiceServiceGrpc
import io.grpc.Status
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class PingService(
    blockingStub: VoiceServiceGrpc.VoiceServiceBlockingStub,
    timeout: Long,
    pingInterval: Long,
    observer: Observer
) {
    companion object {
        private const val TAG = "PingService"
        private const val defaultInterval: Long = 1000 * 30L
        private const val defaultTimeout: Long = 1000 * 10L
    }

    private val executorService: ScheduledThreadPoolExecutor =
        ScheduledThreadPoolExecutor(1).apply {
            removeOnCancelPolicy = true
        }

    private var intervalFuture: ScheduledFuture<*>? = null

    @Volatile
    private var isShutdown = false

    interface Observer {
        fun onPingRequestAcknowledged(success: Boolean)
    }

    init {
        intervalFuture = executorService.scheduleWithFixedDelay({
            try {
                val response = blockingStub.withDeadlineAfter(
                    if (timeout > 0) timeout else defaultTimeout,
                    TimeUnit.MILLISECONDS
                ).ping(PingRequest.newBuilder().build())
                if (!isShutdown) {
                    observer.onPingRequestAcknowledged(true)
                }
            } catch (th: Throwable) {
                val status = Status.fromThrowable(th)
                Logger.w(TAG, "[PingService] $status", th)
                if (!isShutdown) {
                    observer.onPingRequestAcknowledged(false)
                }
            }
        }, 0, if (pingInterval > 0) pingInterval else defaultInterval, TimeUnit.MILLISECONDS)
    }

    fun shutdown() {
        if (isShutdown) {
            Logger.w(TAG, "[shutdown] already shutdown")
            return
        }

        isShutdown = true
        intervalFuture?.cancel(true)
        intervalFuture = null
        executorService.shutdown()
    }
}
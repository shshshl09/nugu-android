package com.skt.nugu.sdk.client.port.transport.grpc2.devicegateway

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.FieldNamingStrategy
import com.google.gson.GsonBuilder
import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.*
import io.grpc.stub.StreamObserver
import java.lang.reflect.Field
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class EventStreamService(asyncStub: VoiceServiceGrpc.VoiceServiceStub, private val observer: Observer) {
    companion object {
        private const val TAG = "EventStreamService"
    }

    interface Observer {
        fun onReceiveDirectives(json: String)
        fun onReceiveAttachment(json: String)
    }

    private val lock: Lock = ReentrantLock()

    private var eventStream: StreamObserver<Upstream>? = asyncStub.withWaitForReady().eventStream(object : StreamObserver<Downstream> {
        override fun onNext(downstream: Downstream) {
            Logger.d(TAG, "[EventStreamService] onNext : ${downstream.messageCase}")
            when (downstream.messageCase) {
                Downstream.MessageCase.DIRECTIVE_MESSAGE -> {
                    downstream.directiveMessage?.let {
                        if (it.directivesCount > 0) {
                            observer.onReceiveDirectives(toJson(downstream.directiveMessage))
                        }
                    }
                }
                Downstream.MessageCase.ATTACHMENT_MESSAGE -> {
                    downstream.attachmentMessage?.let {
                        if (it.hasAttachment()) {
                            observer.onReceiveAttachment(toJson(downstream.attachmentMessage))
                        }
                    }
                }
                else -> {
                    // nothing
                }
            }
        }

        override fun onError(t: Throwable) {
            Logger.e(TAG, "[EventStreamService] onError : $t")
            lock.withLock {
                eventStream = null
            }
        }

        override fun onCompleted() {
            Logger.e(TAG, "[EventStreamService] onCompleted")
            lock.withLock {
                eventStream = null
            }
        }

        private fun toJson(src: Any): String {
            return GsonBuilder().setFieldNamingStrategy(UnderscoresNamingStrategy())
                .addSerializationExclusionStrategy(UnknownFieldsExclusionStrategy())
                .create().toJson(src)
        }
        // directives_ to directives
        inner class UnderscoresNamingStrategy : FieldNamingStrategy {
            override fun translateName(f: Field): String {
                val index = f.name.lastIndexOf("_")
                return if (index == -1 || index != f.name.lastIndex) {
                    f.name
                } else {
                    f.name.substring(0, index)
                }
            }
        }

        inner class UnknownFieldsExclusionStrategy : ExclusionStrategy {
            override fun shouldSkipField(f: FieldAttributes): Boolean {
                return when (f.name) {
                    "unknownFields",
                    "memoizedSerializedSize",
                    "memoizedHashCode" -> true
                    else -> false
                }
            }

            override fun shouldSkipClass(clazz: Class<*>): Boolean {
                return false
            }
        }
    })

    fun sendAttachmentMessage(attachment: AttachmentMessage) {
        eventStream?.onNext(Upstream.newBuilder()
            .setAttachmentMessage(attachment)
            .build())
    }

    fun sendEventMessage(event: EventMessage) {
        eventStream?.onNext(Upstream.newBuilder()
            .setEventMessage(event)
            .build())
    }

    fun shutdown() {
        lock.withLock {
            eventStream?.onCompleted()
            eventStream = null
        }
    }
}
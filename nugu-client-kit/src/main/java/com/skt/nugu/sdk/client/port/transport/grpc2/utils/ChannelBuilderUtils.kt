package com.skt.nugu.sdk.client.port.transport.grpc2.utils

import com.skt.nugu.sdk.client.port.transport.grpc.Options
import com.skt.nugu.sdk.client.port.transport.grpc.core.HeaderClientInterceptor
import com.skt.nugu.sdk.core.utils.SdkVersion
import io.grpc.ChannelLogger
import io.grpc.ManagedChannelBuilder
import java.util.logging.Level

class ChannelBuilderUtils {
    companion object {
        fun createChannelBuilderWith(
            options: Options,
            authorization: String
        ): ManagedChannelBuilder<*> {
            val builder = ManagedChannelBuilder
                .forAddress(options.address, options.port)
                .userAgent(userAgent())

            if (!options.hostname.isBlank()) {
                builder.overrideAuthority(options.hostname)
            }

            if (options.debug) {
                // adb shell setprop log.tag.io.grpc.ChannelLogger DEBUG
                builder.maxTraceEvents(100)
                val logger = java.util.logging.Logger.getLogger(ChannelLogger::class.java.name)
                logger.level = Level.ALL
            }

            return builder.intercept(HeaderClientInterceptor(authorization))
        }

        private fun userAgent(): String {
            return "OpenSDK/" + SdkVersion.currentVersion
        }
    }
}
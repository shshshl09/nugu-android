/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.skt.nugu.sampleapp.utils

import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.asr.WakeupInfo
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.external.jademarble.SpeexEncoder
import com.skt.nugu.sdk.platform.android.speechrecognizer.SpeechRecognizerAggregatorInterface
import java.io.ByteArrayOutputStream
import java.util.*

class WakeupPCMCollector(private val listener: Listener) : SpeechRecognizerAggregatorInterface.TriggerCallback, ASRAgentInterface.StartRecognitionCallback {
    companion object {
        private const val TAG = "WakeupPCMCollector"
    }

    interface Listener {
        fun onCollect(dialogRequestId: String, buffer: ByteArray)
    }

    private data class Chunk(
        val position: Long,
        val buffer: ByteArray
    )

    private inner class CollectThread(
        inputStream: SharedDataStream,
        private val format: AudioFormat
    ): Thread()
        , SpeechRecognizerAggregatorInterface.TriggerCallback
        , ASRAgentInterface.StartRecognitionCallback {
        private val reader = inputStream.createReader()
        private val chunkMillis = 100 // 100ms
        private val chunkSize = format.getBytesPerMillis() * chunkMillis

        private val chunksMillis = 10 * 1000 // 10sec
        private val chunksSize = chunksMillis / chunkMillis
        private val chunks = ArrayDeque<Chunk>()
        private var wakeupBoundary: WakeupInfo.Boundary? = null
        private var isRunning = false

        override fun run() {
            super.run()
            try {
                isRunning = true
                var lastPosition: Long
                do {
                    val chunk = ByteArray(chunkSize)
                    val currentPosition = reader.position()
                    var read: Int
                    if (kotlin.run { read = reader.read(chunk, 0, chunk.size); read } > 0) {
                        if (chunkSize == read) {
                            chunks.addLast(Chunk(currentPosition, chunk))
                        } else {
                            val temp = ByteArray(read)
                            System.arraycopy(chunk, 0, temp, 0, read)
                            chunks.addLast(Chunk(currentPosition, temp))
                        }

                        if (chunks.size > chunksSize) {
                            chunks.removeFirst()
                        }
                    }

                    lastPosition = reader.position()

                } while (wakeupBoundary == null || lastPosition < (wakeupBoundary?.detectPosition ?: Long.MAX_VALUE))
            } finally {
                if (wakeupBoundary?.startPosition == -1L) {
                    chunks.clear()
                }

                try {
                    reader.close()
                } catch (th: Throwable) {
                    // ignore
                }

                isRunning = false
            }
        }

        override fun onTriggerStarted(inputStream: SharedDataStream, format: AudioFormat) {
            // no-op
        }

        override fun onTriggerFinished(wakeupInfo: WakeupInfo?) {
            wakeupBoundary = if (wakeupInfo == null) {
                WakeupInfo.Boundary(-1L, -1L, -1L)
            } else {
                wakeupInfo.boundary
            }
        }

        override fun onSuccess(dialogRequestId: String) {
            val boundaryInInputStream = wakeupBoundary
            if (boundaryInInputStream != null && boundaryInInputStream.startPosition != -1L) {
                Thread {
                    // wait collectThread finish
                    while(isRunning) {
                        sleep(100)
                    }

                    // compute boundary data
                    val initialPosition = chunks.first.position
                    val boundaryInChunks = WakeupInfo.Boundary(
                        boundaryInInputStream.startPosition - initialPosition,
                        boundaryInInputStream.endPosition - initialPosition,
                        boundaryInInputStream.detectPosition - initialPosition
                    )
                    // create speex
                    with(SpeexEncoder()) {
                        var commentAppended = false
                        startEncoding(format)
                        val encoded = ByteArrayOutputStream().use { os ->
                            chunks.forEach {
                                // append speex comment
                                val encoded = encode(it.buffer, 0, it.buffer.size)
                                if (encoded != null) {
                                    if(!commentAppended) {
                                        commentAppended = commentSpeex(encoded, boundaryInChunks, dialogRequestId)
                                    }
                                    os.write(encoded)
                                }
                            }
                            os.toByteArray()
                        }

                        stopEncoding()

                        listener.onCollect(dialogRequestId, encoded)
                    }
                }.start()
            }
        }

        private fun commentSpeex(
            encoded: ByteArray,
            boundaryInChunks: WakeupInfo.Boundary,
            dialogRequestId: String
        ): Boolean {
            Logger.d(TAG, "[commentSpeex] ${encoded.size}")
            if (encoded.size < 512) {
                return false
            }

            var pos = KPM.indexOf(encoded, 0, 512, "asrIdx=".toByteArray())
            Logger.d(TAG, "[commentSpeex] start comment position: $pos ${encoded.size}")

            if (pos >= 0) {
                //write dialogRequestId
                var value = "dialogRequestId=$dialogRequestId".toByteArray()
                System.arraycopy(value, 0, encoded, pos, value.size)
                pos += value.size

                //trigger start position
                value = ";${boundaryInChunks.startPosition}".toByteArray()
                System.arraycopy(value, 0, encoded, pos, value.size)
                pos += value.size

                // trigger end position
                value = ";${boundaryInChunks.endPosition}".toByteArray()
                System.arraycopy(value, 0, encoded, pos, value.size)
                pos += value.size

                // trigger detect position
                value = ";${boundaryInChunks.detectPosition}".toByteArray()
                System.arraycopy(value, 0, encoded, pos, value.size)
                pos += value.size
                Logger.d(TAG, "[commentSpeex] end comment position: $pos")

                return true

//                msg = encoder.getAsrIdx()
//                if (msg != null && !msg.trim({ it <= ' ' }).isEmpty()) {
//                    value = msg.trim({ it <= ' ' }).toByteArray()
//                    using = value.size
//                    if (used + using <= 128) {    //128 is reserved size at the comment
//                        System.arraycopy(value, 0, speex_data, pos, using)
//                        used += using
//                        pos += using
//                    }
//                }
//                System.arraycopy(msg.toByteArray(), 0, speex_data, pos, using)
//                using = msg.toByteArray().size
//                if (used + using <= 128) {
//                    System.arraycopy(msg.toByteArray(), 0, speex_data, pos, using)
//                    used += using
//                    pos += using
//                }
//                //trigger end position
//                msg = ";" + java.lang.Long.toString(encoder.getTriggerEndPos())
//                value = msg.toByteArray()
//                using = msg.toByteArray().size
//                if (used + using <= 128) {
//                    System.arraycopy(msg.toByteArray(), 0, speex_data, pos, using)
//                    used += using
//                    pos += using
//                }
//                //trigger detection position
//                msg = ";" + java.lang.Long.toString(encoder.getTriggerDetectionPos())
//                value = msg.toByteArray()
//                using = msg.toByteArray().size
//                if (used + using <= 128) {
//                    System.arraycopy(msg.toByteArray(), 0, speex_data, pos, using)
//                    used += using
//                    pos += using
//                }
            }

            return false
        }

        override fun onError(
            dialogRequestId: String,
            errorType: ASRAgentInterface.StartRecognitionCallback.ErrorType
        ) {
            // no-op
            Logger.d(TAG, "[onError] $dialogRequestId, $errorType")
        }
    }

    private var collectThread: CollectThread? = null

    override fun onTriggerStarted(inputStream: SharedDataStream, format: AudioFormat) {
        collectThread = CollectThread(inputStream, format).apply {
            start()
            onTriggerStarted(inputStream, format)
        }
    }

    override fun onTriggerFinished(wakeupInfo: WakeupInfo?) {
        collectThread?.onTriggerFinished(wakeupInfo)
    }

    override fun onSuccess(dialogRequestId: String) {
        collectThread?.onSuccess(dialogRequestId)
    }

    override fun onError(
        dialogRequestId: String,
        errorType: ASRAgentInterface.StartRecognitionCallback.ErrorType
    ) {
        collectThread?.onError(dialogRequestId, errorType)
    }
}

internal object KPM {
    /**
     * Search the data byte array for the first occurrence
     * of the byte array pattern.
     */
    fun indexOf(data: ByteArray?, pattern: ByteArray?): Int {
        if (data == null || pattern == null) return -1
        val failure = computeFailure(pattern)
        var j = 0
        for (i in data.indices) {
            while (j > 0 && pattern[j] != data[i]) {
                j = failure[j - 1]
            }
            if (pattern[j] == data[i]) {
                j++
            }
            if (j == pattern.size) {
                return i - pattern.size + 1
            }
        }
        return -1
    }

    fun indexOf(data: ByteArray?, start: Int, stop: Int, pattern: ByteArray?): Int {
        if (data == null || pattern == null) return -1
        val failure = computeFailure(pattern)
        var j = 0
        for (i in start until stop) {
            while (j > 0 && pattern[j] != data[i]) {
                j = failure[j - 1]
            }
            if (pattern[j] == data[i]) {
                j++
            }
            if (j == pattern.size) {
                return i - pattern.size + 1
            }
        }
        return -1
    }

    /**
     * Computes the failure function using a boot-strapping process,
     * where the pattern is matched against itself.
     */
    private fun computeFailure(pattern: ByteArray): IntArray {
        val failure = IntArray(pattern.size)
        var j = 0
        for (i in 1 until pattern.size) {
            while (j > 0 && pattern[j] != pattern[i]) {
                j = failure[j - 1]
            }
            if (pattern[j] == pattern[i]) {
                j++
            }
            failure[i] = j
        }
        return failure
    }
}
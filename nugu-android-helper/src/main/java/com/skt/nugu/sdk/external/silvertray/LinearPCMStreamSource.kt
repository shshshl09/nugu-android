/**
 * Copyright (c) 2025 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.external.silvertray

import android.util.Log
import com.skt.nugu.sdk.agent.mediaplayer.AttachmentSourcePlayable
import com.skt.nugu.sdk.core.interfaces.attachment.Attachment
import com.skt.nugu.silvertray.codec.MediaFormat
import com.skt.nugu.silvertray.source.DataSource
import java.nio.ByteBuffer

internal class LinearPCMStreamSource(
    private val attachmentReader: Attachment.Reader,
    private val format: AttachmentSourcePlayable.MediaFormat
) : DataSource {
    companion object {
        const val MIME_TYPE = "audio/l16"
        private const val TAG = "LinearPCMStreamSource"
        private const val FRAME_SIZE = 960
        private const val MAX_FRAME_SIZE = FRAME_SIZE * 3
    }

    private val _mediaFormat: MediaFormat by lazy {
        object: MediaFormat {
            override fun getMimeType(): String = format.mimeType

            override fun getSampleRate(): Int = format.sampleRateHz

            override fun getChannelCount(): Int = 1

            override fun getFrameSize(): Int = FRAME_SIZE

            override fun getMaxFrameSize(): Int = MAX_FRAME_SIZE
        }
    }
    private var currentBuffer: ByteBuffer? = null
    private var currentFrameSize: Int? = null

    override fun readSampleData(buffer: ByteBuffer, offset: Int): Int {
        // return current frame if exist
        currentBuffer?.let {
            currentFrameSize?.let { size ->
                buffer.put(it)
                return size
            }
        }

        val writeData = ByteArray(buffer.limit())

        val readCount = attachmentReader.read(writeData, 0, writeData.size)
        if(readCount <= 0) {
            Log.e(TAG, "[readSampleData] readCount: $readCount, but reached to end of stream")
            return 0
        }

        buffer.put(writeData)

        currentBuffer = buffer
        currentFrameSize = readCount

        return readCount
    }

    override fun advance() {
        currentBuffer = null
    }

    override fun release() {
        attachmentReader.close()
    }

    override fun getMediaFormat(): MediaFormat = _mediaFormat
}

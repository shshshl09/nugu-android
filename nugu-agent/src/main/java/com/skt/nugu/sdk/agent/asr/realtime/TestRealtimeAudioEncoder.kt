package com.skt.nugu.sdk.agent.asr.realtime

import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.asr.audio.Encoder

class TestRealtimeAudioEncoder: Encoder {
    override fun startEncoding(audioFormat: AudioFormat): Boolean {
        return true
    }

    override fun encode(input: ByteArray, offset: Int, size: Int): ByteArray? {
        return if(size > 0) {
            val outputBuffer = ByteArray(size)
            System.arraycopy(input, offset, outputBuffer, 0, size)
            outputBuffer
        } else {
            null
        }
    }

    override fun flush(): ByteArray? = null

    override fun stopEncoding() {
        // nothing
    }

    override fun getMimeType(): String = "audio/L16; rate=24000; channels=1; endianness=little"

    override fun getCodecName(): String = "PCM16"
}
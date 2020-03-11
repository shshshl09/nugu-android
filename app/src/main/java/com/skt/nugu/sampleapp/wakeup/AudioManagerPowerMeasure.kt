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

package com.skt.nugu.sampleapp.wakeup

import android.media.AudioManager
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.speechrecognizer.measure.PowerMeasure
import java.nio.ByteBuffer
import java.util.*

class AudioManagerPowerMeasure(
    private val audioManager: AudioManager
) : PowerMeasure {
    companion object {
        private const val TAG = "AudioManagerPowerMeasure"
        private const val MIC_GET_POWER = "micGetPower"
    }

    var prevPower = -99f

    override fun measure(buffer: ByteBuffer): Float {
        val strMicGetPower = audioManager.getParameters(MIC_GET_POWER)
        var tkn_results = StringTokenizer(strMicGetPower, "=")

        if (tkn_results.countTokens() != 2) {
            throw NoSuchElementException("[cnxt] fail to get mic gain")
        }
        if (!MIC_GET_POWER.equals(tkn_results.nextToken(), ignoreCase = true)) {
            throw NoSuchElementException("[cnxt] fail to get mic gain")
        }

        val str = tkn_results.nextToken()
        tkn_results = StringTokenizer(str)

        var i: Long
        i = tkn_results.nextToken().toLong(16)
        val left = i.toInt() / 8388608.0f // 8388608 == 2^23

        i = tkn_results.nextToken().toLong(16)
        val right = i.toInt() / 8388608.0f // 8388608 == 2^23

        val currentPower = 0.5f * (left + right)
        val power = 0.8f * currentPower + 0.2f * prevPower // smoothing
        prevPower = currentPower

        Logger.d(TAG, "param: $strMicGetPower, left: $left, right: $right, currentPower: $currentPower, power: $power")

        return power
    }
}
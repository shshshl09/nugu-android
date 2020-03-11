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

import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.speechrecognizer.measure.PowerMeasure
import com.sktelecom.tyche.native_cnxt
import java.nio.ByteBuffer
import java.util.*

class Nu300MicPowerMeasure: PowerMeasure {
    companion object {
        private const val TAG = "Nu300MicPowerMeasure"
        private val cnxt by lazy {
            native_cnxt()
        }
    }

    private var prevPower = -99f

    override fun measure(buffer: ByteBuffer): Float {
        val strMicGain = cnxt.getmicgain()

        val tkn_results = StringTokenizer(strMicGain, "/")
        if (tkn_results.countTokens() != 2) {
            throw NoSuchElementException("[cnxt] fail to get mic gain")
        }

        val left = tkn_results.nextToken().toFloat()
        val right = tkn_results.nextToken().toFloat()
        val currentPower: Float = if (left > -0.01f || right > -0.01f) {    // error: +-0.000
            // check whether range is wrong (0.0 is max value)
            // error
            0f
        } else {
            0.5f * (left + right)
        }

        val power = 0.8f*currentPower + 0.2f*prevPower // smoothing

        Logger.d(TAG, "strMicGain: $strMicGain, left: $left, right: $right, power: $power")

        prevPower = currentPower
        return power
    }
}
/**
 * Copyright (c) 2019 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sampleapp.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import com.skt.nugu.sdk.client.configuration.ConfigurationStore

/**
 * Demonstrate using nugu with webview.
 */
class IntroActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "IntroActivity"
        const val requestCode = 101
        const val EXTRA_KEY_DEVICE_UNIQUEID = "key_device_unique_id"

        fun invokeActivity(activity: Activity, deviceUniqueId: String) {
            activity.startActivityForResult(
                Intent(activity, IntroActivity::class.java)
                    .putExtra(EXTRA_KEY_DEVICE_UNIQUEID, deviceUniqueId)
            , requestCode)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = RelativeLayout(this)
        layout.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContentView(layout)

        val webView = WebView(this)
        webView.settings?.run {
            domStorageEnabled = true
            javaScriptEnabled = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                url?.apply {
                    // Otherwise, the link is not for a page on my site, so launch
                    // another Activity that handles URLs
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    } catch ( e : ActivityNotFoundException) {
                        return false
                    }
                    return true
                }
                return false
            }
        }
        intent.extras?.apply {
            ConfigurationStore.usageGuideUrl(getString(EXTRA_KEY_DEVICE_UNIQUEID).toString()) { url, error ->
                error?.apply {
                    Log.e(TAG, "[onCreate] error=$this")
                    return@usageGuideUrl
                }
                webView.loadUrl(url)
            }
        }

        layout.addView( webView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }
}
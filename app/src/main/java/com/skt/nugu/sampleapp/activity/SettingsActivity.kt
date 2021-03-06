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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.client.toResId
import com.skt.nugu.sampleapp.service.SampleAppService
import com.skt.nugu.sampleapp.utils.PreferenceHelper
import com.skt.nugu.sdk.client.configuration.ConfigurationStore
import com.skt.nugu.sdk.platform.android.login.auth.*
import com.skt.nugu.sdk.platform.android.ux.widget.NuguSnackbar
import com.skt.nugu.sdk.platform.android.ux.widget.NuguToast

class SettingsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "SettingsActivity"
        const val settingsAgreementActivityRequestCode = 102
        fun invokeActivity(context: Context) {
            context.startActivity(Intent(context, SettingsActivity::class.java))
        }
    }

    private val switchEnableNugu: Switch by lazy {
        findViewById<Switch>(R.id.switch_enable_nugu)
    }

    private val switchEnableTrigger: Switch by lazy {
        findViewById<Switch>(R.id.switch_enable_trigger)
    }

    private val spinnerWakeupWord: Spinner by lazy {
        findViewById<Spinner>(R.id.spinner_wakeup_word)
    }

    private val switchEnableWakeupBeep: Switch by lazy {
        findViewById<Switch>(R.id.switch_enable_wakeup_beep)
    }

    private val switchEnableRecognitionBeep: Switch by lazy {
        findViewById<Switch>(R.id.switch_enable_recognition_beep)
    }

    private val switchEnableFloating: Switch by lazy {
        findViewById<Switch>(R.id.switch_enable_floating)
    }

    private val buttonRevoke: Button by lazy {
        findViewById<Button>(R.id.btn_revoke)
    }

    private val spinnerAuthType: Spinner by lazy {
        findViewById<Spinner>(R.id.spinner_auth_type)
    }

    private val textLoginId: TextView by lazy {
        findViewById<TextView>(R.id.text_login_id)
    }

    private val buttonPrivacy: TextView by lazy {
        findViewById<TextView>(R.id.text_privacy)
    }

    private val buttonService: TextView by lazy {
        findViewById<TextView>(R.id.text_service)
    }

    private val buttonAgreement: TextView by lazy {
        findViewById<TextView>(R.id.text_agreement)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        switchEnableNugu.isChecked = PreferenceHelper.enableNugu(this)
        switchEnableTrigger.isChecked = PreferenceHelper.enableTrigger(this)
        switchEnableWakeupBeep.isChecked = PreferenceHelper.enableWakeupBeep(this)
        switchEnableRecognitionBeep.isChecked = PreferenceHelper.enableRecognitionBeep(this)
        switchEnableFloating.isChecked = PreferenceHelper.enableFloating(this)

        spinnerWakeupWord.adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            add("아리아")
            add("팅커벨")
        }

        if (PreferenceHelper.triggerId(this) == 0) {
            spinnerWakeupWord.setSelection(0)
        } else {
            spinnerWakeupWord.setSelection(1)
        }
        initBtnListeners()
        updateAccountInfo()
    }

    private fun updateAccountInfo() {
        NuguOAuth.getClient().introspect(object : NuguOAuthInterface.OnIntrospectResponseListener {
            override fun onSuccess(response: IntrospectResponse) {
                if (response.active) {
                    if (response.username.isEmpty()) {
                        Log.i(TAG, "Anonymous logined")
                    }
                    runOnUiThread {
                        textLoginId.text = response.username
                    }
                } else {
                    Log.e(TAG, "the token is inactive. response=$response")
                    runOnUiThread {
                        NuguToast.with(this@SettingsActivity)
                            .message(R.string.device_gw_error_003)
                            .duration(NuguToast.LENGTH_SHORT)
                            .show()
                    }
                }
            }

            override fun onError(error: NuguOAuthError) {
                handleOAuthError(error)
            }
        })
    }

    fun initBtnListeners() {
        switchEnableNugu.setOnCheckedChangeListener { _, isChecked ->
            PreferenceHelper.enableNugu(this, isChecked)
        }

        switchEnableTrigger.setOnCheckedChangeListener { _, isChecked ->
            PreferenceHelper.enableTrigger(this, isChecked)
        }

        spinnerWakeupWord.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (id == 0L) {
                    PreferenceHelper.triggerId(this@SettingsActivity, 0)
                } else {
                    PreferenceHelper.triggerId(this@SettingsActivity, 4)
                }
            }
        }

        switchEnableWakeupBeep.setOnCheckedChangeListener { _, isChecked ->
            PreferenceHelper.enableWakeupBeep(this, isChecked)
        }

        switchEnableRecognitionBeep.setOnCheckedChangeListener { _, isChecked ->
            PreferenceHelper.enableRecognitionBeep(this, isChecked)
        }

        switchEnableFloating.setOnCheckedChangeListener { _, isChecked ->
            PreferenceHelper.enableFloating(this, isChecked)
            if (!isChecked) {
                SampleAppService.hideFloating(applicationContext)
            }
        }

        buttonRevoke.setOnClickListener {
            NuguOAuth.getClient().revoke(object : NuguOAuthInterface.OnRevokeListener {
                override fun onSuccess() {
                    performRevoke()
                }

                override fun onError(error: NuguOAuthError) {
                    handleOAuthError(error)
                }
            })
        }

        textLoginId.setOnClickListener {
            NuguOAuth.getClient().accountWithTid(this, object : NuguOAuthInterface.OnAccountListener {
                override fun onSuccess(credentials: Credentials) {
                    // Update token
                    PreferenceHelper.credentials(this@SettingsActivity, credentials.toString())
                    ClientManager.getClient().disconnect()
                    ClientManager.getClient().connect()
                    updateAccountInfo()
                }

                override fun onError(error: NuguOAuthError) {
                    handleOAuthError(error)
                }
            })
        }

        buttonPrivacy.setOnClickListener {
            try {
                ConfigurationStore.privacyUrl { url, error ->
                    error?.apply {
                        Log.e(TAG, "[onCreate] error=$this")
                        return@privacyUrl
                    }
                    startActivity(Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(url)
                    })
                }

            } catch (e: SecurityException) {
                Log.d(TAG, "[buttonPrivacy] $e")
            } catch (e: ActivityNotFoundException) {
                Log.d(TAG, "[buttonPrivacy] $e")
            }
        }

        buttonService.setOnClickListener {
            SettingsServiceActivity.invokeActivity(this)
        }
        buttonAgreement.setOnClickListener {
            startActivityForResult(Intent(this, SettingsAgreementActivity::class.java), settingsAgreementActivityRequestCode)
        }
    }
    private fun performRevoke() {
        ClientManager.getClient().disconnect()
        NuguOAuth.getClient().clearAuthorization()
        PreferenceHelper.credentials(this@SettingsActivity, "")
        LoginActivity.invokeActivity(this@SettingsActivity)
        finishAffinity()
    }

    /**
     * Handles failed OAuth attempts.
     * The response errors return a description as defined in the spec: [https://developers-doc.nugu.co.kr/nugu-sdk/authentication]
     */
    private fun handleOAuthError(error: NuguOAuthError) {
        Log.e(TAG, "An unexpected error has occurred. " +
                "Please check the logs for details\n" +
                "$error")
        if(error.error != NuguOAuthError.NETWORK_ERROR &&
            error.error != NuguOAuthError.INITIALIZE_ERROR) {
            performRevoke()
        }
        runOnUiThread {
            NuguToast.with(this@SettingsActivity)
                .message(error.toResId())
                .duration(NuguToast.LENGTH_SHORT)
                .show()
        }
    }
}
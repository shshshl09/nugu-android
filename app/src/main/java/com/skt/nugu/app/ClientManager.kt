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
package com.skt.nugu.app

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.skt.nugu.app.player.SamplePlayerFactory
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.mediaplayer.ErrorType
import com.skt.nugu.sdk.agent.permission.PermissionDelegate
import com.skt.nugu.sdk.agent.permission.PermissionState
import com.skt.nugu.sdk.agent.permission.PermissionType
import com.skt.nugu.sdk.agent.routine.RoutineAgent
import com.skt.nugu.sdk.agent.routine.handler.ContinueDirectiveHandler
import com.skt.nugu.sdk.agent.routine.handler.StartDirectiveHandler
import com.skt.nugu.sdk.agent.sound.SoundProvider
import com.skt.nugu.sdk.client.SdkContainer
import com.skt.nugu.sdk.client.agent.factory.AgentFactory
import com.skt.nugu.sdk.client.configuration.ConfigurationStore
import com.skt.nugu.sdk.client.configuration.configure
import com.skt.nugu.sdk.client.port.transport.grpc2.GrpcTransportFactory
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.context.WakeupWordContextProvider
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.external.jademarble.EndPointDetector
import com.skt.nugu.sdk.external.keensense.KeensenseKeywordDetector
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.audiosource.AudioSourceManager
import com.skt.nugu.sdk.platform.android.audiosource.audiorecord.AudioRecordSourceFactory
import com.skt.nugu.sdk.platform.android.beep.AsrBeepResourceProvider
import com.skt.nugu.sdk.platform.android.speechrecognizer.SpeechProcessorDelegate
import com.skt.nugu.sdk.platform.android.speechrecognizer.SpeechRecognizerAggregator
import com.skt.nugu.sdk.platform.android.speechrecognizer.measure.SimplePcmPowerMeasure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.URI
import java.util.concurrent.Executors

/**
 * This class manage everything related to NUGU.
 * * manage resources of keyword detector.
 * * manage audio source for speech recognizer
 * * provide client for NUGU
 */
object ClientManager : AudioPlayerAgentInterface.Listener {
    private const val TAG = "ClientManager"

    private lateinit var client: NuguAndroidClient

    val ariaResource by lazy {
        KeensenseKeywordDetector.KeywordResources(
            "아리아",
            com.skt.keensense.default_resource.Resources.ARIA_NET_ASSET_FILE_NAME,
            com.skt.keensense.default_resource.Resources.ARIA_SEARCH_ASSET_FILE_NAME
        )
    }

    private val audioSourceManager = AudioSourceManager(AudioRecordSourceFactory())
    var keywordDetector: KeensenseKeywordDetector? = null
    lateinit var speechRecognizerAggregator: SpeechRecognizerAggregator

    var playerActivity: AudioPlayerAgentInterface.State = AudioPlayerAgentInterface.State.IDLE

    private val executor = Executors.newSingleThreadExecutor()

    private val directiveHandlingListener = object: DirectiveSequencerInterface.OnDirectiveHandlingListener {
        private val TAG = "DirectiveEvent"
        override fun onRequested(directive: Directive) {
            Log.d(TAG, "[onRequested] ${directive.header}")
        }

        override fun onCompleted(directive: Directive) {
            Log.d(TAG, "[onCompleted] ${directive.header}")
        }

        override fun onCanceled(directive: Directive) {
            Log.d(TAG, "[onCanceled] ${directive.header}")
        }

        override fun onFailed(directive: Directive, description: String) {
            Log.d(TAG, "[onFailed] ${directive.header} , $description")
        }
    }

    private val _isInitialized: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized

    fun init(context: Context) {
        if (_isInitialized.value) {
            return
        }

        executor.execute {
            // You can hide 'nugu_config.json' in local.properties :
            // [https://github.com/nugu-developers/nugu-android/wiki/Hiding-nugu%E2%80%90config.json-in-local.properties]
            ConfigurationStore.configure(context = context, filename = "nugu-config.json" /* nugu-config.json is the default and can be omitted */)

            // Create NuguAndroidClient
            client = NuguAndroidClient.Builder(
                context,
                object : AuthDelegate {
                    override fun getAuthorization(): String? {
                        TODO("Bearer access_token")
                    }
                },
                audioSourceManager
            ).defaultEpdTimeoutMillis(7000L)
                .addAgentFactory(RoutineAgent.NAMESPACE, object : AgentFactory<RoutineAgent> {
                    override fun create(container: SdkContainer): RoutineAgent = with(container) {
                        RoutineAgent(
                            getMessageSender(),
                            getContextManager(),
                            getDirectiveSequencer(),
                            getDirectiveSequencer(),
                            getDirectiveGroupProcessor(),
                            getAudioSeamlessFocusManager(),
                            object: StartDirectiveHandler.HandleController {
                                override fun shouldExecuteDirective(
                                    payload: StartDirectiveHandler.StartDirective.Payload,
                                    header: Header
                                ): StartDirectiveHandler.HandleController.Result {
                                    return StartDirectiveHandler.HandleController.Result.OK
//                                return StartDirectiveHandler.HandleController.Result.ERROR("type error message")
                                }
                            },
                            object: ContinueDirectiveHandler.HandleController {
                                override fun shouldExecuteDirective(
                                    payload: ContinueDirectiveHandler.ContinueDirective.Payload,
                                    header: Header
                                ): ContinueDirectiveHandler.HandleController.Result {
                                    return ContinueDirectiveHandler.HandleController.Result.OK
//                                return ContinueDirectiveHandler.HandleController.Result.ERROR("type error message")
                                }
                            },
                        )
                    }
                })
                .endPointDetector(EndPointDetector(com.skt.jademarble.default_resource.Resources.EPD_MODEL_ASSET_FILE_NAME, context.assets))
                .enableSound(object : SoundProvider {
                    override fun getContentUri(name: SoundProvider.BeepName): URI {
                        return URI.create(
                            Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.packageName + "/" + R.raw.responsefail_800ms)
                                .toString()
                        )
                    }
                })
                .asrBeepResourceProvider(object : AsrBeepResourceProvider {
                    private val wakeupResource = URI.create(
                        Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.packageName + "/" + R.raw.wakeup_500ms)
                            .toString()
                    )

                    private val responseFailResource = URI.create(
                        Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.packageName + "/" + R.raw.responsefail_800ms)
                            .toString()
                    )

                    private val responseSuccessResource = URI.create(
                        Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.packageName + "/" + R.raw.responsesuccess_800ms)
                            .toString()
                    )
                    override fun getOnStartListeningResource(): URI? {
                        return wakeupResource
                    }

                    override fun getOnErrorNetworkResource(): URI? {
                        return responseFailResource
                    }

                    override fun getOnErrorAudioInputResource(): URI? {
                        return responseFailResource
                    }

                    override fun getOnErrorListeningTimeoutResource(): URI? {
                        return responseFailResource
                    }

                    override fun getOnErrorUnknownResource(): URI? = null

                    override fun getOnErrorResponseTimeoutResource(): URI? = null

                    override fun getOnNoneResultResource(): URI? {
                        return responseFailResource
                    }

                    override fun getOnCompleteResultResource(): URI? {
                        return responseSuccessResource
                    }
                }).playerFactory(SamplePlayerFactory(context))
                .enablePermission(object: PermissionDelegate {
                    override val supportedPermissions: Array<PermissionType>
                        get() = arrayOf(PermissionType.LOCATION)

                    override fun getPermissionState(type: PermissionType): PermissionState {
                        return PermissionState.DENIED
                    }

                    override fun requestPermissions(types: Array<PermissionType>) {
                        Logger.d(TAG, "[requestPermissions] $types")
                    }
                })
                .build()

            client.addAudioPlayerListener(this)
            client.addOnDirectiveHandlingListener(directiveHandlingListener)

            client.audioPlayerAgent?.addOnPlaybackListener(object: AudioPlayerAgentInterface.OnPlaybackListener {
                override fun onPlaybackStarted(context: AudioPlayerAgentInterface.Context) {
                    Logger.d(TAG, "[onPlaybackStarted] dialogRequestId: ${context.dialogRequestId}")
                }

                override fun onPlaybackFinished(context: AudioPlayerAgentInterface.Context) {
                    Logger.d(TAG, "[onPlaybackFinished] dialogRequestId: $context")
                }

                override fun onPlaybackError(
                    context: AudioPlayerAgentInterface.Context,
                    type: ErrorType,
                    error: String
                ) {
                    Logger.d(TAG, "[onPlaybackError] dialogRequestId: ${context.dialogRequestId}, type: $type, error, $error")
                }

                override fun onPlaybackPaused(context: AudioPlayerAgentInterface.Context) {
                    Logger.d(TAG, "[onPlaybackPaused] dialogRequestId: ${context.dialogRequestId}")
                }

                override fun onPlaybackResumed(context: AudioPlayerAgentInterface.Context) {
                    Logger.d(TAG, "[onPlaybackResumed] dialogRequestId: ${context.dialogRequestId}")
                }

                override fun onPlaybackStopped(
                    context: AudioPlayerAgentInterface.Context,
                    stopReason: AudioPlayerAgentInterface.StopReason
                ) {
                    Logger.d(TAG, "[onPlaybackStopped] dialogRequestId: ${context.dialogRequestId}, stopReason: $stopReason")
                }
            })

            val asrAgent = client.asrAgent
            if(asrAgent == null) {
                Log.e(TAG, "asrAgent is null.")
                throw RuntimeException("asrAgent cannot be null")
            }

            keywordDetector = KeensenseKeywordDetector(ariaResource, SimplePcmPowerMeasure(), context.assets)
            speechRecognizerAggregator = SpeechRecognizerAggregator(
                keywordDetector,
                SpeechProcessorDelegate(asrAgent),
                audioSourceManager,
                Handler(Looper.getMainLooper())
            ).apply {
                //keywordRecorder = FileKeywordRecorder(ObbDirFileProvider(context))
            }

            val wakeupWordStateProvider = object : WakeupWordContextProvider() {
                override fun getWakeupWord(): String = "아리아"
            }

            client.setStateProvider(wakeupWordStateProvider.namespaceAndName, wakeupWordStateProvider)
            _isInitialized.value = true
        }
    }

    fun getClient() = client

    override fun onStateChanged(
        activity: AudioPlayerAgentInterface.State,
        context: AudioPlayerAgentInterface.Context
    ) {
        playerActivity = activity
    }
}
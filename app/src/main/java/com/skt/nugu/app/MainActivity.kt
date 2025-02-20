package com.skt.nugu.app

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout

import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skt.nugu.app.ui.theme.NuguAndroidTheme
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.client.configuration.ConfigurationStore
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateRenderer
import com.skt.nugu.sdk.platform.android.ux.widget.ChromeWindow
import com.skt.nugu.sdk.platform.android.ux.widget.NuguButton

class MainActivity : AppCompatActivity() {
    companion object {
        val TAG = MainActivity::class.java.simpleName
    }

    //private lateinit var chromeWindow: ChromeWindow
    private val speechRecognizer by lazy {
        ClientManager.speechRecognizerAggregator
    }

    private val templateRenderer by lazy {
        TemplateRenderer(object :
            TemplateRenderer.NuguClientProvider {
            override fun getNuguClient(): NuguAndroidClient =
                ClientManager.getClient()
        }, ConfigurationStore.configuration.deviceTypeCode, null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isSdkInitialized by ClientManager.isInitialized.collectAsStateWithLifecycle()
            if (!isSdkInitialized) {
                // TODO: Loading screen
            } else {
                val chromeWindowState = remember { mutableStateOf<ChromeWindow?>(null) }
                BackHandler {
                    if (chromeWindowState.value?.isShown() == true) {
                        speechRecognizer.stopListening()
                        return@BackHandler
                    }
                    if (templateRenderer.clearAll()) {
                        return@BackHandler
                    }
                    finish()
                }
                LaunchedEffect(Unit) {
                    ClientManager.getClient()
                        .setDisplayRenderer(templateRenderer.also {
                            it.setFragmentManager(supportFragmentManager)
                            ConfigurationStore.templateServerUri { url, _ ->
                                it.setServerUrl(url)
                            }
                        })
                }
                NuguAndroidTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        MainScreen(innerPadding, templateRenderer, chromeWindowState) {
                            Text(
                                text = "Sample App",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                textAlign = TextAlign.Center,
                                color = Color.LightGray,
                                fontSize = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        speechRecognizer.stop()
    }
}

@Composable
fun MainScreen(
    innerPadding: PaddingValues, templateRenderer: TemplateRenderer,
    chromeWindowState: MutableState<ChromeWindow?>,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        content()

        TemplateAndChromeWindow(
            containerId = templateRenderer.containerId,
            chromeWindowFactory = { context, view ->
                ChromeWindow(context, view, object : ChromeWindow.NuguClientProvider {
                    override fun getNuguClient() = ClientManager.getClient()
                }).also {
                    chromeWindowState.value = it
                }
            })
        SpeakButton(modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
fun TemplateAndChromeWindow(
    containerId: Int,
    chromeWindowFactory: (Context, ViewGroup) -> ChromeWindow
) {
    AndroidView(
        factory = { context ->
            FrameLayout(context).apply {
                addView(FrameLayout(context).apply {
                    // template window : templateRenderer.containerId 사용
                    id = containerId
                })
                addView(FrameLayout(context).apply {
                    chromeWindowFactory(context, this)
                })
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun SpeakButton(
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            NuguButton(context).apply {
                setOnClickListener {
                    ClientManager.speechRecognizerAggregator.startListening(
                        initiator = ASRAgentInterface.Initiator.TAP
                    )
                }
            }
        },
        modifier = modifier
    )
}
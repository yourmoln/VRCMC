package com.vrcmc.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class VoiceInputServiceSectionTest {
    @get:Rule val compose = createComposeRule()
    private val strings = LocaleStringsZhHans

    @Test
    fun choosingLocalHidesCloudFieldsAndPreservesCloudSettings() {
        val recognizer = DownloadTestRecognizer()
        val original = VoiceInputConfig(enabled = true, apiKey = "test-key", model = "saved-cloud-model")
        var config by mutableStateOf(original)
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(480.dp).verticalScroll(rememberScrollState())) {
                    VoiceInputServiceSection(config, strings, { config = it(config) }, localRecognizer = recognizer)
                }
            }
        }
        compose.onNodeWithText(strings.voiceInputProvider).performClick()
        compose.onNodeWithText(strings.localWhisper).performClick()
        compose.onNodeWithText(strings.qwenApiKey).assertDoesNotExist()
        compose.onNodeWithText("Base URL").assertDoesNotExist()
        compose.onNodeWithText("Whisper Small · 190 MB").assertIsDisplayed()
        compose.runOnIdle { assertEquals(original.copy(provider = VoiceInputProvider.LOCAL_WHISPER), config) }
        compose.onNodeWithText(strings.voiceInputProvider).performClick()
        compose.onNodeWithText("Qwen3-ASR").performClick()
        compose.onNodeWithText(strings.qwenApiKey).assertIsDisplayed()
        compose.runOnIdle { assertEquals(original, config) }
    }

    @Test
    fun manualDownloadProgressCancelFailureRetryAndCachedStatesAreVisible() {
        var status by mutableStateOf<LocalSpeechModelStatus>(LocalSpeechModelStatus.Missing)
        var downloads = 0
        var cancellations = 0
        compose.setContent {
            MaterialTheme {
                Surface {
                    Column(Modifier.width(480.dp).padding(16.dp)) {
                        LocalSpeechModelSettings(status, true, strings, { downloads++ }, { cancellations++ })
                    }
                }
            }
        }
        compose.onNodeWithText(strings.localModelPreparing).assertDoesNotExist()
        compose.onNodeWithText(strings.localModelDownload).performClick()
        compose.runOnIdle {
            assertEquals(1, downloads)
            status = LocalSpeechModelStatus.Downloading(95_000_000, 190_000_000)
        }
        compose.onNodeWithText("正在下载模型 50% · 95 / 190 MB").assertIsDisplayed()
        compose.onNodeWithText(strings.localModelCancelDownload).performClick()
        compose.runOnIdle { assertEquals(1, cancellations) }
        val output = File("build/test-screenshots/local-speech-model.png")
        output.parentFile.mkdirs()
        ImageIO.write(compose.onRoot().captureToImage().toAwtImage(), "png", output)
        compose.runOnIdle { status = LocalSpeechModelStatus.Failed("Connection interrupted") }
        compose.onNodeWithText(strings.localModelFailed).assertIsDisplayed()
        compose.onNodeWithText(strings.localModelRetry).performClick()
        compose.runOnIdle {
            assertEquals(2, downloads)
            status = LocalSpeechModelStatus.Downloaded
        }
        compose.onNodeWithText(strings.localModelDownloaded).assertIsDisplayed()
        compose.onNodeWithText(strings.localModelDownload).assertDoesNotExist()
        compose.runOnIdle {
            status = LocalSpeechModelStatus.Ready
        }
        compose.onNodeWithText(strings.localModelReady).assertIsDisplayed()
        compose.onNodeWithText(strings.localModelRetry).assertDoesNotExist()
    }

    @Test
    fun downloadAndCancelRemainAvailableAcrossServiceSwitchDisableAndPageNavigation() {
        val recognizer = DownloadTestRecognizer()
        var config by mutableStateOf(VoiceInputConfig(enabled = true, provider = VoiceInputProvider.LOCAL_WHISPER))
        var pageVisible by mutableStateOf(true)
        compose.setContent {
            val scope = rememberCoroutineScope()
            val controller = remember(scope) { LocalSpeechModelController(scope, recognizer) }
            LaunchedEffect(config) { controller.updateConfig(config) }
            MaterialTheme {
                if (pageVisible) {
                    Column(Modifier.width(480.dp).verticalScroll(rememberScrollState())) {
                        VoiceInputServiceSection(config, strings, { config = it(config) },
                            controller::downloadModel, controller::cancelDownload, recognizer)
                    }
                }
            }
        }
        compose.runOnIdle { assertEquals(0, recognizer.downloads) }
        compose.onNodeWithText(strings.localModelDownload).performClick()
        compose.onNodeWithText(strings.localModelCancelDownload).assertIsDisplayed()
        compose.onNodeWithText(strings.voiceInputProvider).performClick()
        compose.onNodeWithText("Qwen3-ASR").performClick()
        compose.onNodeWithText(strings.localModelCancelDownload).assertIsDisplayed()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText(strings.localModelCancelDownload).assertIsDisplayed()
        compose.runOnIdle { pageVisible = false }
        compose.onNodeWithText(strings.localModelCancelDownload).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, recognizer.downloads)
            assertEquals(0, recognizer.cancellations)
            pageVisible = true
        }
        compose.onNodeWithText(strings.localModelCancelDownload).performClick()
        compose.waitUntil { recognizer.cancellations == 1 }
        compose.onNodeWithText(strings.localModelCancelDownload).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, recognizer.downloads) }
    }

    private class DownloadTestRecognizer : LocalSpeechRecognizer by UnsupportedLocalSpeechRecognizer {
        override val supported = true
        override val status = MutableStateFlow<LocalSpeechModelStatus>(LocalSpeechModelStatus.Missing)
        var downloads = 0
        var cancellations = 0
        override suspend fun downloadModel(): Boolean {
            downloads++
            status.value = LocalSpeechModelStatus.Downloading(95_000_000, 190_000_000)
            try {
                awaitCancellation()
            } finally {
                cancellations++
                status.value = LocalSpeechModelStatus.Missing
            }
        }
    }
}

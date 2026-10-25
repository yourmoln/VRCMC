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
        compose.onNodeWithText(strings.localModel).performClick()
        compose.onNodeWithText(strings.qwenApiKey).assertDoesNotExist()
        compose.onNodeWithText("Base URL").assertDoesNotExist()
        compose.onNodeWithText(LocalSpeechModel.SENSE_VOICE_SMALL_INT8.displayLabel).assertIsDisplayed()
        compose.runOnIdle { assertEquals(original.copy(provider = VoiceInputProvider.LOCAL_MODEL), config) }
        compose.onNodeWithText(strings.voiceInputProvider).performClick()
        compose.onNodeWithText("Fun-ASR").performClick()
        compose.onNodeWithText(strings.qwenApiKey).assertIsDisplayed()
        compose.runOnIdle { assertEquals(original, config) }
    }

    @Test
    fun senseVoiceModelIsShownAndDownloadsInt8Model() {
        val recognizer = DownloadTestRecognizer()
        var config by mutableStateOf(
            VoiceInputConfig(enabled = true, provider = VoiceInputProvider.LOCAL_MODEL),
        )
        var requestedModel: LocalSpeechModel? = null
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(480.dp).verticalScroll(rememberScrollState())) {
                    VoiceInputServiceSection(
                        config = config,
                        strings = strings,
                        onUpdate = { config = it(config) },
                        onDownloadLocalModel = { requestedModel = it },
                        localRecognizer = recognizer,
                    )
                }
            }
        }

        compose.onNodeWithText("SenseVoice Small INT8 · 240 MB").assertIsDisplayed()
        compose.onNodeWithText(strings.localModelDownload).performClick()
        compose.runOnIdle {
            assertEquals(LocalSpeechModel.SENSE_VOICE_SMALL_INT8, config.localSpeechModel)
            assertEquals(LocalSpeechModel.SENSE_VOICE_SMALL_INT8, requestedModel)
        }
    }

    @Test
    fun selectingFunAsrNanoUpdatesConfigAndDownloadsModel() {
        val recognizer = DownloadTestRecognizer()
        var config by mutableStateOf(
            VoiceInputConfig(enabled = true, provider = VoiceInputProvider.LOCAL_MODEL),
        )
        var requestedModel: LocalSpeechModel? = null
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(480.dp).verticalScroll(rememberScrollState())) {
                    VoiceInputServiceSection(
                        config = config,
                        strings = strings,
                        onUpdate = { config = it(config) },
                        onDownloadLocalModel = { requestedModel = it },
                        localRecognizer = recognizer,
                    )
                }
            }
        }

        compose.onNodeWithText(LocalSpeechModel.SENSE_VOICE_SMALL_INT8.displayLabel).performClick()
        compose.onNodeWithText(LocalSpeechModel.FUN_ASR_NANO_2512_INT8.displayLabel).performClick()
        compose.onNodeWithText("Fun-ASR-Nano 2512 INT8 · 265 MB").assertIsDisplayed()
        compose.onNodeWithText(strings.funAsrNanoHint).assertIsDisplayed()
        compose.onNodeWithText(strings.localModelDownload).performClick()
        compose.runOnIdle {
            assertEquals(LocalSpeechModel.FUN_ASR_NANO_2512_INT8, config.localSpeechModel)
            assertEquals(LocalSpeechModel.FUN_ASR_NANO_2512_INT8, requestedModel)
        }
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
                        LocalSpeechModelSettings(
                            model = LocalSpeechModel.SENSE_VOICE_SMALL_INT8,
                            status = status,
                            supported = true,
                            strings = strings,
                            onModelChange = {},
                            onDownload = { downloads++ },
                            onCancel = { cancellations++ },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText(strings.localModelPreparing).assertDoesNotExist()
        compose.onNodeWithText(strings.localModelDownload).performClick()
        compose.runOnIdle {
            assertEquals(1, downloads)
            status = LocalSpeechModelStatus.Downloading(120_000_000, 240_000_000)
        }
        compose.onNodeWithText("正在下载模型 50% · 120 / 240 MB").assertIsDisplayed()
        compose.onNodeWithText(LocalSpeechModel.SENSE_VOICE_SMALL_INT8.displayLabel).assertIsNotEnabled()
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
        var config by mutableStateOf(VoiceInputConfig(enabled = true, provider = VoiceInputProvider.LOCAL_MODEL))
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
        compose.onNodeWithText("Fun-ASR").performClick()
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
        private val statuses = LocalSpeechModel.entries.associateWith {
            MutableStateFlow<LocalSpeechModelStatus>(LocalSpeechModelStatus.Missing)
        }
        var downloads = 0
        var cancellations = 0
        override fun status(model: LocalSpeechModel) = statuses.getValue(model)
        override suspend fun downloadModel(model: LocalSpeechModel): Boolean {
            downloads++
            val status = statuses.getValue(model)
            status.value = LocalSpeechModelStatus.Downloading(95_000_000, model.downloadSizeMb * 1_000_000L)
            try {
                awaitCancellation()
            } finally {
                cancellations++
                status.value = LocalSpeechModelStatus.Missing
            }
        }
    }
}

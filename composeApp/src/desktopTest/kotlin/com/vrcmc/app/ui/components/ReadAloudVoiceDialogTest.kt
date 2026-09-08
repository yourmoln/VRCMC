package com.vrcmc.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

class ReadAloudVoiceDialogTest {
    @get:Rule val compose = createComposeRule()
    private val strings = LocaleStringsZhHans
    private val original = "zh-CN-XiaoxiaoNeural"
    private val voices = listOf(
        EdgeTtsVoice("en-US-AriaNeural", "en-US", "Female"),
        EdgeTtsVoice("en-US-GuyNeural", "en-US", "Male"),
        EdgeTtsVoice("ja-JP-KeitaNeural", "ja-JP", "Male"),
        EdgeTtsVoice("ja-JP-NanamiNeural", "ja-JP", "Female"),
        EdgeTtsVoice(original, "zh-CN", "Female"),
        EdgeTtsVoice("zh-CN-YunxiNeural", "zh-CN", "Male"),
    )
    private var visible by mutableStateOf(true)
    private val confirmed = mutableListOf<String>()
    private val previews = mutableListOf<String>()
    private var stops = 0
    private var refreshes = 0

    private fun showDialog(loadFailed: Boolean = false) {
        compose.setContent {
            MaterialTheme {
                if (visible) ReadAloudVoiceDialog(
                    voices = if (loadFailed) emptyList() else voices,
                    currentVoice = original,
                    strings = strings,
                    loading = false,
                    loadFailed = loadFailed,
                    previewBusy = false,
                    previewFailed = false,
                    onRefresh = { refreshes++ },
                    onPreview = { previews.add(it.shortName) },
                    onStopPreview = { stops++ },
                    onConfirm = { confirmed.add(it); visible = false },
                    onDismiss = { visible = false },
                )
            }
        }
    }

    private fun voiceRow(name: String) = compose.onNode(hasText(name) and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))

    @Test
    fun searchSelectPreviewThenConfirm() {
        showDialog()
        voiceRow("Xiaoxiao").assertIsSelected()
        val screenshot = compose.onNodeWithTag("readAloudVoiceDialog").captureToImage()
        val output = File("build/test-screenshots/read-aloud-voice-dialog.png")
        output.parentFile.mkdirs()
        ImageIO.write(screenshot.toAwtImage(), "png", output)

        compose.onNode(hasSetTextAction()).assertIsFocused().performTextInput("日语 女声")
        voiceRow("Nanami").assertIsDisplayed().performClick().assertIsSelected()
        voiceRow("Keita").assertDoesNotExist()
        compose.onNodeWithText(strings.readAloudPreview).performClick()
        compose.runOnIdle {
            assertEquals(listOf("ja-JP-NanamiNeural"), previews)
            assertTrue(confirmed.isEmpty())
        }
        compose.onNodeWithText(strings.done).performClick()
        compose.runOnIdle {
            assertEquals(listOf("ja-JP-NanamiNeural"), confirmed)
            assertEquals(1, stops)
        }
    }

    @Test
    fun noResultsCanBeClearedAndCancelDiscardsDraftAndSearch() {
        showDialog()
        compose.onNode(hasSetTextAction()).performTextInput("not-a-voice")
        compose.onNodeWithText(strings.readAloudNoVoices).assertIsDisplayed()
        compose.onNodeWithContentDescription(strings.readAloudClearSearch).performClick()
        voiceRow("Xiaoxiao").assertIsSelected()
        voiceRow("Aria").performClick()
        compose.onNodeWithText(strings.cancel).performClick()
        compose.runOnIdle { assertTrue(confirmed.isEmpty()); visible = true }
        voiceRow("Xiaoxiao").assertIsSelected()
        compose.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test
    fun loadingFailureOffersRetryAndRetainsSavedVoice() {
        showDialog(loadFailed = true)
        compose.onNodeWithText(strings.readAloudVoicesFailed).assertIsDisplayed()
        voiceRow("Xiaoxiao").assertIsSelected()
        compose.onNodeWithContentDescription(strings.readAloudRefresh).performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
        compose.onNodeWithText(strings.readAloudPreview).performClick()
        compose.runOnIdle { assertEquals(listOf(original), previews) }
        compose.onNodeWithText(strings.cancel).performClick()
        compose.runOnIdle { assertTrue(confirmed.isEmpty()); assertEquals(1, stops) }
    }
}

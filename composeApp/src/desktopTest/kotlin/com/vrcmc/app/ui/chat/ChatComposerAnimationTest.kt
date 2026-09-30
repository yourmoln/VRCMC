package com.vrcmc.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class ChatComposerAnimationTest {
    @Test
    fun idleComposerDoesNotRequestFramesAndStoppingInterpretationCancelsAnimation() = runSkikoComposeUiTest {
        var interpreting by mutableStateOf(false)
        showComposer(TestWindowInfo(), interpreting = { interpreting })

        settleFrames()
        runOnUiThread { assertFalse(scene.hasInvalidations()) }

        runOnUiThread { interpreting = true }
        settleFrames()
        runOnUiThread { assertTrue(scene.hasInvalidations()) }

        runOnUiThread { interpreting = false }
        settleFrames()
        runOnUiThread { assertFalse(scene.hasInvalidations()) }
    }

    @Test
    fun interpretationAnimationOnlyRunsWhileWindowIsFocused() = runSkikoComposeUiTest {
        val windowInfo = TestWindowInfo(focused = false)
        showComposer(windowInfo, interpreting = { true })

        settleFrames()
        runOnUiThread { assertFalse(scene.hasInvalidations()) }

        repeat(2) {
            runOnUiThread { windowInfo.isWindowFocused = true }
            settleFrames()
            runOnUiThread { assertTrue(scene.hasInvalidations()) }

            runOnUiThread { windowInfo.isWindowFocused = false }
            settleFrames()
            runOnUiThread { assertFalse(scene.hasInvalidations()) }
        }
    }

    @Test
    fun unfocusedWindowStopsCursorFramesWithoutClearingInputFocus() = runSkikoComposeUiTest {
        val windowInfo = TestWindowInfo()
        showComposer(windowInfo, interpreting = { false })
        onNode(hasSetTextAction()).performClick().assertIsFocused()
        settleFrames()

        runOnUiThread { windowInfo.isWindowFocused = false }
        settleFrames()
        runOnUiThread { assertFalse(scene.hasInvalidations()) }
        onNode(hasSetTextAction()).assertIsFocused()

        runOnUiThread { windowInfo.isWindowFocused = true }
        settleFrames()
        onNode(hasSetTextAction()).assertIsFocused()
    }

    private fun SkikoComposeUiTest.showComposer(windowInfo: WindowInfo, interpreting: () -> Boolean) {
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalWindowInfo provides windowInfo) {
                MaterialTheme {
                    ChatComposer(
                        input = "",
                        sending = false,
                        enabled = true,
                        interpreting = interpreting(),
                        alwaysInterpretationEnabled = false,
                        alwaysInterpretationActive = false,
                        voiceInputEnabled = false,
                        voiceRecording = false,
                        voiceSpeaking = false,
                        voiceTranscribing = false,
                        maxInputCharacters = 144,
                        strings = LocaleStringsEn,
                        onInputChange = {},
                        onSend = {},
                        onToggleAlwaysInterpretation = {},
                        onToggleVoiceInput = {},
                    )
                }
            }
        }
    }

    private fun SkikoComposeUiTest.settleFrames() {
        mainClock.advanceTimeBy(1600)
        waitForIdle()
    }

    private class TestWindowInfo(focused: Boolean = true) : WindowInfo {
        override var isWindowFocused by mutableStateOf(focused)
    }
}

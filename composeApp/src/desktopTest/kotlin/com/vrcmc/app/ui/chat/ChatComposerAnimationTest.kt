package com.vrcmc.app

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class ChatComposerAnimationTest {
    @Test
    fun interpretationBorderUpdatesAtFiveFramesPerSecond() = runSkikoComposeUiTest {
        var drawCount = 0
        showComposer(TestWindowInfo(), interpreting = { true }, onDraw = { drawCount++ })
        settleFrames()

        val animationDrawCount = countAnimationDraws { drawCount }
        assertTrue(animationDrawCount in 4..5, "Border redrew $animationDrawCount times in 960 ms")
    }

    @Test
    fun inputUpdatesDoNotWaitForTheNextBorderFrame() = runSkikoComposeUiTest {
        var input by mutableStateOf("")
        showComposer(TestWindowInfo(), interpreting = { true }, input = { input })
        settleFrames()

        runOnUiThread { input = "recognized speech" }
        mainClock.advanceTimeByFrame()
        waitForIdle()

        onNode(hasSetTextAction()).assertTextEquals("recognized speech")
    }

    @Test
    fun idleComposerDoesNotRequestFramesAndStoppingInterpretationCancelsAnimation() = runSkikoComposeUiTest {
        var interpreting by mutableStateOf(false)
        var drawCount = 0
        showComposer(TestWindowInfo(), interpreting = { interpreting }, onDraw = { drawCount++ })

        settleFrames()
        runOnUiThread { assertFalse(scene.hasInvalidations()) }

        runOnUiThread { interpreting = true }
        settleFrames()
        assertTrue(countAnimationDraws { drawCount } > 0)

        runOnUiThread { interpreting = false }
        settleFrames()
        runOnUiThread { assertFalse(scene.hasInvalidations()) }
        assertEquals(0, countAnimationDraws { drawCount })
    }

    @Test
    fun interpretationAnimationKeepsRunningWhenWindowIsUnfocused() = runSkikoComposeUiTest {
        val windowInfo = TestWindowInfo(focused = false)
        var interpreting by mutableStateOf(true)
        var drawCount = 0
        showComposer(windowInfo, interpreting = { interpreting }, onDraw = { drawCount++ })

        settleFrames()
        assertTrue(countAnimationDraws { drawCount } in 4..5)

        repeat(2) {
            runOnUiThread { windowInfo.isWindowFocused = true }
            settleFrames()
            assertTrue(countAnimationDraws { drawCount } in 4..5)

            runOnUiThread { windowInfo.isWindowFocused = false }
            settleFrames()
            assertTrue(countAnimationDraws { drawCount } in 4..5)
        }

        runOnUiThread { interpreting = false }
        settleFrames()
        runOnUiThread { assertFalse(scene.hasInvalidations()) }
        assertEquals(0, countAnimationDraws { drawCount })
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

    private fun SkikoComposeUiTest.showComposer(
        windowInfo: WindowInfo,
        interpreting: () -> Boolean,
        onDraw: () -> Unit = {},
        input: () -> String = { "" },
    ) {
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalWindowInfo provides windowInfo) {
                MaterialTheme {
                    Box(
                        Modifier.drawWithContent {
                            onDraw()
                            drawContent()
                        }
                    ) {
                        ChatComposer(
                            input = input(),
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
    }

    private fun SkikoComposeUiTest.settleFrames() {
        mainClock.advanceTimeBy(1600)
        waitForIdle()
    }

    private fun SkikoComposeUiTest.countAnimationDraws(drawCount: () -> Int): Int {
        val initialDrawCount = runOnUiThread(drawCount)
        repeat(60) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
        }
        return runOnUiThread { drawCount() - initialDrawCount }
    }

    private class TestWindowInfo(focused: Boolean = true) : WindowInfo {
        override var isWindowFocused by mutableStateOf(focused)
    }
}

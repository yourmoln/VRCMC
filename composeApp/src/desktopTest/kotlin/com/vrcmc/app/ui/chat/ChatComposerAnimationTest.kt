package com.vrcmc.app

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
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
    fun interpretationBorderUpdatesAtSixtyFramesPerSecond() = runSkikoComposeUiTest {
        val samples = mutableListOf<Long>()
        showComposer(TestWindowInfo(), interpreting = { true }, animationSamples = samples)
        settleFrames()

        val animationFrameCount = countAnimationSamples(samples)
        assertTrue(animationFrameCount in 55..60, "Border updated $animationFrameCount times in 960 ms")
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
        showComposer(TestWindowInfo(), interpreting = { interpreting })

        settleFrames()
        runOnUiThread { assertFalse(scene.hasInvalidations()) }

        runOnUiThread { interpreting = true }
        mainClock.advanceTimeByFrame()
        waitForIdle()
        runOnUiThread { assertTrue(scene.hasInvalidations()) }

        runOnUiThread { interpreting = false }
        settleFrames()
        runOnUiThread { assertFalse(scene.hasInvalidations()) }
    }

    @Test
    fun interpretationAnimationKeepsRunningWhenWindowIsUnfocused() = runSkikoComposeUiTest {
        val windowInfo = TestWindowInfo(focused = false)
        var interpreting by mutableStateOf(true)
        val samples = mutableListOf<Long>()
        showComposer(windowInfo, interpreting = { interpreting }, animationSamples = samples)

        settleFrames()
        assertTrue(countAnimationSamples(samples) in 55..60)

        repeat(2) {
            runOnUiThread { windowInfo.isWindowFocused = true }
            settleFrames()
            assertTrue(countAnimationSamples(samples) in 55..60)

            runOnUiThread { windowInfo.isWindowFocused = false }
            settleFrames()
            assertTrue(countAnimationSamples(samples) in 55..60)
        }

        runOnUiThread { interpreting = false }
        settleFrames()
        runOnUiThread { assertFalse(scene.hasInvalidations()) }
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
        input: () -> String = { "" },
        animationSamples: MutableList<Long>? = null,
    ) {
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalWindowInfo provides windowInfo) {
                MaterialTheme {
                    val sampledAnimationTime = animationSamples?.let { rememberChatAnimationTime(active = true) }
                    val sampledValue = sampledAnimationTime?.value
                    if (sampledValue != null) SideEffect { animationSamples += sampledValue }
                    Box {
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
                            animationTimeNanos = sampledAnimationTime,
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

    private fun SkikoComposeUiTest.countAnimationSamples(samples: List<Long>): Int {
        val initialSampleCount = samples.distinct().size
        repeat(60) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
        }
        return samples.distinct().size - initialSampleCount
    }

    private class TestWindowInfo(focused: Boolean = true) : WindowInfo {
        override var isWindowFocused by mutableStateOf(focused)
    }
}

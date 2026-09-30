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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runSkikoComposeUiTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class ChatLoadingAnimationTest {
    @Test
    fun sendingProgressUpdatesAtSixtyFramesPerSecond() = runSkikoComposeUiTest {
        val samples = mutableListOf<Long>()
        showComposer(sending = true, samples = samples)
        settleFrames()
        assertSixtyUpdatesPerSecond(samples)
    }

    @Test
    fun voiceTranscriptionProgressUpdatesAtSixtyFramesPerSecond() = runSkikoComposeUiTest {
        val samples = mutableListOf<Long>()
        showComposer(voiceTranscribing = true, samples = samples)
        settleFrames()
        assertSixtyUpdatesPerSecond(samples)
    }

    @Test
    fun loadingMessagesStopAnimatingWhenCompleted() = runSkikoComposeUiTest {
        val state = AppState().apply {
            devices.clear()
            messages.clear()
            messages.add(ChatMessage("", MessageRole.ASSISTANT, timestamp = 1, isLoading = true))
        }
        var visible by mutableStateOf(true)
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalWindowInfo provides object : WindowInfo {
                override val isWindowFocused = false
            }) {
                MaterialTheme {
                    Box {
                        if (visible) ChatPage(state, LocaleStringsEn)
                    }
                }
            }
        }
        settleFrames()

        runOnUiThread {
            state.messages.add(ChatMessage("", MessageRole.ASSISTANT, timestamp = 2, isLoading = true))
        }
        mainClock.advanceTimeByFrame()
        waitForIdle()
        runOnUiThread { assertTrue(scene.hasInvalidations()) }

        runOnUiThread {
            state.messages.indices.forEach { index ->
                state.messages[index] = state.messages[index].copy(text = "translation $index", isLoading = false)
            }
        }
        mainClock.advanceTimeByFrame()
        waitForIdle()
        onNodeWithText("translation 0").assertExists()
        onNodeWithText("translation 1").assertExists()
        settleFrames()
        runOnUiThread { assertFalse(scene.hasInvalidations()) }

        runOnUiThread { visible = false }
        settleFrames()
        runOnUiThread { assertFalse(scene.hasInvalidations()) }
        onNode(hasSetTextAction()).assertDoesNotExist()
    }

    private fun SkikoComposeUiTest.showComposer(
        sending: Boolean = false,
        voiceTranscribing: Boolean = false,
        samples: MutableList<Long>,
    ) {
        mainClock.autoAdvance = false
        setContent {
            MaterialTheme {
                val animationTime = rememberChatAnimationTime(active = true)
                val animationValue = animationTime.value
                SideEffect { samples += animationValue }
                Box {
                    ChatComposer(
                        input = "",
                        sending = sending,
                        enabled = true,
                        interpreting = false,
                        alwaysInterpretationEnabled = false,
                        alwaysInterpretationActive = false,
                        voiceInputEnabled = voiceTranscribing,
                        voiceRecording = false,
                        voiceSpeaking = false,
                        voiceTranscribing = voiceTranscribing,
                        maxInputCharacters = 144,
                        strings = LocaleStringsEn,
                        onInputChange = {},
                        onSend = {},
                        onToggleAlwaysInterpretation = {},
                        onToggleVoiceInput = {},
                        animationTimeNanos = animationTime,
                    )
                }
            }
        }
    }

    private fun SkikoComposeUiTest.assertSixtyUpdatesPerSecond(samples: List<Long>) {
        val initialSampleCount = samples.distinct().size
        repeat(60) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
        }
        val updateCount = samples.distinct().size - initialSampleCount
        assertTrue(updateCount in 55..60, "Loading indicator updated $updateCount times in 960 ms")
    }

    private fun SkikoComposeUiTest.settleFrames() {
        mainClock.advanceTimeBy(1600)
        waitForIdle()
    }
}

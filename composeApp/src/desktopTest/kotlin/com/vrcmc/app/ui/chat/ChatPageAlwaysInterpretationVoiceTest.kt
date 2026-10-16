package com.vrcmc.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.runSkikoComposeUiTest
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Regression tests for always interpretation with synced voice input: the composer draft
 * is the user's own text and must keep auto-sending even while the managed voice capture
 * is recording.
 */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class ChatPageAlwaysInterpretationVoiceTest {
    @Test
    fun typedDraftAutoSendsWhileSyncedVoiceInputIsRecording() = runSkikoComposeUiTest {
        withStoredSettingsRestored {
            val state = AppState().apply {
                devices.clear()
                messages.clear()
                devices.add(Device("127.0.0.1"))
                activeAddress = "127.0.0.1"
                translate = false
                updateHotwordDictionary(HotwordDictionary())
                updateVoiceInputConfig {
                    it.copy(
                        enabled = true,
                        provider = VoiceInputProvider.QWEN,
                        apiKey = "test-key",
                        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                        model = "fun-asr",
                    )
                }
                updateInterpretationVoiceInputEnabled(true)
                updateAlwaysInterpretationEnabled(true)
                updateAlwaysInterpretationDelayMillis(500)
                toggleAlwaysInterpretationActive()
            }
            val recorder = FakeAudioRecorder()
            showChatPage(state, recorder)

            // The synced voice input starts a continuous managed capture.
            settleFrames()
            assertEquals(1, recorder.starts)
            assertEquals(0, recorder.lastMaxDurationSeconds)

            runOnUiThread { state.chatDraft = "typed while listening" }
            waitForIdle()
            mainClock.advanceTimeBy(700)
            waitForIdle()

            runOnUiThread {
                assertEquals("", state.chatDraft)
                val message = state.messages.singleOrNull()
                assertEquals(MessageRole.USER, message?.role)
                assertEquals("typed while listening", message?.text)
            }
        }
    }

    private fun SkikoComposeUiTest.showChatPage(state: AppState, recorder: FakeAudioRecorder) {
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalWindowInfo provides object : WindowInfo {
                override val isWindowFocused = true
            }) {
                MaterialTheme {
                    ChatPage(
                        state = state,
                        strings = LocaleStringsEn,
                        audioRecorderFactory = { recorder },
                    )
                }
            }
        }
    }

    private fun SkikoComposeUiTest.settleFrames() {
        mainClock.advanceTimeBy(1600)
        waitForIdle()
    }

    private class FakeAudioRecorder : AudioRecorder {
        var starts = 0
            private set
        var lastMaxDurationSeconds = -1
            private set

        override fun start(
            sampleRate: Int,
            maxDurationSeconds: Int,
            microphoneId: String,
            onPcmData: (ByteArray) -> Unit,
            onStopped: () -> Unit,
            onError: (String) -> Unit,
        ) {
            starts++
            lastMaxDurationSeconds = maxDurationSeconds
        }

        override fun stop() = Unit

        override fun release() = Unit
    }

    /**
     * The test drives AppState through its setters, which persist to the real user
     * storage. Snapshot everything that may be touched and restore it afterwards.
     */
    private fun withStoredSettingsRestored(block: () -> Unit) {
        val translationSettings = loadStoredTranslationSettings()
        val translationSecrets = loadStoredTranslationSecrets()
        val hotwords = loadStoredHotwordDictionary()
        val chatHistory = loadStoredChatHistory()
        val errorLogs = loadStoredErrorLogs()
        val alwaysEnabled = loadStoredAlwaysInterpretationEnabled()
        val alwaysDelay = loadStoredAlwaysInterpretationDelayMillis()
        val simultaneousEnabled = loadStoredSimultaneousInterpretationEnabled()
        try {
            block()
        } finally {
            saveStoredTranslationSettings(translationSettings)
            saveStoredTranslationSecrets(translationSecrets)
            saveStoredHotwordDictionary(hotwords)
            saveStoredChatHistory(chatHistory)
            saveStoredErrorLogs(errorLogs)
            saveStoredAlwaysInterpretationEnabled(alwaysEnabled)
            saveStoredAlwaysInterpretationDelayMillis(alwaysDelay)
            saveStoredSimultaneousInterpretationEnabled(simultaneousEnabled)
        }
    }
}

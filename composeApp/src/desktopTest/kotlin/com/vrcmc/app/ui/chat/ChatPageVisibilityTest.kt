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
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class ChatPageVisibilityTest {
    @Test
    fun hiddenPageStopsLoadingFramesAndShowsBackgroundUpdatesWhenReturning() = runSkikoComposeUiTest {
        val state = emptyChatState()
        state.chatDraft = "unsent draft"
        state.messages.add(ChatMessage("", MessageRole.ASSISTANT, timestamp = 1, isLoading = true))
        var visible by mutableStateOf(true)
        showChatPage(state, visible = { visible })

        repeat(2) {
            settleFrames()
            runOnUiThread { assertTrue(scene.hasInvalidations()) }

            runOnUiThread { visible = false }
            settleFrames()
            runOnUiThread {
                assertFalse(scene.hasInvalidations())
                assertEquals("unsent draft", state.chatDraft)
                assertTrue(state.messages.single().isLoading)
            }
            onNodeWithText(LocaleStringsEn.translating).assertDoesNotExist()
            onNode(hasSetTextAction()).assertDoesNotExist()

            runOnUiThread { visible = true }
        }

        runOnUiThread { visible = false }
        settleFrames()
        runOnUiThread {
            state.messages[0] = state.messages[0].copy(text = "completed translation", isLoading = false)
            state.messages.add(ChatMessage("received while hidden", MessageRole.USER, timestamp = 2))
        }
        settleFrames()
        runOnUiThread { assertFalse(scene.hasInvalidations()) }

        runOnUiThread { visible = true }
        settleFrames()
        runOnUiThread { assertFalse(scene.hasInvalidations()) }
        onNodeWithText("completed translation").assertExists()
        onNodeWithText("received while hidden").assertExists()
        onNodeWithText("unsent draft").assertExists()
    }

    @Test
    fun leavingChatRemovesHiddenInputFocusAndReturningDoesNotStartCursorFrames() = runSkikoComposeUiTest {
        val state = emptyChatState()
        state.devices.add(Device("127.0.0.1"))
        var visible by mutableStateOf(true)
        showChatPage(state, visible = { visible })

        onNode(hasSetTextAction()).performClick().assertIsFocused()
        runOnUiThread { visible = false }
        settleFrames()
        runOnUiThread { assertFalse(scene.hasInvalidations()) }
        onNode(hasSetTextAction()).assertDoesNotExist()

        runOnUiThread { visible = true }
        settleFrames()
        runOnUiThread { assertFalse(scene.hasInvalidations()) }
        onNode(hasSetTextAction()).assertIsNotFocused()
    }

    private fun emptyChatState() = AppState().apply {
        devices.clear()
        messages.clear()
    }

    private fun SkikoComposeUiTest.showChatPage(state: AppState, visible: () -> Boolean) {
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalWindowInfo provides object : WindowInfo {
                override val isWindowFocused = true
            }) {
                MaterialTheme {
                    ChatPage(state = state, strings = LocaleStringsEn, visible = visible())
                }
            }
        }
    }

    private fun SkikoComposeUiTest.settleFrames() {
        mainClock.advanceTimeBy(1600)
        waitForIdle()
    }
}

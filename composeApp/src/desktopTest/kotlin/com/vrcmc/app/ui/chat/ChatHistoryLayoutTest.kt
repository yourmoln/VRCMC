package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatHistoryLayoutTest {
    @get:Rule val compose = createComposeRule()
    private var height by mutableStateOf(600.dp)
    private var messageHeight by mutableStateOf(60.dp)
    private var messages by mutableStateOf(
        List(20) { ChatMessage("message-$it", MessageRole.USER, timestamp = it.toLong()) }
    )

    private fun showHistory() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(390.dp, height)) {
                    ChatHistoryList(
                        messages = messages,
                        modifier = Modifier.fillMaxSize().testTag("history"),
                        emptyContent = { Box(Modifier.fillParentMaxSize().testTag("empty")) },
                    ) { index, message ->
                        check(messages[index] == message)
                        val rememberedText = remember { message.text }
                        Box(Modifier.fillMaxWidth().height(messageHeight).testTag(message.text)) {
                            Box(Modifier.fillMaxSize().testTag("remembered-$rememberedText"))
                        }
                    }
                }
            }
        }
    }

    private fun assertLatestAtBottom() {
        val history = compose.onNodeWithTag("history").getUnclippedBoundsInRoot()
        val latest = compose.onNodeWithTag(messages.last().text).getUnclippedBoundsInRoot()
        assertEquals(history.bottom - 18.dp, latest.bottom)
    }

    @Test fun startupRestoresLatestMessage() {
        showHistory()
        assertLatestAtBottom()
    }

    @Test fun startupViewportResizeKeepsLatestVisible() {
        showHistory()
        compose.runOnIdle { height = 480.dp }
        assertLatestAtBottom()
    }

    @Test fun delayedMessageMeasurementKeepsLatestVisible() {
        showHistory()
        compose.runOnIdle { messageHeight = 90.dp }
        assertLatestAtBottom()
    }

    @Test fun tallLastMessageStartsAtItsBottom() {
        messageHeight = 720.dp
        showHistory()
        assertLatestAtBottom()
    }

    @Test fun shortHistoryStaysInChronologicalOrderAtTop() {
        messages = messages.take(3)
        showHistory()
        val first = compose.onNodeWithTag("message-0").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("message-1").getUnclippedBoundsInRoot()
        val third = compose.onNodeWithTag("message-2").getUnclippedBoundsInRoot()
        assertEquals(18.dp, first.top)
        assertTrue(first.bottom < second.top && second.bottom < third.top)
    }

    @Test fun scrollingToHistoryIsNotForcedBackToLatest() {
        showHistory()
        val list = compose.onNodeWithTag("history")
        list.performTouchInput { swipeDown() }
        compose.onNodeWithTag("message-19").assertDoesNotExist()
        compose.runOnIdle { height = 480.dp }
        compose.onNodeWithTag("message-19").assertDoesNotExist()
        compose.runOnIdle {
            messages = messages + ChatMessage("new-message", MessageRole.USER, timestamp = 21)
        }
        assertLatestAtBottom()
    }

    @Test fun emptyHistoryFillsViewportThenShowsFirstMessage() {
        messages = emptyList()
        showHistory()
        compose.onNodeWithTag("empty").assertHeightIsEqualTo(564.dp)
        compose.runOnIdle { messages = listOf(ChatMessage("first", MessageRole.USER)) }
        compose.onNodeWithTag("empty").assertDoesNotExist()
        compose.onNodeWithTag("first").assertTopPositionInRootIsEqualTo(18.dp)
    }

    @Test fun appendingMessagePreservesExistingBubbleState() {
        showHistory()
        compose.runOnIdle {
            messages = messages + ChatMessage("new-message", MessageRole.USER, timestamp = 21)
        }
        assertLatestAtBottom()
        compose.onNodeWithTag("remembered-message-19")
            .assert(hasAnyAncestor(hasTestTag("message-19")))
        compose.onNodeWithTag("remembered-new-message")
            .assert(hasAnyAncestor(hasTestTag("new-message")))
    }
}

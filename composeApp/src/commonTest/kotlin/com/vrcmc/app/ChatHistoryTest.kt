package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatHistoryTest {
    @Test
    fun restoresMessagesWithRolesAndTimestamps() {
        val messages = listOf(
            ChatMessage("Hello \"VRChat\"", MessageRole.USER, timestamp = 1_700_000_000_000),
            ChatMessage("你好", MessageRole.ASSISTANT, timestamp = 1_700_000_001_000),
        )

        assertEquals(messages, chatHistoryFromJson(messages.toChatHistoryJson()))
    }

    @Test
    fun doesNotPersistLoadingMessages() {
        val messages = listOf(
            ChatMessage("Hello", MessageRole.USER, timestamp = 100),
            ChatMessage("", MessageRole.ASSISTANT, timestamp = 101, isLoading = true),
        )

        assertEquals(listOf(messages.first()), chatHistoryFromJson(messages.toChatHistoryJson()))
    }
}

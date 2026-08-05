package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatHistoryTest {
    @Test
    fun restoresTranslationLanguage() {
        val message =
            ChatMessage(
                "Good evening",
                MessageRole.ASSISTANT,
                timestamp = 100,
                language = "English",
            )

        assertEquals(listOf(message), chatHistoryFromJson(listOf(message).toChatHistoryJson()))
    }

    @Test
    fun restoresMessagesWithRolesAndTimestamps() {
        val messages =
            listOf(
                ChatMessage("Hello \"VRChat\"", MessageRole.USER, timestamp = 1_700_000_000_000),
                ChatMessage("你好", MessageRole.ASSISTANT, timestamp = 1_700_000_001_000),
            )

        assertEquals(messages, chatHistoryFromJson(messages.toChatHistoryJson()))
    }

    @Test
    fun doesNotPersistLoadingMessages() {
        val messages =
            listOf(
                ChatMessage("Hello", MessageRole.USER, timestamp = 100),
                ChatMessage("", MessageRole.ASSISTANT, timestamp = 101, isLoading = true),
            )

        assertEquals(listOf(messages.first()), chatHistoryFromJson(messages.toChatHistoryJson()))
    }

    @Test
    fun capsPersistedHistory() {
        val messages =
            (0..maxSavedChatMessages).map {
                ChatMessage("message-$it", MessageRole.USER, timestamp = it.toLong())
            }
        val restored = chatHistoryFromJson(messages.toChatHistoryJson())
        assertEquals(maxSavedChatMessages, restored.size)
        assertEquals("message-1", restored.first().text)
    }
}

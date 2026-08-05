package com.vrcmc.app

enum class MessageRole {
    USER,
    ASSISTANT,
}

data class ChatMessage(
    val text: String,
    val role: MessageRole,
    val timestamp: Long = currentTimeMillis(),
    val isLoading: Boolean = false,
    val language: String? = null,
)

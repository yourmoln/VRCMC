package com.vrcmc.app

import kotlinx.serialization.json.*

internal const val maxSavedChatMessages = 200

internal fun List<ChatMessage>.toChatHistoryJson(): String = buildJsonArray {
    this@toChatHistoryJson.filterNot(ChatMessage::isLoading).takeLast(maxSavedChatMessages).forEach { message ->
        addJsonObject {
            put("text", message.text)
            put("role", message.role.name)
            put("timestamp", message.timestamp)
        }
    }
}.toString()

internal fun chatHistoryFromJson(value: String): List<ChatMessage> = runCatching {
    if (value.isBlank()) return emptyList()
    Json.parseToJsonElement(value).jsonArray.mapNotNull { element ->
        val item = element.jsonObject
        val text = item["text"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val role = item["role"]?.jsonPrimitive?.content?.let { runCatching { MessageRole.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
        val timestamp = item["timestamp"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
        ChatMessage(text = text, role = role, timestamp = timestamp)
    }.takeLast(maxSavedChatMessages)
}.getOrDefault(emptyList())

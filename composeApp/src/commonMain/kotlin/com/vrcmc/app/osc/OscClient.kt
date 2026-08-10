package com.vrcmc.app

internal const val maxChatboxCharacters = 144
internal const val maxChatboxLines = 9

internal fun chatboxInputCharacterLimit(translationEnabled: Boolean, translationLanguageCount: Int): Int {
    val outputParts = if (translationEnabled) translationLanguageCount.coerceIn(1, 2) + 1 else 1
    return maxChatboxCharacters / outputParts
}

internal fun isValidChatboxText(text: String, maxCharacters: Int = maxChatboxCharacters): Boolean =
    text.isNotBlank() &&
        text.length <= maxCharacters &&
        text.lineSequence().count() <= maxChatboxLines

internal fun chatboxPacket(text: String): ByteArray {
    fun padded(value: String): ByteArray {
        val raw = value.encodeToByteArray() + byteArrayOf(0)
        return raw + ByteArray((4 - raw.size % 4) % 4)
    }
    // Matches vrctts/python-osc: [String(text), Bool(true)]. True has no payload.
    return padded("/chatbox/input") + padded(",sT") + padded(text)
}

internal fun chatboxTypingPacket(typing: Boolean = true): ByteArray {
    fun padded(value: String): ByteArray {
        val raw = value.encodeToByteArray() + byteArrayOf(0)
        return raw + ByteArray((4 - raw.size % 4) % 4)
    }
    return padded("/chatbox/typing") + padded(if (typing) ",T" else ",F")
}

expect suspend fun sendChatboxOsc(address: String, text: String, port: Int = 9000): Boolean

expect suspend fun sendChatboxTypingOsc(
    address: String,
    typing: Boolean = true,
    port: Int = 9000,
): Boolean

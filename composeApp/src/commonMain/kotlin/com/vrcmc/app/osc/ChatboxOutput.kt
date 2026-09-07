package com.vrcmc.app

internal enum class ChatboxSendResult {
    SENT,
    SENT_OVER_LIMIT,
    EMPTY,
    FAILED,
}

// Limits describe the transmitted payload; they must never prevent a send.
internal suspend fun sendChatboxOutput(
    device: Device,
    text: String,
    send: suspend (String, String, Int) -> Boolean = ::sendChatboxOsc,
): ChatboxSendResult {
    if (text.isBlank()) return ChatboxSendResult.EMPTY
    val exceedsLimits = text.length > maxChatboxCharacters ||
        text.lineSequence().count() > maxChatboxLines
    if (!send(device.address, text, device.receivePort)) return ChatboxSendResult.FAILED
    return if (exceedsLimits) ChatboxSendResult.SENT_OVER_LIMIT else ChatboxSendResult.SENT
}

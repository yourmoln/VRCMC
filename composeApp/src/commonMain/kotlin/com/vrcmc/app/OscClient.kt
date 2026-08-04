package com.vrcmc.app

internal fun chatboxPacket(text: String): ByteArray {
    fun padded(value: String): ByteArray {
        val raw = value.encodeToByteArray() + byteArrayOf(0)
        return raw + ByteArray((4 - raw.size % 4) % 4)
    }
    // Matches vrctts/python-osc: [String(text), Bool(true)]. True has no payload.
    return padded("/chatbox/input") + padded(",sT") + padded(text)
}

expect suspend fun sendChatboxOsc(address: String, text: String, port: Int = 9000): Boolean

package com.vrcmc.app

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun sendChatboxOsc(address: String, text: String, port: Int): Boolean =
    withContext(Dispatchers.IO) {
        if (!isValidChatboxText(text)) return@withContext false
        runCatching {
                val data = chatboxPacket(text)
                val target = InetAddress.getByName(address.trim())
                DatagramSocket().use { it.send(DatagramPacket(data, data.size, target, port)) }
                true
            }
            .getOrDefault(false)
    }

actual suspend fun sendChatboxTypingOsc(address: String, typing: Boolean, port: Int): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
                val data = chatboxTypingPacket(typing)
                val target = InetAddress.getByName(address.trim())
                DatagramSocket().use { it.send(DatagramPacket(data, data.size, target, port)) }
                true
            }
            .getOrDefault(false)
    }

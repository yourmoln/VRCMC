package com.vrcmc.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

actual suspend fun sendChatboxOsc(address: String, text: String, port: Int): Boolean = withContext(Dispatchers.IO) {
    runCatching { val data = chatboxPacket(text); val target = InetAddress.getByName(address.trim()); DatagramSocket().use { it.send(DatagramPacket(data, data.size, target, port)) }; true }.getOrDefault(false)
}

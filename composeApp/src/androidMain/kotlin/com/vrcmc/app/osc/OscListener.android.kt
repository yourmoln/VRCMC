package com.vrcmc.app

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

actual fun vrchatMuteSelfEvents(sourceAddress: String, port: Int): Flow<Boolean> = callbackFlow {
    val socket =
        DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(port))
        }
    val receiver =
        launch(Dispatchers.IO) {
            val allowedAddresses =
                runCatching { java.net.InetAddress.getAllByName(sourceAddress.trim()).toSet() }
                    .getOrElse {
                        close(it)
                        return@launch
                    }
            val buffer = ByteArray(2048)
            while (!socket.isClosed) {
                val packet = DatagramPacket(buffer, buffer.size)
                runCatching { socket.receive(packet) }
                    .getOrElse {
                        if (!socket.isClosed) close(it)
                        return@launch
                    }
                if (packet.address !in allowedAddresses) continue
                parseMuteSelfOsc(
                        packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                    )
                    ?.let { trySend(it) }
            }
        }
    awaitClose {
        socket.close()
        receiver.cancel()
    }
}

package com.vrcmc.app

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.posix.AF_INET
import platform.posix.EAGAIN
import platform.posix.EWOULDBLOCK
import platform.posix.F_SETFL
import platform.posix.INADDR_ANY
import platform.posix.IPPROTO_UDP
import platform.posix.O_NONBLOCK
import platform.posix.SOCK_DGRAM
import platform.posix.bind as posixBind
import platform.posix.close as posixClose
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.memset
import platform.posix.recv
import platform.posix.sockaddr_in
import platform.posix.socket

@OptIn(ExperimentalForeignApi::class)
actual fun vrchatMuteSelfEvents(sourceAddress: String, port: Int): Flow<Boolean> = callbackFlow {
    @Suppress("UNUSED_VARIABLE") val configuredSourceAddress = sourceAddress
    val descriptor = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
    if (descriptor < 0) {
        close(IllegalStateException("Unable to create UDP socket"))
        return@callbackFlow
    }
    val bound = memScoped {
        val address = alloc<sockaddr_in>()
        memset(address.ptr, 0, sizeOf<sockaddr_in>().convert())
        address.sin_len = sizeOf<sockaddr_in>().convert()
        address.sin_family = AF_INET.convert()
        address.sin_port = hostToNetworkShort(port)
        address.sin_addr.s_addr = INADDR_ANY
        posixBind(descriptor, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0
    }
    if (!bound || fcntl(descriptor, F_SETFL, O_NONBLOCK) < 0) {
        posixClose(descriptor)
        close(IllegalStateException("Unable to listen on UDP port $port"))
        return@callbackFlow
    }

    val receiver = launch(Dispatchers.Default) {
        val buffer = ByteArray(2048)
        while (isActive) {
            val size = buffer.usePinned { pinned -> recv(descriptor, pinned.addressOf(0), buffer.size.convert(), 0) }
            when {
                size > 0 -> parseMuteSelfOsc(buffer.copyOf(size.toInt()))?.let { trySend(it) }
                size < 0 && errno != EAGAIN && errno != EWOULDBLOCK -> {
                    close(IllegalStateException("OSC receive failed"))
                    return@launch
                }
                else -> delay(20)
            }
        }
    }
    awaitClose {
        posixClose(descriptor)
        receiver.cancel()
    }
}

private fun hostToNetworkShort(value: Int): UShort =
    (((value and 0xff) shl 8) or ((value ushr 8) and 0xff)).toUShort()

package com.vrcmc.app

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.posix.AF_INET
import platform.posix.addrinfo
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
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.memset
import platform.posix.recv
import platform.posix.recvfrom
import platform.posix.sockaddr_in
import platform.posix.socket

@OptIn(ExperimentalForeignApi::class)
actual fun vrchatMuteSelfEvents(sourceAddress: String, port: Int): Flow<Boolean> = callbackFlow {
    val allowedAddresses = resolveIpv4Addresses(sourceAddress)
    if (allowedAddresses.isEmpty()) {
        close(IllegalArgumentException("Unable to resolve VRChat OSC address: $sourceAddress"))
        return@callbackFlow
    }
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
            var sourceAddress = 0u
            val size = memScoped {
                val source = alloc<sockaddr_in>()
                val sourceLength = alloc<UIntVar>().apply { value = sizeOf<sockaddr_in>().convert() }
                val received = buffer.usePinned { pinned ->
                    recvfrom(
                        descriptor,
                        pinned.addressOf(0),
                        buffer.size.convert(),
                        0,
                        source.ptr.reinterpret(),
                        sourceLength.ptr,
                    )
                }
                sourceAddress = source.sin_addr.s_addr
                received
            }
            when {
                size > 0 && sourceAddress in allowedAddresses -> parseMuteSelfOsc(buffer.copyOf(size.toInt()))?.let { trySend(it) }
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

@OptIn(ExperimentalForeignApi::class)
private fun resolveIpv4Addresses(value: String): Set<UInt> = memScoped {
    val hints = alloc<addrinfo>().apply {
        ai_flags = 0
        ai_family = AF_INET
        ai_socktype = SOCK_DGRAM
        ai_protocol = IPPROTO_UDP
        ai_addrlen = 0u
        ai_canonname = null
        ai_addr = null
        ai_next = null
    }
    val result = alloc<CPointerVar<addrinfo>>()
    if (getaddrinfo(value.trim(), null, hints.ptr, result.ptr) != 0) return@memScoped emptySet()
    try {
        buildSet {
            var current = result.value
            while (current != null) {
                val endpoint = current.pointed
                endpoint.ai_addr?.let { pointer ->
                    add(pointer.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr)
                }
                current = endpoint.ai_next
            }
        }
    } finally {
        result.value?.let(::freeaddrinfo)
    }
}

private fun hostToNetworkShort(value: Int): UShort =
    (((value and 0xff) shl 8) or ((value ushr 8) and 0xff)).toUShort()

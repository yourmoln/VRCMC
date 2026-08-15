package com.vrcmc.app

import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.AF_UNSPEC
import platform.posix.IPPROTO_UDP
import platform.posix.SOCK_DGRAM
import platform.posix.addrinfo
import platform.posix.close
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.sendto
import platform.posix.socket

@OptIn(ExperimentalForeignApi::class)
actual suspend fun sendChatboxOsc(address: String, text: String, port: Int): Boolean = memScoped {
    val hints =
        alloc<addrinfo>().apply {
            ai_flags = 0
            ai_family = AF_UNSPEC
            ai_socktype = SOCK_DGRAM
            ai_protocol = IPPROTO_UDP
            ai_addrlen = 0u
            ai_canonname = null
            ai_addr = null
            ai_next = null
        }
    val result = alloc<CPointerVar<addrinfo>>()
    if (getaddrinfo(address.trim(), port.toString(), hints.ptr, result.ptr) != 0)
        return@memScoped false

    val packet = chatboxPacket(text)
    try {
        var current = result.value
        while (current != null) {
            val endpoint = current.pointed
            val descriptor = socket(endpoint.ai_family, endpoint.ai_socktype, endpoint.ai_protocol)
            if (descriptor >= 0) {
                val sent =
                    try {
                        packet.usePinned { pinned ->
                            sendto(
                                descriptor,
                                pinned.addressOf(0),
                                packet.size.convert(),
                                0,
                                endpoint.ai_addr,
                                endpoint.ai_addrlen,
                            )
                        }
                    } finally {
                        close(descriptor)
                    }
                if (sent == packet.size.toLong()) return@memScoped true
            }
            current = endpoint.ai_next
        }
        false
    } finally {
        result.value?.let(::freeaddrinfo)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun sendChatboxTypingOsc(
    address: String,
    typing: Boolean,
    port: Int,
): Boolean =
    memScoped {
        val hints =
            alloc<addrinfo>().apply {
                ai_flags = 0
                ai_family = AF_UNSPEC
                ai_socktype = SOCK_DGRAM
                ai_protocol = IPPROTO_UDP
                ai_addrlen = 0u
                ai_canonname = null
                ai_addr = null
                ai_next = null
            }
        val result = alloc<CPointerVar<addrinfo>>()
        if (getaddrinfo(address.trim(), port.toString(), hints.ptr, result.ptr) != 0)
            return@memScoped false

        val packet = chatboxTypingPacket(typing)
        try {
            var current = result.value
            while (current != null) {
                val endpoint = current.pointed
                val descriptor =
                    socket(endpoint.ai_family, endpoint.ai_socktype, endpoint.ai_protocol)
                if (descriptor >= 0) {
                    val sent =
                        try {
                            packet.usePinned { pinned ->
                                sendto(
                                    descriptor,
                                    pinned.addressOf(0),
                                    packet.size.convert(),
                                    0,
                                    endpoint.ai_addr,
                                    endpoint.ai_addrlen,
                                )
                            }
                        } finally {
                            close(descriptor)
                        }
                    if (sent == packet.size.toLong()) return@memScoped true
                }
                current = endpoint.ai_next
            }
            false
        } finally {
            result.value?.let(::freeaddrinfo)
        }
    }

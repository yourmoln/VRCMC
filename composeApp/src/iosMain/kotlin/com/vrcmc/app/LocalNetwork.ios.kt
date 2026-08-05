package com.vrcmc.app

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import platform.posix.AF_INET
import platform.posix.INET_ADDRSTRLEN
import platform.posix.freeifaddrs
import platform.posix.getifaddrs
import platform.posix.ifaddrs
import platform.posix.inet_ntop
import platform.posix.sockaddr_in

@OptIn(ExperimentalForeignApi::class)
actual fun localIpv4Addresses(): List<String> = memScoped {
    val interfaces = alloc<CPointerVar<ifaddrs>>()
    if (getifaddrs(interfaces.ptr) != 0) return@memScoped emptyList()

    val addresses = mutableListOf<String>()
    try {
        var current = interfaces.value
        while (current != null) {
            val socketAddress = current.pointed.ifa_addr
            if (socketAddress != null && socketAddress.pointed.sa_family.toInt() == AF_INET) {
                val ipv4Address = socketAddress.reinterpret<sockaddr_in>()
                val buffer = allocArray<ByteVar>(INET_ADDRSTRLEN)
                if (inet_ntop(AF_INET, ipv4Address.pointed.sin_addr.ptr, buffer, INET_ADDRSTRLEN.toUInt()) != null) {
                    addresses += buffer.toKString()
                }
            }
            current = current.pointed.ifa_next
        }
    } finally {
        freeifaddrs(interfaces.value)
    }
    addresses
}

actual suspend fun scanLocalNetworkDevices(): List<DiscoveredNetworkDevice> = emptyList()

package com.vrcmc.app

import java.net.Inet4Address
import java.net.NetworkInterface

actual fun localIpv4Addresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .flatMap { network ->
            network.inetAddresses.toList()
                .filterIsInstance<Inet4Address>()
                .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
                .map { it.hostAddress }
        }
}.getOrDefault(emptyList())

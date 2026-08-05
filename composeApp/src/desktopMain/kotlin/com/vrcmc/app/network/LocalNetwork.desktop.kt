package com.vrcmc.app

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

actual val localNetworkScanSupported: Boolean = true

actual fun localIpv4Addresses(): List<String> =
    runCatching {
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { network ->
                    network.inetAddresses
                        .toList()
                        .filterIsInstance<Inet4Address>()
                        .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
                        .map { it.hostAddress }
                }
        }
        .getOrDefault(emptyList())

actual suspend fun scanLocalNetworkDevices(): List<DiscoveredNetworkDevice> = coroutineScope {
    val ownAddresses = localIpv4Addresses()
    val targets = localIpv4ScanTargets(ownAddresses)
    val reachable = mutableSetOf<String>()

    targets.chunked(32).forEach { batch ->
        reachable +=
            batch
                .map { ip ->
                    async(Dispatchers.IO) {
                        runCatching { ip.takeIf { InetAddress.getByName(it).isReachable(350) } }
                            .getOrNull()
                    }
                }
                .awaitAll()
                .filterNotNull()
    }

    val neighborAddresses =
        withContext(Dispatchers.IO) {
            runCatching {
                    ProcessBuilder("arp", "-a").redirectErrorStream(true).start().let { process ->
                        val output = process.inputStream.bufferedReader().use { it.readText() }
                        process.waitFor()
                        Regex("(?:\\d{1,3}\\.){3}\\d{1,3}")
                            .findAll(output)
                            .map { it.value }
                            .filter { it in targets }
                            .toList()
                    }
                }
                .getOrDefault(emptyList())
        }

    (reachable + neighborAddresses)
        .distinct()
        .map { ip -> async(Dispatchers.IO) { discoveredDevice(ip) } }
        .awaitAll()
        .sortedBy { device ->
            device.ipAddress.split('.').fold(0L) { value, part -> value * 256 + part.toLong() }
        }
}

private fun discoveredDevice(ip: String): DiscoveredNetworkDevice {
    val hostname = runCatching { InetAddress.getByName(ip).canonicalHostName }.getOrDefault(ip)
    return DiscoveredNetworkDevice(hostname.takeUnless { it == ip }.orEmpty(), ip)
}

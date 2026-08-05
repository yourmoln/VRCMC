package com.vrcmc.app

actual val localNetworkScanSupported: Boolean = false

actual fun localIpv4Addresses(): List<String> = emptyList()

actual suspend fun scanLocalNetworkDevices(): List<DiscoveredNetworkDevice> = emptyList()

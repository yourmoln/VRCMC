package com.vrcmc.app

expect fun localIpv4Addresses(): List<String>

internal fun preferredLocalIpv4Address(addresses: List<String>): String? =
    addresses
        .asSequence()
        .map(String::trim)
        .filter(::isUsableIpv4Address)
        .distinct()
        .sortedByDescending(::localIpv4Priority)
        .firstOrNull()

private fun isUsableIpv4Address(address: String): Boolean {
    val octets = address.split('.').mapNotNull(String::toIntOrNull)
    return octets.size == 4 &&
        octets.all { it in 0..255 } &&
        octets[0] !in listOf(0, 127) &&
        !(octets[0] == 169 && octets[1] == 254) &&
        address != "255.255.255.255"
}

private fun localIpv4Priority(address: String): Int = when {
    address.startsWith("192.168.") -> 3
    address.startsWith("10.") -> 2
    address.substringBefore('.').toIntOrNull() == 172 &&
        address.split('.')[1].toIntOrNull() in 16..31 -> 2
    else -> 1
}

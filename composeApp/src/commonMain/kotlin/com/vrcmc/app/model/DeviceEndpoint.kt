package com.vrcmc.app

internal fun parseDeviceEndpoint(value: String): Device? {
    val endpoint = value.trim()
    if (endpoint.isBlank() || endpoint.any(Char::isWhitespace)) return null

    val bracketedTriple = Regex("^(\\d+):\\[([^]]+)]:(\\d+)$").matchEntire(endpoint)
    if (bracketedTriple != null) {
        val (receivePort, address, sendPort) = bracketedTriple.destructured
        return deviceOrNull(address, receivePort, sendPort)
    }

    if (endpoint.startsWith("[")) {
        val closingBracket = endpoint.indexOf(']')
        if (closingBracket <= 1) return null
        val address = endpoint.substring(1, closingBracket)
        val portPart = endpoint.substring(closingBracket + 1)
        val receivePort =
            when {
                portPart.isEmpty() -> return Device(address)
                portPart.startsWith(":") -> portPart.drop(1).toIntOrNull()
                else -> null
            } ?: return null
        return Device(address, receivePort).takeIf { receivePort in 1..65535 }
    }

    return when (endpoint.count { it == ':' }) {
        0 -> Device(endpoint)
        1 -> {
            val address = endpoint.substringBeforeLast(':')
            val receivePort = endpoint.substringAfterLast(':')
            deviceOrNull(address, receivePort, "9001")
        }
        2 -> {
            val parts = endpoint.split(':')
            deviceOrNull(parts[1], parts[0], parts[2])
        }
        else -> Device(endpoint)
    }
}

private fun deviceOrNull(address: String, receivePort: String, sendPort: String): Device? {
    val parsedReceivePort = receivePort.toIntOrNull()
    val parsedSendPort = sendPort.toIntOrNull()
    return Device(address, parsedReceivePort ?: return null, parsedSendPort ?: return null).takeIf {
        address.isNotBlank() && parsedReceivePort in 1..65535 && parsedSendPort in 1..65535
    }
}

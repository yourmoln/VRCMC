package com.vrcmc.app

import kotlinx.coroutines.flow.Flow

internal const val defaultVrchatSendPort = 9001
private const val muteSelfAddress = "/avatar/parameters/MuteSelf"

expect fun vrchatMuteSelfEvents(sourceAddress: String, port: Int = defaultVrchatSendPort): Flow<Boolean>

internal fun parseMuteSelfOsc(packet: ByteArray): Boolean? {
    val (address, typeTagOffset) = readOscString(packet, 0) ?: return null
    if (!address.equals(muteSelfAddress, ignoreCase = true)) return null
    val (typeTags, valueOffset) = readOscString(packet, typeTagOffset) ?: return null
    return when (typeTags.getOrNull(1)) {
        'T' -> true
        'F' -> false
        'i' -> readInt(packet, valueOffset)?.let { it != 0 }
        'f' -> readInt(packet, valueOffset)?.let { Float.fromBits(it) != 0f }
        's' -> readOscString(packet, valueOffset)?.first?.trim()?.lowercase()?.let {
            when (it) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> null
            }
        }
        else -> null
    }
}

private fun readOscString(packet: ByteArray, offset: Int): Pair<String, Int>? {
    if (offset !in packet.indices) return null
    var end = offset
    while (end < packet.size && packet[end] != 0.toByte()) end++
    if (end == packet.size) return null
    val next = ((end + 1 + 3) / 4) * 4
    if (next > packet.size) return null
    return packet.copyOfRange(offset, end).decodeToString() to next
}

private fun readInt(packet: ByteArray, offset: Int): Int? {
    if (offset < 0 || offset + 4 > packet.size) return null
    return ((packet[offset].toInt() and 0xff) shl 24) or
        ((packet[offset + 1].toInt() and 0xff) shl 16) or
        ((packet[offset + 2].toInt() and 0xff) shl 8) or
        (packet[offset + 3].toInt() and 0xff)
}

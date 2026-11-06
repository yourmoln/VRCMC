package com.vrcmc.app

import io.ktor.util.hex

// Edge TTS v2 signs a salted Windows timestamp with CS64 and SHA-256.
// The salt and checksum arithmetic are part of the service's wire protocol.
// Protocol reference: https://github.com/rany2/edge-tts/blob/master/src/edge_tts/drm.py
internal fun edgeTtsGec(unixMillis: Long): String {
    val ticks = (unixMillis / 1_000 + 11_644_473_600L) * 10_000_000
    val rounded = ticks - ticks % 3_000_000_000L
    val data = ("Copyright (C) Microsoft Corporation. All rights reserved." +
        "$rounded$edgeTtsToken").encodeToByteArray()
    val digest = speechSha256(data)
    return "${hex(edgeTtsCs64(data, digest))}-${hex(digest)}".uppercase()
}

private val cs64OffsetFactors = intArrayOf(0xEF0569FB.toInt(), 0xC31713DB.toInt())
private val cs64SwapFactors = intArrayOf(0x76F50F34, 0x1881CE3C)
private val cs64SwapFinalFactors = intArrayOf(0xC3EFEA97.toInt(), 0x35BD1EC9)
private val cs64StateFactors = arrayOf(
    intArrayOf(0xCF98B111.toInt(), 0x87085B9F.toInt(), 0x12CEB96D, 0x257E1D83),
    intArrayOf(0xA27416F5.toInt(), 0xD38396FF.toInt(), 0x7C932B89, 0xBFA49F69.toInt()),
)

private fun ByteArray.wordLittleEndian(offset: Int): Int {
    var word = 0
    for (byte in 0..3) {
        word = word or ((getOrNull(offset + byte)?.toInt()?.and(255) ?: 0) shl (byte * 8))
    }
    return word
}

private fun edgeTtsCs64(data: ByteArray, digest: ByteArray): ByteArray {
    val keys = intArrayOf(digest.wordLittleEndian(0) or 1, digest.wordLittleEndian(4) or 1)
    var state = 0
    var total = 0
    var swappedTotal = 0
    var swapped = 0
    // CS64 consumes pairs of little-endian words, padding the final pair with zeros.
    // Int overflow intentionally implements arithmetic modulo 2^32 on every platform.
    val wordCount = (data.size + 7) / 8 * 2
    for (index in 0 until wordCount) {
        val lane = index % 2
        val word = data.wordLittleEndian(index * 4)
        val term = word * keys[lane]
        val offset = word.rotateLeft(16) * cs64OffsetFactors[lane]
        val mixed = (offset + term).rotateLeft(16) * cs64SwapFactors[lane]
        swapped = mixed.rotateLeft(16) * cs64SwapFinalFactors[lane] + mixed
        swappedTotal += swapped

        state = state * keys[lane] + term
        for (factor in cs64StateFactors[lane]) state = state.rotateLeft(16) * factor
        total += state
    }
    val checksum = intArrayOf(swapped xor state, swappedTotal xor total)
    return ByteArray(8) { index -> (checksum[index / 4] ushr (index % 4 * 8)).toByte() }
}

package com.vrcmc.app

import kotlin.math.roundToInt

private data class Pcm16Wave(
    val sampleRate: Int,
    val channels: Int,
    val samples: FloatArray,
)

internal fun speechPcmSamples(wav: ByteArray): FloatArray {
    require(wav.size >= 44 && ascii(wav, 0, 4) == "RIFF" && ascii(wav, 8, 4) == "WAVE") {
        "Invalid WAV audio"
    }
    var offset = 12
    var channels = 0
    var sampleRate = 0
    var bitsPerSample = 0
    var formatCode = 0
    var dataOffset = -1
    var dataSize = 0
    while (offset + 8 <= wav.size) {
        val id = ascii(wav, offset, 4)
        val size = readUInt32(wav, offset + 4)
        require(size <= Int.MAX_VALUE) { "WAV chunk is too large" }
        val content = offset + 8
        require(content.toLong() + size <= wav.size) { "Truncated WAV chunk" }
        when (id) {
            "fmt " -> {
                require(size >= 16) { "Invalid WAV format chunk" }
                formatCode = readUInt16(wav, content)
                channels = readUInt16(wav, content + 2)
                sampleRate = readUInt32(wav, content + 4).toInt()
                bitsPerSample = readUInt16(wav, content + 14)
            }
            "data" -> {
                dataOffset = content
                dataSize = size.toInt()
            }
        }
        offset = content + size.toInt() + (size.toInt() and 1)
    }
    require(formatCode == 1) { "Only PCM WAV audio is supported" }
    require(channels in 1..2) { "Unsupported WAV channel count: $channels" }
    require(sampleRate > 0) { "Invalid WAV sample rate" }
    require(bitsPerSample == 16) { "Only PCM16 WAV audio is supported" }
    require(dataOffset >= 0) { "WAV audio has no data chunk" }

    val frameSize = channels * 2
    val frames = dataSize / frameSize
    require(frames > 0) { "Invalid PCM audio" }
    val monoSamples = FloatArray(frames) { frame ->
        var sum = 0
        for (channel in 0 until channels) {
            val sampleOffset = dataOffset + frame * frameSize + channel * 2
            val low = wav[sampleOffset].toInt() and 0xff
            val high = wav[sampleOffset + 1].toInt()
            sum += (high shl 8) or low
        }
        (sum.toFloat() / channels) / 32768f
    }
    return resampleTo16Khz(monoSamples, sampleRate)
}

private fun resampleTo16Khz(samples: FloatArray, sampleRate: Int): FloatArray {
    if (sampleRate == 16_000) return samples
    val outputSize = (samples.size.toLong() * 16_000L / sampleRate).toInt()
    require(outputSize > 0) { "Audio is too short" }
    return FloatArray(outputSize) { index ->
        val position = index.toDouble() * sampleRate / 16_000.0
        val left = position.toInt().coerceIn(0, samples.lastIndex)
        val right = (left + 1).coerceAtMost(samples.lastIndex)
        val fraction = (position - left).toFloat()
        samples[left] + (samples[right] - samples[left]) * fraction
    }
}

private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
    bytes.copyOfRange(offset, offset + length).decodeToString()

private fun readUInt16(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

private fun readUInt32(bytes: ByteArray, offset: Int): Long =
    (bytes[offset].toLong() and 0xff) or
        ((bytes[offset + 1].toLong() and 0xff) shl 8) or
        ((bytes[offset + 2].toLong() and 0xff) shl 16) or
        ((bytes[offset + 3].toLong() and 0xff) shl 24)

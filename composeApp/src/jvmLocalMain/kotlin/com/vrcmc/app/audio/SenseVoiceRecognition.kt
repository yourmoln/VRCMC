package com.vrcmc.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

private const val senseVoiceSampleRate = 16_000
private const val senseVoiceFrameLength = 400
private const val senseVoiceFrameShift = 160
private const val senseVoiceFftSize = 512
internal const val localSpeechMelBins = 80
private const val senseVoiceLowFrequency = 20.0
private const val senseVoiceHighFrequency = 8_000.0
private const val senseVoiceFftBinWidth = senseVoiceSampleRate.toDouble() / senseVoiceFftSize
private const val senseVoiceLogFloor = 1.1920929e-7

internal class SenseVoiceEngine(model: Path) : LocalSpeechEngine {
    private val environment = OrtEnvironment.getEnvironment()
    private val session = OrtSession.SessionOptions().use { options ->
        options.setInterOpNumThreads(1)
        options.setIntraOpNumThreads(
            Runtime.getRuntime().availableProcessors().let { (it / 2).coerceIn(1, 6) },
        )
        environment.createSession(model.toString(), options)
    }
    private val metadata = session.metadata.customMetadata
    private val lfrWindowSize = metadata.getValue("lfr_window_size").toInt()
    private val lfrWindowShift = metadata.getValue("lfr_window_shift").toInt()
    private val featureDimension = localSpeechMelBins * lfrWindowSize
    private val negMean = parseSenseVoiceNumbers(metadata.getValue("neg_mean"), featureDimension)
    private val invStddev = parseSenseVoiceNumbers(metadata.getValue("inv_stddev"), featureDimension)
    private val tokens = Files.readAllLines(
        model.resolveSibling("tokens.txt"),
        StandardCharsets.UTF_8,
    ).map { line -> line.substringBeforeLast(' ') }
    private val melBanks = localSpeechMelBanks()

    override fun transcribe(samples: FloatArray, language: String): String {
        val fbank = localSpeechFbank(samples, melBanks)
        if (fbank.isEmpty()) return ""
        val features = localSpeechApplyLfr(fbank, localSpeechMelBins, lfrWindowSize, lfrWindowShift)
        applySenseVoiceCmvn(features, negMean, invStddev)
        val frames = features.size / featureDimension
        if (frames == 0) return ""

        val languageId = senseVoiceLanguageId(language)
        val textNormId = metadata.getValue("with_itn").toInt()
        val logits = try {
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(features),
                longArrayOf(1, frames.toLong(), featureDimension.toLong()),
            ).use { x ->
                OnnxTensor.createTensor(
                    environment,
                    IntBuffer.wrap(intArrayOf(frames)),
                    longArrayOf(1),
                ).use { xLength ->
                    OnnxTensor.createTensor(
                        environment,
                        IntBuffer.wrap(intArrayOf(languageId)),
                        longArrayOf(1),
                    ).use { languageTensor ->
                        OnnxTensor.createTensor(
                            environment,
                            IntBuffer.wrap(intArrayOf(textNormId)),
                            longArrayOf(1),
                        ).use { textNorm ->
                            session.run(
                                mapOf(
                                    "x" to x,
                                    "x_length" to xLength,
                                    "language" to languageTensor,
                                    "text_norm" to textNorm,
                                ),
                            ).use { result ->
                                val output = result.get("logits").orElseThrow() as OnnxTensor
                                val values = output.floatBuffer
                                val shape = output.info.shape
                                val copied = FloatArray(values.remaining())
                                values.get(copied)
                                CtcLogits(copied, shape[1].toInt(), shape[2].toInt())
                            }
                        }
                    }
                }
            }
        } catch (error: Exception) {
            throw IllegalStateException("SenseVoice inference failed", error)
        }
        return decodeCtcLogits(
            logits.values,
            logits.frames,
            logits.vocabularySize,
            tokens,
            blankId = 0,
        )
    }

    private fun senseVoiceLanguageId(language: String): Int {
        val key = when (language.lowercase()) {
            "zh" -> "lang_zh"
            "en" -> "lang_en"
            "yue" -> "lang_yue"
            "ja" -> "lang_ja"
            "ko" -> "lang_ko"
            else -> "lang_auto"
        }
        return metadata.getValue(key).toInt()
    }

    override fun close() = session.close()
}

internal data class CtcLogits(
    val values: FloatArray,
    val frames: Int,
    val vocabularySize: Int,
)

internal fun localSpeechFbank(
    samples: FloatArray,
    melBanks: Array<DoubleArray> = localSpeechMelBanks(),
): FloatArray {
    if (samples.size < senseVoiceFrameLength) return FloatArray(0)
    val frames = 1 + (samples.size - senseVoiceFrameLength) / senseVoiceFrameShift
    val features = FloatArray(frames * localSpeechMelBins)
    val hamming = DoubleArray(senseVoiceFrameLength) { index ->
        0.54 - 0.46 * cos(2.0 * PI * index / (senseVoiceFrameLength - 1))
    }
    val real = DoubleArray(senseVoiceFftSize)
    val imaginary = DoubleArray(senseVoiceFftSize)
    var output = 0
    for (frame in 0 until frames) {
        val start = frame * senseVoiceFrameShift
        var mean = 0.0
        for (index in 0 until senseVoiceFrameLength) {
            real[index] = samples[start + index] * 32768.0
            mean += real[index]
        }
        mean /= senseVoiceFrameLength
        for (index in 0 until senseVoiceFrameLength) real[index] -= mean
        for (index in senseVoiceFrameLength - 1 downTo 1) {
            real[index] -= 0.97 * real[index - 1]
        }
        real[0] -= 0.97 * real[0]
        for (index in 0 until senseVoiceFrameLength) {
            real[index] *= hamming[index]
            imaginary[index] = 0.0
        }
        for (index in senseVoiceFrameLength until senseVoiceFftSize) {
            real[index] = 0.0
            imaginary[index] = 0.0
        }
        fft(real, imaginary)
        for (mel in 0 until localSpeechMelBins) {
            var energy = 0.0
            for (bin in 0..senseVoiceFftSize / 2) {
                val power = real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
                energy += melBanks[mel][bin] * power
            }
            features[output++] = ln(max(energy, senseVoiceLogFloor)).toFloat()
        }
    }
    return features
}

internal fun localSpeechApplyLfr(
    input: FloatArray,
    inputDimension: Int,
    windowSize: Int,
    windowShift: Int,
): FloatArray {
    require(inputDimension > 0 && input.size % inputDimension == 0)
    require(windowSize > 0 && windowShift > 0)
    if (input.isEmpty()) return FloatArray(0)
    val inputFrames = input.size / inputDimension
    val outputFrames = 1 + (inputFrames - 1) / windowShift
    val output = FloatArray(outputFrames * inputDimension * windowSize)
    val leftContext = (windowSize - 1) / 2
    var destination = 0
    for (frame in 0 until outputFrames) {
        val centerFrame = frame * windowShift
        val leftPadding = if (centerFrame < leftContext) leftContext - centerFrame else 0
        val firstFrame = if (centerFrame < leftContext) 0 else centerFrame - leftContext
        val maximumOffset = inputFrames - 1 - firstFrame
        for (index in 0 until windowSize) {
            val sourceFrame = if (index < leftPadding) {
                0
            } else {
                val offset = index - leftPadding
                if (offset > maximumOffset) inputFrames - 1 else firstFrame + offset
            }
            input.copyInto(output, destination, sourceFrame * inputDimension, (sourceFrame + 1) * inputDimension)
            destination += inputDimension
        }
    }
    return output
}

internal fun decodeCtcLogits(
    logits: FloatArray,
    frames: Int,
    vocabularySize: Int,
    tokens: List<String>,
    blankId: Int,
): String {
    require(logits.size >= frames * vocabularySize)
    require(vocabularySize <= tokens.size)
    val decoded = StringBuilder()
    var previous = -1
    for (frame in 0 until frames) {
        var best = 0
        var bestScore = Float.NEGATIVE_INFINITY
        val offset = frame * vocabularySize
        for (token in 0 until vocabularySize) {
            val score = logits[offset + token]
            if (score > bestScore) {
                best = token
                bestScore = score
            }
        }
        if (best != blankId && best != previous) {
            val symbol = tokens[best]
            if (
                !symbol.startsWith("<|") &&
                symbol != "<blk>" &&
                symbol != "<unk>" &&
                symbol != "<s>" &&
                symbol != "</s>"
            ) {
                decoded.append(symbol.replace('\u2581', ' '))
            }
        }
        previous = best
    }
    return decoded.toString().replace(Regex(" +"), " ").trim()
}

private fun applySenseVoiceCmvn(features: FloatArray, negMean: FloatArray, invStddev: FloatArray) {
    val dimension = negMean.size
    for (index in features.indices) {
        val channel = index % dimension
        features[index] = (features[index] + negMean[channel]) * invStddev[channel]
    }
}

private fun parseSenseVoiceNumbers(value: String, expectedSize: Int): FloatArray =
    value.removePrefix("[").removeSuffix("]").split(',')
        .map { it.trim().toFloat() }
        .toFloatArray()
        .also { require(it.size == expectedSize) { "Unexpected SenseVoice normalization size: ${it.size}" } }

internal fun localSpeechMelBanks(): Array<DoubleArray> {
    val melLow = senseVoiceMel(senseVoiceLowFrequency)
    val melHigh = senseVoiceMel(senseVoiceHighFrequency)
    val delta = (melHigh - melLow) / (localSpeechMelBins + 1)
    return Array(localSpeechMelBins) { mel ->
        val left = melLow + mel * delta
        val center = melLow + (mel + 1) * delta
        val right = melLow + (mel + 2) * delta
        DoubleArray(senseVoiceFftSize / 2 + 1) { bin ->
            val value = senseVoiceMel(bin * senseVoiceFftBinWidth)
            when {
                value <= left || value >= right -> 0.0
                value <= center -> (value - left) / (center - left)
                else -> (right - value) / (right - center)
            }
        }
    }
}

private fun senseVoiceMel(frequency: Double): Double = 1127.0 * ln(1.0 + frequency / 700.0)

private fun fft(real: DoubleArray, imaginary: DoubleArray) {
    val size = real.size
    var reversed = 0
    for (index in 1 until size) {
        var bit = size shr 1
        while ((reversed and bit) != 0) {
            reversed = reversed xor bit
            bit = bit shr 1
        }
        reversed = reversed xor bit
        if (index < reversed) {
            var value = real[index]
            real[index] = real[reversed]
            real[reversed] = value
            value = imaginary[index]
            imaginary[index] = imaginary[reversed]
            imaginary[reversed] = value
        }
    }
    var length = 2
    while (length <= size) {
        val angle = -2.0 * PI / length
        val stepReal = cos(angle)
        val stepImaginary = sin(angle)
        var offset = 0
        while (offset < size) {
            var phaseReal = 1.0
            var phaseImaginary = 0.0
            for (index in 0 until length / 2) {
                val even = offset + index
                val odd = even + length / 2
                val oddReal = real[odd] * phaseReal - imaginary[odd] * phaseImaginary
                val oddImaginary = real[odd] * phaseImaginary + imaginary[odd] * phaseReal
                real[odd] = real[even] - oddReal
                imaginary[odd] = imaginary[even] - oddImaginary
                real[even] += oddReal
                imaginary[even] += oddImaginary
                val nextReal = phaseReal * stepReal - phaseImaginary * stepImaginary
                phaseImaginary = phaseReal * stepImaginary + phaseImaginary * stepReal
                phaseReal = nextReal
            }
            offset += length
        }
        length = length shl 1
    }
}

package com.vrcmc.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

internal class FunAsrNanoEngine(model: Path) : LocalSpeechEngine {
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
    private val blankId = metadata.getValue("blank_id").toInt()
    private val tokens = Files.readAllLines(
        model.resolveSibling("tokens.txt"),
        StandardCharsets.UTF_8,
    ).map { line ->
        val encoded = line.substringBeforeLast(' ')
        Base64.getDecoder().decode(encoded).toString(StandardCharsets.UTF_8)
    }
    private val melBanks = localSpeechMelBanks()

    override fun transcribe(samples: FloatArray, language: String): String {
        val fbank = localSpeechFbank(samples, melBanks)
        if (fbank.isEmpty()) return ""
        val features = localSpeechApplyLfr(fbank, localSpeechMelBins, lfrWindowSize, lfrWindowShift)
        val frames = features.size / featureDimension
        if (frames == 0) return ""
        val logits = try {
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(features),
                longArrayOf(1, frames.toLong(), featureDimension.toLong()),
            ).use { x ->
                session.run(mapOf("x" to x)).use { result ->
                    val output = result.get("logits").orElseThrow() as OnnxTensor
                    val values = output.floatBuffer
                    val shape = output.info.shape
                    val copied = FloatArray(values.remaining())
                    values.get(copied)
                    CtcLogits(copied, shape[1].toInt(), shape[2].toInt())
                }
            }
        } catch (error: Exception) {
            throw IllegalStateException("Fun-ASR-Nano inference failed", error)
        }
        return decodeCtcLogits(
            logits.values,
            logits.frames,
            logits.vocabularySize,
            tokens,
            blankId = blankId,
        )
    }

    override fun close() = session.close()
}

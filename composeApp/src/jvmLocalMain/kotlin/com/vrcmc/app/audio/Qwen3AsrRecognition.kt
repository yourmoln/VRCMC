package com.vrcmc.app

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineQwen3AsrModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.nio.file.Path

private const val qwen3AsrSampleRate = 16_000

internal class Qwen3AsrEngine(model: Path) : LocalSpeechEngine {
    private val recognizer = OfflineRecognizer(
        OfflineRecognizerConfig.builder()
            .setOfflineModelConfig(
                OfflineModelConfig.builder()
                    .setQwen3Asr(
                        OfflineQwen3AsrModelConfig.builder()
                            .setConvFrontend(model.resolveSibling("conv_frontend.onnx").toString())
                            .setEncoder(model.toString())
                            .setDecoder(model.resolveSibling("decoder.int8.onnx").toString())
                            .setTokenizer(model.resolveSibling("tokenizer").toString())
                            .build(),
                    )
                    .setNumThreads(
                        Runtime.getRuntime().availableProcessors().let { (it / 2).coerceIn(1, 6) },
                    )
                    .setDebug(false)
                    .build(),
            )
            .setDecodingMethod("greedy_search")
            .build(),
    )

    @Suppress("UNUSED_PARAMETER")
    override fun transcribe(samples: FloatArray, language: String): String {
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, qwen3AsrSampleRate)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } catch (error: Exception) {
            throw IllegalStateException("Qwen3-ASR inference failed", error)
        } finally {
            stream.release()
        }
    }

    override fun close() = recognizer.release()
}

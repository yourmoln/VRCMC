package com.vrcmc.app

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import java.nio.file.Path

private const val senseVoiceSampleRate = 16_000

internal class SenseVoiceAndroidEngine(model: Path) : LocalSpeechEngine {
    private val recognizer = OfflineRecognizer(
        config =
            OfflineRecognizerConfig(
                modelConfig =
                    OfflineModelConfig(
                        senseVoice =
                            OfflineSenseVoiceModelConfig(
                                model = model.toString(),
                                language = "auto",
                                useInverseTextNormalization = true,
                            ),
                        tokens = model.resolveSibling("tokens.txt").toString(),
                        numThreads =
                            Runtime.getRuntime().availableProcessors().let {
                                (it / 2).coerceIn(1, 6)
                            },
                        debug = false,
                    ),
                decodingMethod = "greedy_search",
            ),
    )

    @Suppress("UNUSED_PARAMETER")
    override fun transcribe(samples: FloatArray, language: String): String {
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, senseVoiceSampleRate)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } catch (error: Exception) {
            throw IllegalStateException("SenseVoice inference failed", error)
        } finally {
            stream.release()
        }
    }

    override fun close() = recognizer.release()
}

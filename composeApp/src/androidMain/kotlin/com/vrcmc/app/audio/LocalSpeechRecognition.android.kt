package com.vrcmc.app

import android.os.Build
import java.nio.file.Path

internal actual val localSpeechRecognizer: LocalSpeechRecognizer by lazy {
    val context = audioApplicationContext()
    if (context == null || !androidLocalSpeechSupported()) {
        UnsupportedLocalSpeechRecognizer
    } else {
        val model = LocalSpeechModel.QWEN3_ASR_0_6B_INT8
        JvmLocalSpeechRecognizer(
            stores = mapOf(
                model to model.createStore(
                    context.filesDir
                        .resolve("models")
                        .resolve("qwen3-asr-0.6b")
                        .resolve(model.modelFileName())
                        .toPath(),
                ),
            ),
            supported = true,
            createEngine = ::createAndroidLocalSpeechEngine,
        )
    }
}

private fun createAndroidLocalSpeechEngine(
    model: LocalSpeechModel,
    path: Path,
): LocalSpeechEngine =
    when (model) {
        LocalSpeechModel.QWEN3_ASR_0_6B_INT8 -> Qwen3AsrEngine(path)
        LocalSpeechModel.SENSE_VOICE_SMALL_INT8,
        LocalSpeechModel.FUN_ASR_NANO_2512_INT8,
        -> error("Unsupported Android local speech model: ${model.serializedName}")
    }

internal fun androidLocalSpeechSupported(): Boolean =
    Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", ignoreCase = true) }

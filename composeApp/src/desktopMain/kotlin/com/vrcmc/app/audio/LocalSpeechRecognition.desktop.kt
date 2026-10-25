package com.vrcmc.app

import com.sun.jna.Platform
import java.nio.file.Path

internal actual val localSpeechRecognizer: LocalSpeechRecognizer by lazy {
    JvmLocalSpeechRecognizer(
        stores = LocalSpeechModel.entries.associateWith { model ->
            model.createStore(desktopLocalSpeechModelPath(model))
        },
        supported = localSpeechSupported(),
    )
}

private fun desktopLocalSpeechModelPath(model: LocalSpeechModel): Path {
    val property = when (model) {
        LocalSpeechModel.SENSE_VOICE_SMALL_INT8 -> "vrcmc.senseVoiceModel.path"
        LocalSpeechModel.FUN_ASR_NANO_2512_INT8 -> "vrcmc.funAsrNanoModel.path"
    }
    System.getProperty(property)?.takeIf(String::isNotBlank)?.let { return Path.of(it) }
    val root = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)?.let { Path.of(it, "VRCMC") }
        ?: Path.of(System.getProperty("user.home"), ".vrcmc")
    val directory = when (model) {
        LocalSpeechModel.SENSE_VOICE_SMALL_INT8 -> "sense-voice-small"
        LocalSpeechModel.FUN_ASR_NANO_2512_INT8 -> "fun-asr-nano-2512"
    }
    return root.resolve("models").resolve(directory).resolve(model.modelFileName())
}

internal fun localSpeechSupported(): Boolean =
    Platform.isWindows() && Platform.is64Bit() && !Platform.isARM()

package com.vrcmc.app

import com.sun.jna.platform.win32.Crypt32Util
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.prefs.Preferences

private val prefs = Preferences.userNodeForPackage(DeviceStorage::class.java)
private object DeviceStorage
private val storageDirectory: Path = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let { Path.of(it, "VRCMC") }
    ?: Path.of(System.getProperty("user.home"), ".vrcmc")
private val translationFile = storageDirectory.resolve("translation-settings.json")
private val chatHistoryFile = storageDirectory.resolve("chat-history.json")
private val translationSecretsFile = storageDirectory.resolve("translation-secrets.dpapi")
private val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

actual fun loadStoredDevices(): List<Device> = prefs.get("devices", "").split(';').mapNotNull { value ->
    val parts = value.split('|')
    when (parts.firstOrNull()) {
        "v3" -> parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { Device(it, parts.getOrNull(2)?.toIntOrNull() ?: 9000, parts.getOrNull(3)?.toIntOrNull() ?: 9001) }
        "v2" -> parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { Device(it, parts.getOrNull(2)?.toIntOrNull() ?: 9000) }
        else -> parts.firstOrNull()?.takeIf { it.isNotBlank() }?.let { Device(it) }
    }
}

actual fun saveStoredDevices(devices: List<Device>, activeAddress: String) {
    prefs.put("devices", devices.joinToString(";") { "v3|${it.address}|${it.receivePort}|${it.sendPort}" }); prefs.put("active", activeAddress)
}

actual fun loadStoredActiveAddress(): String = prefs.get("active", "")
actual fun loadStoredTranslationSettings(): String = readText(translationFile).ifBlank { prefs.get("translationSettings", "") }
actual fun saveStoredTranslationSettings(value: String) { writeTextAtomically(translationFile, value); prefs.remove("translationSettings") }
actual fun loadStoredTranslationSecrets(): String {
    if (!isWindows) return ""
    return runCatching {
        val encrypted = Files.readAllBytes(translationSecretsFile)
        Crypt32Util.cryptUnprotectData(encrypted).toString(StandardCharsets.UTF_8)
    }.getOrDefault("")
}
actual fun saveStoredTranslationSecrets(value: String) {
    if (!isWindows || value.isBlank() || value == "{}") {
        runCatching { Files.deleteIfExists(translationSecretsFile) }
        return
    }
    val encrypted = Crypt32Util.cryptProtectData(value.toByteArray(StandardCharsets.UTF_8))
    writeBytesAtomically(translationSecretsFile, encrypted)
}
actual fun loadStoredChatHistory(): String = readText(chatHistoryFile).ifBlank { prefs.get("chatHistory", "") }
actual fun saveStoredChatHistory(value: String) { writeTextAtomically(chatHistoryFile, value); prefs.remove("chatHistory") }
actual fun loadStoredSimultaneousInterpretationEnabled(): Boolean = prefs.getBoolean("simultaneousInterpretation", false)
actual fun saveStoredSimultaneousInterpretationEnabled(value: Boolean) { prefs.putBoolean("simultaneousInterpretation", value) }
actual fun loadStoredAlwaysInterpretationEnabled(): Boolean = prefs.getBoolean("alwaysInterpretation", false)
actual fun saveStoredAlwaysInterpretationEnabled(value: Boolean) { prefs.putBoolean("alwaysInterpretation", value) }
actual fun loadStoredAlwaysInterpretationDelayMillis(): Int = prefs.getInt("alwaysInterpretationDelayMillis", 2_000)
actual fun saveStoredAlwaysInterpretationDelayMillis(value: Int) { prefs.putInt("alwaysInterpretationDelayMillis", value) }

private fun readText(path: Path): String = runCatching { Files.readString(path, StandardCharsets.UTF_8) }.getOrDefault("")

private fun writeTextAtomically(path: Path, value: String) = writeBytesAtomically(path, value.toByteArray(StandardCharsets.UTF_8))

private fun writeBytesAtomically(path: Path, value: ByteArray) {
    Files.createDirectories(path.parent)
    val temporary = Files.createTempFile(path.parent, path.fileName.toString(), ".tmp")
    try {
        Files.write(temporary, value)
        runCatching {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
}

package com.vrcmc.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private var appContext: Context? = null
private var audioContext: Context? = null

internal fun initDeviceStorage(context: Context) {
    appContext = context.applicationContext
    audioContext = context
}

internal fun audioApplicationContext(): Context? = audioContext

internal actual fun platformJapaneseDictionaryCachePath(): String? =
    appContext
        ?.filesDir
        ?.resolve("romaji")
        ?.resolve(japaneseDictionaryArchiveName)
        ?.absolutePath

private fun prefs() = appContext?.getSharedPreferences("vrcmc", Context.MODE_PRIVATE)

actual fun loadStoredDevices(): List<Device> =
    prefs()?.getString("devices", "")?.split(';')?.mapNotNull { value ->
        val parts = value.split('|')
        when (parts.firstOrNull()) {
            "v3" ->
                parts
                    .getOrNull(1)
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        Device(
                            it,
                            parts.getOrNull(2)?.toIntOrNull() ?: 9000,
                            parts.getOrNull(3)?.toIntOrNull() ?: 9001,
                        )
                    }
            "v2" ->
                parts
                    .getOrNull(1)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { Device(it, parts.getOrNull(2)?.toIntOrNull() ?: 9000) }
            else -> parts.firstOrNull()?.takeIf { it.isNotBlank() }?.let { Device(it) }
        }
    } ?: emptyList()

actual fun saveStoredDevices(devices: List<Device>, activeAddress: String) {
    prefs()?.edit {
        putString(
            "devices",
            devices.joinToString(";") { "v3|${it.address}|${it.receivePort}|${it.sendPort}" },
        )
        putString("active", activeAddress)
    }
}

actual fun loadStoredActiveAddress(): String = prefs()?.getString("active", "") ?: ""

actual fun loadStoredTranslationSettings(): String =
    prefs()?.getString("translationSettings", "") ?: ""

actual fun saveStoredTranslationSettings(value: String) {
    prefs()?.edit { putString("translationSettings", value) }
}

actual fun loadStoredTranslationSecrets(): String =
    prefs()?.getString("translationSecrets", null)?.let(::decryptSecrets).orEmpty()

actual fun saveStoredTranslationSecrets(value: String) {
    val preferences = prefs() ?: return
    preferences.edit {
        if (value.isBlank() || value == "{}") remove("translationSecrets")
        else putString("translationSecrets", encryptSecrets(value))
    }
}

actual fun loadStoredChatHistory(): String = prefs()?.getString("chatHistory", "") ?: ""

actual fun saveStoredChatHistory(value: String) {
    prefs()?.edit { putString("chatHistory", value) }
}

actual fun loadStoredErrorLogs(): String = prefs()?.getString("errorLogs", "") ?: ""

actual fun saveStoredErrorLogs(value: String) {
    prefs()?.edit { putString("errorLogs", value) }
}

actual fun loadIgnoredUpdateVersion(): String = prefs()?.getString("ignoredUpdateVersion", "") ?: ""

actual fun saveIgnoredUpdateVersion(value: String) {
    prefs()?.edit { putString("ignoredUpdateVersion", value) }
}

actual fun loadStoredSimultaneousInterpretationEnabled(): Boolean =
    prefs()?.getBoolean("simultaneousInterpretation", false) ?: false

actual fun saveStoredSimultaneousInterpretationEnabled(value: Boolean) {
    prefs()?.edit { putBoolean("simultaneousInterpretation", value) }
}

actual fun loadStoredSimultaneousInterpretationSendDelayMillis(): Int =
    prefs()?.getInt("simultaneousInterpretationSendDelayMillis", 2_000) ?: 2_000

actual fun saveStoredSimultaneousInterpretationSendDelayMillis(value: Int) {
    prefs()?.edit { putInt("simultaneousInterpretationSendDelayMillis", value) }
}

actual fun loadStoredAlwaysInterpretationEnabled(): Boolean =
    prefs()?.getBoolean("alwaysInterpretation", false) ?: false

actual fun saveStoredAlwaysInterpretationEnabled(value: Boolean) {
    prefs()?.edit { putBoolean("alwaysInterpretation", value) }
}

actual fun loadStoredAlwaysInterpretationDelayMillis(): Int =
    prefs()?.getInt("alwaysInterpretationDelayMillis", 2_000) ?: 2_000

actual fun saveStoredAlwaysInterpretationDelayMillis(value: Int) {
    prefs()?.edit { putInt("alwaysInterpretationDelayMillis", value) }
}

actual fun loadStoredInterpretationKeepScreenOn(): Boolean =
    prefs()?.getBoolean("interpretationKeepScreenOn", true) ?: true

actual fun saveStoredInterpretationKeepScreenOn(value: Boolean) {
    prefs()?.edit { putBoolean("interpretationKeepScreenOn", value) }
}

private const val secretKeyAlias = "vrcmc.translation.secrets"

private fun getOrCreateSecretKey(): SecretKey {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (keyStore.getKey(secretKeyAlias, null) as? SecretKey)?.let {
        return it
    }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        .apply {
            init(
                KeyGenParameterSpec.Builder(
                        secretKeyAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }
        .generateKey()
}

private fun encryptSecrets(value: String): String {
    val cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        }
    val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    val payload = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + encrypted
    return Base64.encodeToString(payload, Base64.NO_WRAP)
}

private fun decryptSecrets(value: String): String =
    runCatching {
            val payload = Base64.decode(value, Base64.NO_WRAP)
            val ivSize = payload.first().toInt() and 0xff
            require(ivSize in 12..16 && payload.size > ivSize + 1)
            val iv = payload.copyOfRange(1, ivSize + 1)
            val encrypted = payload.copyOfRange(ivSize + 1, payload.size)
            val cipher =
                Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
                }
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }
        .getOrDefault("")

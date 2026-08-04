package com.vrcmc.app

import java.util.prefs.Preferences

private val prefs = Preferences.userNodeForPackage(DeviceStorage::class.java)
private object DeviceStorage

actual fun loadStoredDevices(): List<Device> = prefs.get("devices", "").split(';').mapNotNull { value ->
    val parts = value.split('|')
    if (parts.firstOrNull() == "v2") parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { Device(it, parts.getOrNull(2)?.toIntOrNull() ?: 9000) }
    else parts.firstOrNull()?.takeIf { it.isNotBlank() }?.let { Device(it, 9000) }
}

actual fun saveStoredDevices(devices: List<Device>, activeAddress: String) {
    prefs.put("devices", devices.joinToString(";") { "v2|${it.address}|${it.port}" }); prefs.put("active", activeAddress)
}

actual fun loadStoredActiveAddress(): String = prefs.get("active", "")
actual fun loadStoredTranslationSettings(): String = prefs.get("translationSettings", "")
actual fun saveStoredTranslationSettings(value: String) = prefs.put("translationSettings", value)
actual fun loadStoredChatHistory(): String = prefs.get("chatHistory", "")
actual fun saveStoredChatHistory(value: String) = prefs.put("chatHistory", value)

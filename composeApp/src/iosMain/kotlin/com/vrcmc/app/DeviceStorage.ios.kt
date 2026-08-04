package com.vrcmc.app

import platform.Foundation.NSUserDefaults

private val defaults get() = NSUserDefaults.standardUserDefaults
actual fun loadStoredDevices(): List<Device> = (defaults.stringForKey("devices") ?: "").split(';').mapNotNull { value ->
    val parts = value.split('|')
    if (parts.firstOrNull() == "v2") parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { Device(it, parts.getOrNull(2)?.toIntOrNull() ?: 9000) }
    else parts.firstOrNull()?.takeIf { it.isNotBlank() }?.let { Device(it, 9000) }
}
actual fun saveStoredDevices(devices: List<Device>, activeAddress: String) { defaults.setObject(devices.joinToString(";") { "v2|${it.address}|${it.port}" }, forKey = "devices"); defaults.setObject(activeAddress, forKey = "active") }
actual fun loadStoredActiveAddress(): String = defaults.stringForKey("active") ?: ""
actual fun loadStoredTranslationSettings(): String = defaults.stringForKey("translationSettings") ?: ""
actual fun saveStoredTranslationSettings(value: String) { defaults.setObject(value, forKey = "translationSettings") }
actual fun loadStoredChatHistory(): String = defaults.stringForKey("chatHistory") ?: ""
actual fun saveStoredChatHistory(value: String) { defaults.setObject(value, forKey = "chatHistory") }

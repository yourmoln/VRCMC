package com.vrcmc.app

import android.content.Context

private var appContext: Context? = null
internal fun initDeviceStorage(context: Context) { appContext = context.applicationContext }
private fun prefs() = appContext?.getSharedPreferences("vrcmc", Context.MODE_PRIVATE)

actual fun loadStoredDevices(): List<Device> = prefs()?.getString("devices", "")?.split(';')?.mapNotNull { value ->
    val parts = value.split('|')
    if (parts.firstOrNull() == "v2") parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { Device(it, parts.getOrNull(2)?.toIntOrNull() ?: 9000) }
    else parts.firstOrNull()?.takeIf { it.isNotBlank() }?.let { Device(it, 9000) }
} ?: emptyList()

actual fun saveStoredDevices(devices: List<Device>, activeAddress: String) { prefs()?.edit()?.putString("devices", devices.joinToString(";") { "v2|${it.address}|${it.port}" })?.putString("active", activeAddress)?.apply() }
actual fun loadStoredActiveAddress(): String = prefs()?.getString("active", "") ?: ""
actual fun loadStoredTranslationSettings(): String = prefs()?.getString("translationSettings", "") ?: ""
actual fun saveStoredTranslationSettings(value: String) { prefs()?.edit()?.putString("translationSettings", value)?.apply() }
actual fun loadStoredChatHistory(): String = prefs()?.getString("chatHistory", "") ?: ""
actual fun saveStoredChatHistory(value: String) { prefs()?.edit()?.putString("chatHistory", value)?.apply() }

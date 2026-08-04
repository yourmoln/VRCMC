package com.vrcmc.app

expect fun loadStoredDevices(): List<Device>
expect fun saveStoredDevices(devices: List<Device>, activeAddress: String)
expect fun loadStoredActiveAddress(): String
expect fun loadStoredTranslationSettings(): String
expect fun saveStoredTranslationSettings(value: String)
expect fun loadStoredTranslationSecrets(): String
expect fun saveStoredTranslationSecrets(value: String)
expect fun loadStoredChatHistory(): String
expect fun saveStoredChatHistory(value: String)

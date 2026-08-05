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
expect fun loadStoredSimultaneousInterpretationEnabled(): Boolean
expect fun saveStoredSimultaneousInterpretationEnabled(value: Boolean)
expect fun loadStoredAlwaysInterpretationEnabled(): Boolean
expect fun saveStoredAlwaysInterpretationEnabled(value: Boolean)
expect fun loadStoredAlwaysInterpretationDelayMillis(): Int
expect fun saveStoredAlwaysInterpretationDelayMillis(value: Int)
expect fun loadStoredErrorLogs(): String
expect fun saveStoredErrorLogs(value: String)

package com.vrcmc.app

import androidx.compose.runtime.*

class AppState {
    private val storedTranslation =
        storedTranslationSettingsFromJson(loadStoredTranslationSettings())
    private val storedSecrets = storedProviderSecretsFromJson(loadStoredTranslationSecrets())
    private val storedVoiceInput =
        storedTranslation.voiceInput.copy(apiKey = storedSecrets["qwen3_asr"]?.apiKey.orEmpty())
    val devices = mutableStateListOf<Device>().apply {
        addAll(loadStoredDevices())
        if (isDesktopAudioPlatform() && isEmpty()) add(Device(address = "127.0.0.1"))
    }
    val messages =
        mutableStateListOf<ChatMessage>().apply {
            addAll(chatHistoryFromJson(loadStoredChatHistory()))
        }
    val errorLogs =
        mutableStateListOf<ErrorLog>().apply { addAll(errorLogsFromJson(loadStoredErrorLogs())) }
    var chatDraft by mutableStateOf("")
    var voiceInputConfig by mutableStateOf(storedVoiceInput)
        private set
    var interpretationVoiceInputEnabled by mutableStateOf(
        storedTranslation.interpretationVoiceInputEnabled &&
            storedVoiceInput.enabled && storedVoiceInput.apiKey.isNotBlank()
    )
        private set
    var simultaneousInterpretationEnabled by
        mutableStateOf(loadStoredSimultaneousInterpretationEnabled())
        private set

    var isSimultaneousInterpretationActive by mutableStateOf(false)
        private set

    var simultaneousFinalPending by mutableStateOf(false)
        private set

    var simultaneousListenerError by mutableStateOf<String?>(null)
    var alwaysInterpretationEnabled by
        mutableStateOf(
            loadStoredAlwaysInterpretationEnabled() && !simultaneousInterpretationEnabled
        )
        private set

    var isAlwaysInterpretationActive by mutableStateOf(false)
        private set

    var alwaysInterpretationDelayMillis by
        mutableIntStateOf(
            loadStoredAlwaysInterpretationDelayMillis().takeIf { it in 500..10_000 } ?: 2_000
        )
        private set
    var interpretationKeepScreenOn by mutableStateOf(loadStoredInterpretationKeepScreenOn())
        private set

    fun updateInterpretationKeepScreenOn(enabled: Boolean) {
        interpretationKeepScreenOn = enabled
        saveStoredInterpretationKeepScreenOn(enabled)
    }

    var activeAddress by
        mutableStateOf(
            loadStoredActiveAddress().takeIf { saved -> devices.any { it.address == saved } }
                ?: devices.firstOrNull()?.address.orEmpty()
        )
    var providerId by
        mutableStateOf(
            storedTranslation.providerId.takeIf { id -> translationProviders.any { it.id == id } }
                ?: "deepseek"
        )
    var translate by mutableStateOf(storedTranslation.translate)
    var disableDynamicInputLimit by mutableStateOf(storedTranslation.disableDynamicInputLimit)
    var sendOriginalBeforeTranslation by
        mutableStateOf(storedTranslation.sendOriginalBeforeTranslation)
        private set

    val languages =
        mutableStateListOf<String>().apply {
            addAll(storedTranslation.targetLanguages.take(2).ifEmpty { listOf("English") })
        }
    val outputOrder =
        mutableStateListOf<String>().apply {
            addAll(normalizeOutputOrder(languages, storedTranslation.outputOrder))
        }
    var lineBreakOutput by mutableStateOf(storedTranslation.lineBreakOutput)
        private set
    val providerConfigs = initialProviderConfigs(storedTranslation.configs, storedSecrets)
    val provider
        get() = providerById(providerId)

    val providerConfig
        get() = providerConfigs[providerId] ?: defaultProviderConfig(provider)

    val isTranslationApiConfigured
        get() = provider.isConfigured(providerConfig)

    fun updateTranslationEnabled(enabled: Boolean) {
        translate = enabled
        persistTranslation()
    }

    fun updateProviderConfig(transform: (ProviderConfig) -> ProviderConfig) {
        providerConfigs[providerId] = transform(providerConfig)
        persistTranslation()
    }

    fun updateVoiceInputConfig(transform: (VoiceInputConfig) -> VoiceInputConfig) {
        voiceInputConfig = transform(voiceInputConfig)
        if (!voiceInputConfig.enabled || voiceInputConfig.apiKey.isBlank()) {
            interpretationVoiceInputEnabled = false
        }
        persistTranslation()
    }

    fun selectProvider(id: String) {
        providerId = id
        persistTranslation()
    }

    fun configureQwenServices(apiKey: String, regionId: String) {
        val provider = providerById("qianwen")
        val region = provider.regions.firstOrNull { it.id == regionId } ?: provider.regions.first()
        providerId = provider.id
        providerConfigs[provider.id] =
            defaultProviderConfig(provider).copy(
                apiKey = apiKey.trim(),
                baseUrl = region.baseUrl,
                model = "qwen-mt-plus",
                region = region.id,
            )
        translate = true
        voiceInputConfig =
            voiceInputConfig.copy(
                enabled = true,
                apiKey = apiKey.trim(),
                region = if (region.id == "china") "china_mainland" else region.id,
                baseUrl = region.baseUrl,
            )
        persistTranslation()
    }

    fun setLanguages(values: List<String>) {
        languages.clear()
        languages.addAll(
            values.filter { it.isNotBlank() }.distinct().take(2).ifEmpty { listOf("English") }
        )
        setOutputOrder(outputOrder.toList())
    }

    fun setOutputOrder(values: List<String>) {
        outputOrder.clear()
        outputOrder.addAll(normalizeOutputOrder(languages, values))
        persistTranslation()
    }

    fun updateLineBreakOutput(enabled: Boolean) {
        lineBreakOutput = enabled
        persistTranslation()
    }

    fun updateDisableDynamicInputLimit(enabled: Boolean) {
        disableDynamicInputLimit = enabled
        persistTranslation()
    }

    fun updateSendOriginalBeforeTranslation(enabled: Boolean) {
        sendOriginalBeforeTranslation = enabled
        persistTranslation()
    }

    init {
        if (
            storedTranslation.configs.values.any {
                it.apiKey.isNotBlank() || it.customHeaders.isNotBlank()
            }
        )
            persistTranslation()
        if (simultaneousInterpretationEnabled && loadStoredAlwaysInterpretationEnabled()) {
            saveStoredAlwaysInterpretationEnabled(false)
        }
    }

    fun persistTranslation() {
        saveStoredTranslationSecrets(
            (providerConfigs
                .mapValues { (_, value) -> ProviderSecrets(value.apiKey, value.customHeaders) }
                + ("qwen3_asr" to ProviderSecrets(voiceInputConfig.apiKey)))
                .toSecretsJson()
        )
        saveStoredTranslationSettings(
            StoredTranslationSettings(
                    providerId = providerId,
                    translate = translate,
                    sendOriginalBeforeTranslation = sendOriginalBeforeTranslation,
                    targetLanguages = languages.toList(),
                    outputOrder = outputOrder.toList(),
                    lineBreakOutput = lineBreakOutput,
                    configs = providerConfigs.toMap(),
                    voiceInput = voiceInputConfig.copy(apiKey = ""),
                    interpretationVoiceInputEnabled = interpretationVoiceInputEnabled,
                    disableDynamicInputLimit = disableDynamicInputLimit,
                )
                .toJson()
        )
    }

    fun updateInterpretationVoiceInputEnabled(enabled: Boolean) {
        interpretationVoiceInputEnabled =
            enabled && voiceInputConfig.enabled && voiceInputConfig.apiKey.isNotBlank()
        persistTranslation()
    }

    fun persist() = saveStoredDevices(devices.toList(), activeAddress)

    fun updateSimultaneousInterpretationEnabled(enabled: Boolean) {
        if (enabled && alwaysInterpretationEnabled) {
            alwaysInterpretationEnabled = false
            isAlwaysInterpretationActive = false
            saveStoredAlwaysInterpretationEnabled(false)
        }
        simultaneousInterpretationEnabled = enabled
        if (!enabled) {
            isSimultaneousInterpretationActive = false
            simultaneousFinalPending = false
        }
        simultaneousListenerError = null
        saveStoredSimultaneousInterpretationEnabled(enabled)
    }

    fun updateAlwaysInterpretationEnabled(enabled: Boolean) {
        if (enabled && simultaneousInterpretationEnabled) {
            simultaneousInterpretationEnabled = false
            isSimultaneousInterpretationActive = false
            simultaneousFinalPending = false
            simultaneousListenerError = null
            saveStoredSimultaneousInterpretationEnabled(false)
        }
        alwaysInterpretationEnabled = enabled
        if (!enabled) isAlwaysInterpretationActive = false
        saveStoredAlwaysInterpretationEnabled(enabled)
    }

    fun toggleAlwaysInterpretationActive() {
        if (alwaysInterpretationEnabled) {
            isAlwaysInterpretationActive = !isAlwaysInterpretationActive
        }
    }

    fun updateAlwaysInterpretationDelayMillis(value: Int) {
        alwaysInterpretationDelayMillis = value.coerceIn(500, 10_000)
        saveStoredAlwaysInterpretationDelayMillis(alwaysInterpretationDelayMillis)
    }

    fun handleVrchatMuteSelf(muted: Boolean) {
        if (!simultaneousInterpretationEnabled) return
        if (!muted && !isSimultaneousInterpretationActive) {
            chatDraft = ""
            isSimultaneousInterpretationActive = true
        } else if (muted && isSimultaneousInterpretationActive) {
            isSimultaneousInterpretationActive = false
            simultaneousFinalPending = true
        }
    }

    fun finishSimultaneousInterpretation() {
        isSimultaneousInterpretationActive = false
    }

    fun consumeSimultaneousFinalRequest() {
        simultaneousFinalPending = false
    }

    fun activeDevice() =
        devices.firstOrNull { it.address == activeAddress } ?: devices.firstOrNull()

    fun addDevice(value: String): Boolean {
        val device = parseDeviceEndpoint(value) ?: return false
        val existing = devices.indexOfFirst { it.address == device.address }
        if (existing >= 0) devices[existing] = device else devices += device
        activeAddress = device.address
        persist()
        return true
    }

    fun removeDevice(device: Device) {
        devices.remove(device)
        if (activeAddress == device.address)
            activeAddress = devices.firstOrNull()?.address.orEmpty()
        persist()
    }

    fun updateDevice(
        device: Device,
        address: String,
        receivePort: String,
        sendPort: String,
    ): Boolean {
        val updatedAddress = address.trim()
        val updatedReceivePort = receivePort.toIntOrNull()
        val updatedSendPort = sendPort.toIntOrNull()
        if (
            updatedAddress.isBlank() ||
                updatedAddress.any(Char::isWhitespace) ||
                updatedReceivePort == null ||
                updatedReceivePort !in 1..65535 ||
                updatedSendPort == null ||
                updatedSendPort !in 1..65535
        )
            return false
        val index = devices.indexOf(device)
        if (index < 0 || devices.any { it != device && it.address == updatedAddress }) return false
        devices[index] = Device(updatedAddress, updatedReceivePort, updatedSendPort)
        if (activeAddress == device.address) activeAddress = updatedAddress
        persist()
        return true
    }

    fun addMessage(message: ChatMessage): Int {
        messages += message
        while (messages.size > maxSavedChatMessages) messages.removeAt(0)
        persistChatHistory()
        return messages.lastIndex
    }

    fun removeMessageAt(index: Int) {
        if (index !in messages.indices) return
        messages.removeAt(index)
        persistChatHistory()
    }

    fun replaceMessage(index: Int, message: ChatMessage) {
        if (index !in messages.indices) return
        messages[index] = message
        persistChatHistory()
    }

    fun clearChatHistory() {
        messages.clear()
        persistChatHistory()
    }

    fun addErrorLog(message: String) {
        val clean = message.trim().takeIf { it.isNotBlank() } ?: return
        errorLogs += ErrorLog(currentTimeMillis(), clean)
        while (errorLogs.size > maxErrorLogs) errorLogs.removeAt(0)
        saveStoredErrorLogs(errorLogs.toErrorLogsJson())
    }

    fun clearErrorLogs() {
        errorLogs.clear()
        saveStoredErrorLogs("[]")
    }

    private fun persistChatHistory() = saveStoredChatHistory(messages.toChatHistoryJson())
}

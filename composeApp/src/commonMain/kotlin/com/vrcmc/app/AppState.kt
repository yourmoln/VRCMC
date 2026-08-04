package com.vrcmc.app

import androidx.compose.runtime.*

data class Device(
    val address: String,
    val receivePort: Int = 9000,
    val sendPort: Int = 9001,
)

fun Device.displayEndpoint(): String = "$receivePort:${if (':' in address) "[$address]" else address}:$sendPort"
enum class MessageRole { USER, ASSISTANT }
data class ChatMessage(
    val text: String,
    val role: MessageRole,
    val timestamp: Long = currentTimeMillis(),
    val isLoading: Boolean = false,
    val language: String? = null,
)

class AppState {
    private val storedTranslation = storedTranslationSettingsFromJson(loadStoredTranslationSettings())
    private val storedSecrets = storedProviderSecretsFromJson(loadStoredTranslationSecrets())
    val devices = mutableStateListOf<Device>().apply { addAll(loadStoredDevices()) }
    val messages = mutableStateListOf<ChatMessage>().apply { addAll(chatHistoryFromJson(loadStoredChatHistory())) }
    var chatDraft by mutableStateOf("")
    var simultaneousInterpretationEnabled by mutableStateOf(loadStoredSimultaneousInterpretationEnabled())
        private set
    var isSimultaneousInterpretationActive by mutableStateOf(false)
        private set
    var simultaneousFinalPending by mutableStateOf(false)
        private set
    var simultaneousListenerError by mutableStateOf<String?>(null)
    var activeAddress by mutableStateOf(loadStoredActiveAddress().takeIf { saved -> devices.any { it.address == saved } } ?: devices.firstOrNull()?.address.orEmpty())
    var providerId by mutableStateOf(storedTranslation.providerId.takeIf { id -> translationProviders.any { it.id == id } } ?: "deepseek")
    var translate by mutableStateOf(storedTranslation.translate)
    val languages = mutableStateListOf<String>().apply { addAll(storedTranslation.targetLanguages.take(2).ifEmpty { listOf("English") }) }
    val outputOrder = mutableStateListOf<String>().apply { addAll(normalizeOutputOrder(languages, storedTranslation.outputOrder)) }
    val providerConfigs = initialProviderConfigs(storedTranslation.configs, storedSecrets)
    val provider get() = providerById(providerId)
    val providerConfig get() = providerConfigs[providerId] ?: defaultProviderConfig(provider)
    fun updateProviderConfig(transform: (ProviderConfig) -> ProviderConfig) { providerConfigs[providerId] = transform(providerConfig); persistTranslation() }
    fun selectProvider(id: String) { providerId = id; persistTranslation() }
    fun setLanguages(values: List<String>) {
        languages.clear()
        languages.addAll(values.filter { it.isNotBlank() }.distinct().take(2).ifEmpty { listOf("English") })
        setOutputOrder(outputOrder.toList())
    }
    fun setOutputOrder(values: List<String>) {
        outputOrder.clear()
        outputOrder.addAll(normalizeOutputOrder(languages, values))
        persistTranslation()
    }
    init {
        if (storedTranslation.configs.values.any { it.apiKey.isNotBlank() || it.customHeaders.isNotBlank() }) persistTranslation()
    }

    fun persistTranslation() {
        saveStoredTranslationSecrets(providerConfigs.mapValues { (_, value) -> ProviderSecrets(value.apiKey, value.customHeaders) }.toSecretsJson())
        saveStoredTranslationSettings(StoredTranslationSettings(providerId = providerId, translate = translate, targetLanguages = languages.toList(), outputOrder = outputOrder.toList(), configs = providerConfigs.toMap()).toJson())
    }
    fun persist() = saveStoredDevices(devices.toList(), activeAddress)
    fun updateSimultaneousInterpretationEnabled(enabled: Boolean) {
        simultaneousInterpretationEnabled = enabled
        if (!enabled) {
            isSimultaneousInterpretationActive = false
            simultaneousFinalPending = false
        }
        simultaneousListenerError = null
        saveStoredSimultaneousInterpretationEnabled(enabled)
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
    fun activeDevice() = devices.firstOrNull { it.address == activeAddress } ?: devices.firstOrNull()
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
        if (activeAddress == device.address) activeAddress = devices.firstOrNull()?.address.orEmpty()
        persist()
    }
    fun updateDevice(device: Device, address: String, receivePort: String, sendPort: String): Boolean {
        val updatedAddress = address.trim()
        val updatedReceivePort = receivePort.toIntOrNull()
        val updatedSendPort = sendPort.toIntOrNull()
        if (
            updatedAddress.isBlank() || updatedAddress.any(Char::isWhitespace) ||
            updatedReceivePort == null || updatedReceivePort !in 1..65535 ||
            updatedSendPort == null || updatedSendPort !in 1..65535
        ) return false
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
    private fun persistChatHistory() = saveStoredChatHistory(messages.toChatHistoryJson())
}

internal fun parseDeviceEndpoint(value: String): Device? {
    val endpoint = value.trim()
    if (endpoint.isBlank() || endpoint.any(Char::isWhitespace)) return null

    val bracketedTriple = Regex("^(\\d+):\\[([^]]+)]:(\\d+)$").matchEntire(endpoint)
    if (bracketedTriple != null) {
        val (receivePort, address, sendPort) = bracketedTriple.destructured
        return deviceOrNull(address, receivePort, sendPort)
    }

    if (endpoint.startsWith("[")) {
        val closingBracket = endpoint.indexOf(']')
        if (closingBracket <= 1) return null
        val address = endpoint.substring(1, closingBracket)
        val portPart = endpoint.substring(closingBracket + 1)
        val receivePort = when {
            portPart.isEmpty() -> return Device(address)
            portPart.startsWith(":") -> portPart.drop(1).toIntOrNull()
            else -> null
        } ?: return null
        return Device(address, receivePort).takeIf { receivePort in 1..65535 }
    }

    return when (endpoint.count { it == ':' }) {
        0 -> Device(endpoint)
        1 -> {
            val address = endpoint.substringBeforeLast(':')
            val receivePort = endpoint.substringAfterLast(':')
            deviceOrNull(address, receivePort, "9001")
        }
        2 -> {
            val parts = endpoint.split(':')
            deviceOrNull(parts[1], parts[0], parts[2])
        }
        else -> Device(endpoint)
    }
}

private fun deviceOrNull(address: String, receivePort: String, sendPort: String): Device? {
    val parsedReceivePort = receivePort.toIntOrNull()
    val parsedSendPort = sendPort.toIntOrNull()
    return Device(address, parsedReceivePort ?: return null, parsedSendPort ?: return null).takeIf {
        address.isNotBlank() && parsedReceivePort in 1..65535 && parsedSendPort in 1..65535
    }
}

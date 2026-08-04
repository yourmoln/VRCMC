package com.vrcmc.app

import androidx.compose.runtime.*

data class Device(val address: String, val port: Int = 9000)
enum class MessageRole { USER, ASSISTANT }
data class ChatMessage(
    val text: String,
    val role: MessageRole,
    val timestamp: Long = currentTimeMillis(),
    val isLoading: Boolean = false,
)

class AppState {
    private val storedTranslation = storedTranslationSettingsFromJson(loadStoredTranslationSettings())
    val devices = mutableStateListOf<Device>().apply { addAll(loadStoredDevices()) }
    val messages = mutableStateListOf<ChatMessage>().apply { addAll(chatHistoryFromJson(loadStoredChatHistory())) }
    var chatDraft by mutableStateOf("")
    var activeAddress by mutableStateOf(loadStoredActiveAddress().takeIf { saved -> devices.any { it.address == saved } } ?: devices.firstOrNull()?.address.orEmpty())
    var providerId by mutableStateOf(storedTranslation.providerId.takeIf { id -> translationProviders.any { it.id == id } } ?: "deepseek")
    var translate by mutableStateOf(storedTranslation.translate)
    val languages = mutableStateListOf<String>().apply { addAll(storedTranslation.targetLanguages.take(2).ifEmpty { listOf("English") }) }
    val providerConfigs = initialProviderConfigs(storedTranslation.configs)
    val provider get() = providerById(providerId)
    val providerConfig get() = providerConfigs[providerId] ?: defaultProviderConfig(provider)
    fun updateProviderConfig(transform: (ProviderConfig) -> ProviderConfig) { providerConfigs[providerId] = transform(providerConfig); persistTranslation() }
    fun selectProvider(id: String) { providerId = id; persistTranslation() }
    fun setLanguages(values: List<String>) { languages.clear(); languages.addAll(values.filter { it.isNotBlank() }.distinct().take(2).ifEmpty { listOf("English") }); persistTranslation() }
    fun persistTranslation() = saveStoredTranslationSettings(StoredTranslationSettings(providerId = providerId, translate = translate, targetLanguages = languages.toList(), configs = providerConfigs.toMap()).toJson())
    fun persist() = saveStoredDevices(devices.toList(), activeAddress)
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
    fun addMessage(message: ChatMessage): Int {
        messages += message
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

    if (endpoint.startsWith("[")) {
        val closingBracket = endpoint.indexOf(']')
        if (closingBracket <= 1) return null
        val address = endpoint.substring(1, closingBracket)
        val portPart = endpoint.substring(closingBracket + 1)
        val port = when {
            portPart.isEmpty() -> 9000
            portPart.startsWith(":") -> portPart.drop(1).toIntOrNull()
            else -> null
        } ?: return null
        return Device(address, port).takeIf { port in 1..65535 }
    }

    if (endpoint.count { it == ':' } == 1) {
        val address = endpoint.substringBeforeLast(':')
        val port = endpoint.substringAfterLast(':').toIntOrNull() ?: return null
        return Device(address, port).takeIf { address.isNotBlank() && port in 1..65535 }
    }
    return Device(endpoint, 9000)
}

package com.vrcmc.app

import kotlinx.serialization.json.*

enum class ProviderProtocol {
    OPENAI,
    ANTHROPIC,
    GOOGLE_WEB,
    MYMEMORY,
    DEEPL,
    LIBRE,
}

data class ProviderRegion(val id: String, val label: String, val baseUrl: String)

data class TranslationProvider(
    val id: String,
    val label: String,
    val protocol: ProviderProtocol,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val models: List<String>,
    val keyRequired: Boolean = true,
    val keyPlaceholder: String = "sk-...",
    val editableBaseUrl: Boolean = false,
    val editableModel: Boolean = false,
    val supportsHeaders: Boolean = false,
    val supportsStreaming: Boolean = false,
    val hint: String,
    val regions: List<ProviderRegion> = emptyList(),
)

data class ProviderConfig(
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val region: String = "",
    val timeoutSeconds: Int = 20,
    val customHeaders: String = "",
    val streaming: Boolean = false,
    val retryCount: Int = 5,
    val fallbackModel: String = "",
    val fallbackRetryCount: Int = 3,
    val fallbackEnabled: Boolean = false,
)

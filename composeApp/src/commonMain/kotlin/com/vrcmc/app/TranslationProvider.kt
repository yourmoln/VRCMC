package com.vrcmc.app

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.serialization.json.*

enum class ProviderProtocol { OPENAI, ANTHROPIC, GOOGLE_WEB, MYMEMORY, DEEPL, LIBRE }

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

val translationProviders = listOf(
    TranslationProvider("openai", "OpenAI / GPT", ProviderProtocol.OPENAI, "https://api.openai.com/v1", "gpt-5.6-sol", listOf("gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna", "gpt-5.5"), hint = "OpenAI 官方接口。Base URL 固定，模型 ID 可从预设中选择。"),
    TranslationProvider("openai_compatible", "OpenAI Compatible", ProviderProtocol.OPENAI, "https://api.openai.com/v1", "gpt-5.6-sol", listOf("gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.5"), editableBaseUrl = true, editableModel = true, supportsHeaders = true, supportsStreaming = true, hint = "适用于 OpenAI 兼容代理、中转站及自建接口。自定义模型 ID 会原样保留。"),
    TranslationProvider("anthropic", "Anthropic / Claude", ProviderProtocol.ANTHROPIC, "https://api.anthropic.com", "claude-sonnet-4-6", listOf("claude-sonnet-5", "claude-sonnet-4-6", "claude-haiku-4-5-20251001"), keyPlaceholder = "sk-ant-...", hint = "Anthropic 官方 Messages API。"),
    TranslationProvider("anthropic_compatible", "Claude Compatible", ProviderProtocol.ANTHROPIC, "https://api.anthropic.com", "claude-sonnet-4-6", listOf("claude-sonnet-5", "claude-sonnet-4-6", "claude-haiku-4-5-20251001"), keyPlaceholder = "sk-ant-...", editableBaseUrl = true, editableModel = true, supportsHeaders = true, hint = "适用于兼容 Anthropic Messages API 的代理或中转接口。"),
    TranslationProvider("xai", "xAI / Grok", ProviderProtocol.OPENAI, "https://api.x.ai/v1", "grok-4.3", listOf("grok-4.3", "grok-4.20", "grok-4.20-0309-non-reasoning"), keyPlaceholder = "xai-...", hint = "xAI 官方 OpenAI-compatible 接口。"),
    TranslationProvider("grok_compatible", "Grok Compatible", ProviderProtocol.OPENAI, "https://api.x.ai/v1", "grok-4.5", listOf("grok-4.5"), keyPlaceholder = "xai-...", editableBaseUrl = true, editableModel = true, supportsHeaders = true, supportsStreaming = true, hint = "适用于 Grok 兼容中转接口，可添加自定义请求头。"),
    TranslationProvider("local_ai", "Local AI / Ollama", ProviderProtocol.OPENAI, "http://127.0.0.1:11434/v1", "qwen2.5:7b-instruct", emptyList(), keyRequired = false, editableBaseUrl = true, editableModel = true, supportsHeaders = true, hint = "本机或局域网 OpenAI-compatible 服务，API Key 可留空。"),
    TranslationProvider("google_web", "Google Web", ProviderProtocol.GOOGLE_WEB, "https://translate.googleapis.com/translate_a/single", "google-web", listOf("google-web"), keyRequired = false, hint = "免 Key 的公共网页翻译接口；部分网络环境可能无法访问。"),
    TranslationProvider("mymemory", "MyMemory", ProviderProtocol.MYMEMORY, "https://api.mymemory.translated.net/get", "mymemory", listOf("mymemory"), keyRequired = false, hint = "免 Key 的翻译记忆库，适合作为零配置备用服务。"),
    TranslationProvider("deepl", "DeepL", ProviderProtocol.DEEPL, "https://api-free.deepl.com/v2", "deepl-api", listOf("deepl-api"), editableBaseUrl = true, hint = "默认使用 DeepL API Free；付费账户请改为 https://api.deepl.com/v2。"),
    TranslationProvider("libretranslate", "LibreTranslate", ProviderProtocol.LIBRE, "http://127.0.0.1:5000", "libretranslate", listOf("libretranslate"), keyRequired = false, editableBaseUrl = true, hint = "自托管 LibreTranslate 通常无需 Key，公共实例可能需要。"),
    TranslationProvider("qianwen", "Qwen", ProviderProtocol.OPENAI, "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", "qwen-mt-plus", listOf("qwen-mt-plus", "qwen-mt-flash"), editableBaseUrl = true, hint = "按 API Key 所属地域选择端点。日本区域需填写工作空间专属 URL。", regions = listOf(ProviderRegion("china", "中国大陆", "https://dashscope.aliyuncs.com/compatible-mode/v1"), ProviderRegion("singapore", "新加坡/国际", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"), ProviderRegion("japan", "日本（自定义）", ""))),
    TranslationProvider("hunyuan", "腾讯混元", ProviderProtocol.OPENAI, "https://api.hunyuan.cloud.tencent.com/v1", "hunyuan-turbos-latest", listOf("hunyuan-turbos-latest", "hunyuan-turbo-latest"), editableBaseUrl = true, hint = "腾讯混元 OpenAI-compatible 接口。"),
    TranslationProvider("xiaomi", "Xiaomi MiMo", ProviderProtocol.OPENAI, "https://api.xiaomimimo.com/v1", "mimo-v2.5-pro", listOf("mimo-v2.5-pro", "mimo-v2.5", "mimo-v2-flash"), editableBaseUrl = true, hint = "按 Key 类型选择全球按量或 Token Plan 集群。", regions = listOf(ProviderRegion("global", "全球按量", "https://api.xiaomimimo.com/v1"), ProviderRegion("china", "中国 Token Plan", "https://token-plan-cn.xiaomimimo.com/v1"), ProviderRegion("singapore", "新加坡 Token Plan", "https://token-plan-sgp.xiaomimimo.com/v1"), ProviderRegion("europe", "欧洲 Token Plan", "https://token-plan-ams.xiaomimimo.com/v1"))),
    TranslationProvider("deepseek", "DeepSeek", ProviderProtocol.OPENAI, "https://api.deepseek.com", "deepseek-v4-flash", listOf("deepseek-v4-flash", "deepseek-v4-pro"), editableBaseUrl = true, hint = "DeepSeek 官方接口；使用中转 Key 时请填写对应 Base URL。"),
    TranslationProvider("zhipu", "GLM / 智谱", ProviderProtocol.OPENAI, "https://open.bigmodel.cn/api/paas/v4", "glm-5.1", listOf("glm-5.1", "glm-5-turbo", "glm-5", "glm-4.7-flash", "glm-4.7-flashx", "glm-4.7"), hint = "智谱 GLM OpenAI-compatible 接口。"),
    TranslationProvider("gemini", "Google Gemini", ProviderProtocol.OPENAI, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.5-flash", listOf("gemini-3.5-flash", "gemini-2.5-flash", "gemini-2.5-flash-lite"), keyPlaceholder = "AI...", hint = "Gemini 官方 OpenAI-compatible 接口。"),
    TranslationProvider("kimi", "Kimi / Moonshot", ProviderProtocol.OPENAI, "https://api.moonshot.cn/v1", "kimi-k2.6", listOf("kimi-k2.6", "kimi-k2.5"), hint = "Moonshot 官方 OpenAI-compatible 接口。"),
    TranslationProvider("mistral", "Mistral", ProviderProtocol.OPENAI, "https://api.mistral.ai/v1", "mistral-medium-3-5", listOf("mistral-medium-3-5", "mistral-medium-latest", "mistral-small-latest", "ministral-8b-latest"), hint = "Mistral 官方接口。"),
    TranslationProvider("doubao", "豆包 / 火山方舟", ProviderProtocol.OPENAI, "https://ark.cn-beijing.volces.com/api/v3", "doubao-seed-2-0-pro-260215", emptyList(), editableModel = true, hint = "请填写火山方舟控制台提供的当前模型或推理接入点 ID。"),
    TranslationProvider("nvidia", "NVIDIA AI", ProviderProtocol.OPENAI, "https://integrate.api.nvidia.com/v1", "nvidia/nemotron-3-nano-30b-a3b", listOf("nvidia/nemotron-3-nano-30b-a3b"), editableBaseUrl = true, editableModel = true, hint = "支持 NVIDIA API Catalog、NIM 自部署或兼容代理。"),
)

fun providerById(id: String) = translationProviders.firstOrNull { it.id == id } ?: translationProviders.first()
fun defaultProviderConfig(provider: TranslationProvider) = ProviderConfig(
    baseUrl = provider.defaultBaseUrl,
    model = provider.defaultModel,
    region = provider.regions.firstOrNull()?.id.orEmpty(),
    fallbackModel = provider.models.firstOrNull { it != provider.defaultModel }.orEmpty(),
)

data class StoredTranslationSettings(
    val providerId: String = "deepseek",
    val translate: Boolean = false,
    val sendOriginalBeforeTranslation: Boolean = true,
    val targetLanguages: List<String> = listOf("English"),
    val outputOrder: List<String> = targetLanguages + originalOutputKey,
    val configs: Map<String, ProviderConfig> = emptyMap(),
)

data class ProviderSecrets(
    val apiKey: String = "",
    val customHeaders: String = "",
)

fun StoredTranslationSettings.toJson(): String = buildJsonObject {
    put("provider", providerId); put("translate", translate); put("sendOriginalBeforeTranslation", sendOriginalBeforeTranslation); putJsonArray("targetLanguages") { targetLanguages.take(2).forEach(::add) }
    putJsonArray("outputOrder") { normalizeOutputOrder(targetLanguages, outputOrder).forEach(::add) }
    putJsonObject("configs") { configs.forEach { (id, value) -> putJsonObject(id) {
        put("baseUrl", value.baseUrl); put("model", value.model); put("region", value.region); put("timeout", value.timeoutSeconds); put("streaming", value.streaming); put("retries", value.retryCount)
        put("fallbackModel", value.fallbackModel); put("fallbackRetries", value.fallbackRetryCount); put("fallbackEnabled", value.fallbackEnabled)
    } } }
}.toString()

fun Map<String, ProviderSecrets>.toSecretsJson(): String = buildJsonObject {
    forEach { (id, value) ->
        if (value.apiKey.isNotBlank() || value.customHeaders.isNotBlank()) {
            putJsonObject(id) { put("apiKey", value.apiKey); put("headers", value.customHeaders) }
        }
    }
}.toString()

fun storedProviderSecretsFromJson(value: String): Map<String, ProviderSecrets> = runCatching {
    if (value.isBlank()) return emptyMap()
    Json.parseToJsonElement(value).jsonObject.mapValues { (_, element) ->
        element.jsonObject.let { obj ->
            ProviderSecrets(
                apiKey = obj["apiKey"]?.jsonPrimitive?.content.orEmpty(),
                customHeaders = obj["headers"]?.jsonPrimitive?.content.orEmpty(),
            )
        }
    }
}.getOrDefault(emptyMap())

fun storedTranslationSettingsFromJson(value: String): StoredTranslationSettings = runCatching {
    val root = Json.parseToJsonElement(value).jsonObject
    val configs = root["configs"]?.jsonObject?.mapValues { (_, element) -> element.jsonObject.let { obj ->
        ProviderConfig(
            apiKey = obj["apiKey"]?.jsonPrimitive?.content.orEmpty(),
            baseUrl = obj["baseUrl"]?.jsonPrimitive?.content.orEmpty(),
            model = obj["model"]?.jsonPrimitive?.content.orEmpty(),
            region = obj["region"]?.jsonPrimitive?.content.orEmpty(),
            timeoutSeconds = obj["timeout"]?.jsonPrimitive?.intOrNull ?: 20,
            customHeaders = obj["headers"]?.jsonPrimitive?.content.orEmpty(),
            streaming = obj["streaming"]?.jsonPrimitive?.booleanOrNull ?: false,
            retryCount = (obj["retries"]?.jsonPrimitive?.intOrNull ?: 5).coerceIn(0, 10),
            fallbackModel = obj["fallbackModel"]?.jsonPrimitive?.content.orEmpty(),
            fallbackRetryCount = (obj["fallbackRetries"]?.jsonPrimitive?.intOrNull ?: 3).coerceIn(0, 10),
            fallbackEnabled = obj["fallbackEnabled"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    } }.orEmpty()
    val languages = root["targetLanguages"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.filter { it.isNotBlank() }?.distinct()?.take(2)
        ?: listOf(root["targetLanguage"]?.jsonPrimitive?.contentOrNull ?: "English")
    val selectedLanguages = languages.ifEmpty { listOf("English") }
    val storedOrder = root["outputOrder"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
    StoredTranslationSettings(
        providerId = root["provider"]?.jsonPrimitive?.content ?: "deepseek",
        translate = root["translate"]?.jsonPrimitive?.booleanOrNull ?: false,
        sendOriginalBeforeTranslation = root["sendOriginalBeforeTranslation"]?.jsonPrimitive?.booleanOrNull ?: true,
        targetLanguages = selectedLanguages,
        outputOrder = normalizeOutputOrder(selectedLanguages, storedOrder),
        configs = configs,
    )
}.getOrDefault(StoredTranslationSettings())

fun initialProviderConfigs(
    stored: Map<String, ProviderConfig>,
    secrets: Map<String, ProviderSecrets> = emptyMap(),
) = mutableStateMapOf<String, ProviderConfig>().apply {
    translationProviders.forEach { provider ->
        val value = stored[provider.id] ?: defaultProviderConfig(provider)
        val protected = secrets[provider.id]
        put(
            provider.id,
            value.copy(
                apiKey = protected?.apiKey ?: value.apiKey,
                customHeaders = protected?.customHeaders ?: value.customHeaders,
            ),
        )
    }
}

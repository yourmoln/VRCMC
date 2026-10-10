package com.vrcmc.app

internal const val defaultTranslationProviderId = "microsoft_edge_web"
internal const val customCompatibleProviderId = "custom_compatible"

private val customCompatibleProvider =
    TranslationProvider(
        id = customCompatibleProviderId,
        label = "Custom compatible",
        protocol = ProviderProtocol.OPENAI,
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-6.1-sol",
        models = emptyList(),
        editableModel = true,
        supportsHeaders = true,
        supportsStreaming = true,
        hint = "手动添加 OpenAI-compatible 或 Anthropic-compatible 服务。",
    )

private val catalogProviders = coreProviders + regionalProviders + additionalProviders

/** Services shown in the provider picker. */
val translationProviders =
    listOf("qianwen", "microsoft_edge_web", "deepseek", "openai")
        .map { id -> catalogProviders.first { it.id == id } } + customCompatibleProvider

/** All built-in definitions, including providers kept for backwards-compatible settings reads. */
internal val allTranslationProviders =
    (catalogProviders + customCompatibleProvider).distinctBy { it.id }

fun providerById(id: String) =
    allTranslationProviders.firstOrNull { it.id == id }
        ?: translationProviders.first { it.id == defaultTranslationProviderId }

fun defaultProviderConfig(provider: TranslationProvider) =
    ProviderConfig(
        baseUrl = provider.defaultBaseUrl,
        model = provider.defaultModel,
        region = provider.regions.firstOrNull()?.id.orEmpty(),
        fallbackModel = provider.models.firstOrNull { it != provider.defaultModel }.orEmpty(),
    )

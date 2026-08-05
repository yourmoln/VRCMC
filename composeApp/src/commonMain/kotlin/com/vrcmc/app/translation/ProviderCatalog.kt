package com.vrcmc.app

val translationProviders = coreProviders + regionalProviders + additionalProviders

fun providerById(id: String) =
    translationProviders.firstOrNull { it.id == id } ?: translationProviders.first()

fun defaultProviderConfig(provider: TranslationProvider) =
    ProviderConfig(
        baseUrl = provider.defaultBaseUrl,
        model = provider.defaultModel,
        region = provider.regions.firstOrNull()?.id.orEmpty(),
        fallbackModel = provider.models.firstOrNull { it != provider.defaultModel }.orEmpty(),
    )

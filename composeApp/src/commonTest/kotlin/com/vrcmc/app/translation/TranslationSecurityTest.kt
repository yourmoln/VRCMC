package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TranslationSecurityTest {
    @Test
    fun acceptsHttpsCredentials() {
        assertNull(
            endpointSecurityError(
                ProviderConfig(apiKey = "secret", baseUrl = "https://example.com")
            )
        )
    }

    @Test
    fun acceptsHttpEndpointsWithCredentialsAndHeaders() {
        listOf(
            ProviderConfig(baseUrl = "http://127.0.0.1:11434/v1"),
            ProviderConfig(apiKey = "secret", baseUrl = "http://example.com"),
            ProviderConfig(
                baseUrl = "http://example.com",
                customHeaders = "Authorization: secret",
            ),
        ).forEach { config -> assertNull(endpointSecurityError(config)) }
    }

    @Test
    fun rejectsUnsupportedUrlSchemes() {
        assertEquals(
            "Base URL 必须以 http:// 或 https:// 开头",
            endpointSecurityError(ProviderConfig(baseUrl = "ftp://example.com")),
        )
    }
}

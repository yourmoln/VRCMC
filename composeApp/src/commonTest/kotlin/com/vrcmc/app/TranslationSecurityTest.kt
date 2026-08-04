package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TranslationSecurityTest {
    @Test
    fun acceptsHttpsCredentials() {
        assertNull(endpointSecurityError(ProviderConfig(apiKey = "secret", baseUrl = "https://example.com")))
    }

    @Test
    fun acceptsCredentialFreeLocalHttp() {
        assertNull(endpointSecurityError(ProviderConfig(baseUrl = "http://127.0.0.1:11434/v1")))
    }

    @Test
    fun rejectsCredentialsAndHeadersOverHttp() {
        assertEquals(
            "HTTP 端点不能携带 API Key 或自定义请求头，请改用 HTTPS",
            endpointSecurityError(ProviderConfig(apiKey = "secret", baseUrl = "http://example.com")),
        )
        assertEquals(
            "HTTP 端点不能携带 API Key 或自定义请求头，请改用 HTTPS",
            endpointSecurityError(ProviderConfig(baseUrl = "http://example.com", customHeaders = "Authorization: secret")),
        )
    }

    @Test
    fun rejectsPublicCleartextEndpoints() {
        assertEquals(
            "HTTP 仅允许用于本机或局域网端点，公网端点必须使用 HTTPS",
            endpointSecurityError(ProviderConfig(baseUrl = "http://example.com")),
        )
        assertNull(endpointSecurityError(ProviderConfig(baseUrl = "http://192.168.1.20:11434/v1")))
        assertNull(endpointSecurityError(ProviderConfig(baseUrl = "http://[::1]:11434/v1")))
    }
}

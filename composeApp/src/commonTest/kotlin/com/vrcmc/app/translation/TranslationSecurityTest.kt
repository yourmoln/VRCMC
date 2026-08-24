package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranslationSecurityTest {
    @Test
    fun acceptsHttpsCredentials() {
        assertTrue(isSupportedHttpEndpoint("https://example.com"))
    }

    @Test
    fun acceptsHttpEndpointsWithCredentialsAndHeaders() {
        listOf("http://127.0.0.1:11434/v1", "http://example.com").forEach {
            assertTrue(isSupportedHttpEndpoint(it))
        }
    }

    @Test
    fun rejectsUnsupportedUrlSchemes() {
        assertFalse(isSupportedHttpEndpoint("ftp://example.com"))
    }
}

package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MicrosoftEdgeWebTranslationTest {
    @Test
    fun providerIsKeylessAndUsesEdgeEndpoint() {
        val provider = providerById("microsoft_edge_web")
        val config = defaultProviderConfig(provider)

        assertEquals(ProviderProtocol.MICROSOFT_EDGE_WEB, provider.protocol)
        assertEquals("https://edge.microsoft.com/translate/translatetext", config.baseUrl)
        assertEquals("microsoft-edge-web", config.model)
        assertFalse(provider.keyRequired)
        assertTrue(provider.isConfigured(config))
    }

    @Test
    fun mapsMicrosoftChineseLanguageCodes() {
        assertEquals("zh-Hans", microsoftEdgeLanguageCode("简体中文"))
        assertEquals("zh-Hant", microsoftEdgeLanguageCode("繁體中文"))
        assertEquals("ja", microsoftEdgeLanguageCode("日本語"))
    }

    @Test
    fun parsesMicrosoftTranslationArray() {
        val raw =
            """[{"detectedLanguage":{"language":"en"},"translations":[{"text":" こんにちは ","to":"ja"}]}]"""

        assertEquals("こんにちは", parseMicrosoftEdgeTranslation(raw))
    }

    @Test
    fun rejectsMissingOrEmptyTranslation() {
        assertNull(parseMicrosoftEdgeTranslation("{}"))
        assertNull(parseMicrosoftEdgeTranslation("""[{"translations":[{"text":"  "}]}]"""))
    }
}

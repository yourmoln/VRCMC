package com.vrcmc.app

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TranslationFailureLocalizationTest {
    @Test
    fun emptyResponsesAreRenderedInTheSelectedUiLanguage() {
        val failure =
            TranslationResult.Failure(
                reason = TranslationFailureReason.EMPTY_RESPONSE,
                provider = "Microsoft Edge Web",
            )

        assertEquals(
            "Microsoft Edge Web 未返回可用翻译",
            LocaleStringsZhHans.translationFailureMessage(failure),
        )
        assertEquals(
            "Microsoft Edge Web did not return a usable translation",
            LocaleStringsEn.translationFailureMessage(failure),
        )
        assertEquals(
            "Microsoft Edge Webから利用可能な翻訳が返されませんでした",
            LocaleStringsJa.translationFailureMessage(failure),
        )
        assertEquals(
            "Microsoft Edge Web 未傳回可用翻譯",
            LocaleStringsZhHant.translationFailureMessage(failure),
        )
    }

    @Test
    fun dynamicNetworkDetailsArePreservedAfterLocalization() {
        val failure =
            TranslationResult.Failure(
                message = "timeout",
                reason = TranslationFailureReason.NETWORK_REQUEST_FAILED,
            )

        assertEquals(
            "网络请求失败：timeout",
            LocaleStringsZhHans.translationFailureMessage(failure),
        )
        assertEquals(
            "Network request failed: timeout",
            LocaleStringsEn.translationFailureMessage(failure),
        )
    }

    @Test
    fun translationValidationReturnsAReasonInsteadOfDisplayText() = runBlocking {
        val provider = providerById("microsoft_edge_web")
        val result = translateText(provider, defaultProviderConfig(provider), "English", "")
        val failure = assertIs<TranslationResult.Failure>(result)

        assertEquals(TranslationFailureReason.EMPTY_INPUT, failure.reason)
        assertEquals("", failure.message)
    }

    @Test
    fun voiceFailuresAreAlsoRenderedInTheSelectedUiLanguage() {
        val failure =
            VoiceTranscriptionResult.Failure(
                reason = VoiceTranscriptionFailureReason.EMPTY_RESPONSE,
            )

        assertEquals(
            "服务返回成功，但没有可用的识别文字",
            LocaleStringsZhHans.voiceTranscriptionFailureMessage(failure),
        )
        assertEquals(
            "The service did not return usable transcription text",
            LocaleStringsEn.voiceTranscriptionFailureMessage(failure),
        )
        assertEquals(
            "サービスから利用可能な認識テキストが返されませんでした",
            LocaleStringsJa.voiceTranscriptionFailureMessage(failure),
        )
        assertEquals(
            "服務已成功回應，但沒有可用的辨識文字",
            LocaleStringsZhHant.voiceTranscriptionFailureMessage(failure),
        )
    }
}

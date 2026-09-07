package com.vrcmc.app

import kotlin.test.*
import kotlinx.coroutines.runBlocking

class ChatboxOutputTest {
    private val device = Device("127.0.0.1", receivePort = 19000)

    private class RecordingSender {
        val payloads = mutableListOf<String>()
        val endpoints = mutableListOf<Pair<String, Int>>()
        var succeeds = true

        suspend fun send(address: String, text: String, port: Int): Boolean {
            payloads += text
            endpoints += address to port
            return succeeds
        }
    }

    @Test fun normalPayloadAboveDynamicInputAllowanceIsSentWithoutWarning() = runBlocking {
        val sender = RecordingSender()
        val text = "x".repeat(100)
        assertTrue(text.length > chatboxInputCharacterLimit(true, 2))
        assertEquals(ChatboxSendResult.SENT, sendChatboxOutput(device, text, sender::send))
        assertEquals(listOf(text), sender.payloads)
        assertEquals(listOf("127.0.0.1" to 19000), sender.endpoints)
    }

    @Test fun exactCharacterAndLineBoundariesDoNotWarn() = runBlocking {
        val sender = RecordingSender()
        val texts = listOf("x".repeat(144), (1..9).joinToString("\n") { "line" })
        for (text in texts) {
            assertEquals(ChatboxSendResult.SENT, sendChatboxOutput(device, text, sender::send))
        }
        assertEquals(texts, sender.payloads)
    }

    @Test fun excessiveCharactersAndLinesWarnButAreSentUnmodified() = runBlocking {
        val sender = RecordingSender()
        val texts = listOf("x".repeat(145), (1..10).joinToString("\n") { "line" })
        for (text in texts) {
            assertEquals(ChatboxSendResult.SENT_OVER_LIMIT, sendChatboxOutput(device, text, sender::send))
        }
        assertEquals(texts, sender.payloads)
    }

    @Test fun expandedReplacementIsSentInFull() = runBlocking {
        val sender = RecordingSender()
        val replacement = (1..10).joinToString("\n") { "x".repeat(20) }
        val dictionary = HotwordDictionary(listOf(KeywordReplacement("keyword", replacement)))
        val output = dictionary.process("keyword")!!
        assertEquals(ChatboxSendResult.SENT_OVER_LIMIT, sendChatboxOutput(device, output, sender::send))
        assertEquals(listOf(replacement), sender.payloads)
    }

    @Test fun shortenedReplacementDoesNotWarnAboutOversizedSource() = runBlocking {
        val sender = RecordingSender()
        val keyword = "x".repeat(200)
        val dictionary = HotwordDictionary(listOf(KeywordReplacement(keyword, "short")))
        val output = dictionary.process(keyword)!!
        assertEquals(ChatboxSendResult.SENT, sendChatboxOutput(device, output, sender::send))
        assertEquals(listOf("short"), sender.payloads)
    }

    @Test fun hiddenOriginalIsNotIncludedInFinalPayloadValidation() = runBlocking {
        val sender = RecordingSender()
        val output = buildTranslationOutput(
            "x".repeat(200), mapOf("English" to "hello"),
            listOf("English", originalOutputKey), showOriginalText = false,
        )
        assertEquals(ChatboxSendResult.SENT, sendChatboxOutput(device, output, sender::send))
        assertEquals(listOf("hello"), sender.payloads)
    }

    @Test fun disabledOrSkippedTranslationStillSendsOriginalWhenOriginalDisplayIsOff() = runBlocking {
        val sender = RecordingSender()
        for (text in listOf("x".repeat(100), "12345")) {
            val output = buildTranslationOutput(
                text, emptyMap(), listOf("English", originalOutputKey), showOriginalText = false,
            )
            assertEquals(ChatboxSendResult.SENT, sendChatboxOutput(device, output, sender::send))
            assertEquals(text, sender.payloads.last())
        }
    }

    @Test fun combinedTranslationPayloadIsCheckedAfterFormatting() = runBlocking {
        val sender = RecordingSender()
        val original = "x".repeat(72)
        val translation = "y".repeat(72)
        val output = buildTranslationOutput(
            original, mapOf("English" to translation), listOf("English", originalOutputKey),
        )
        assertEquals(145, output.length)
        assertEquals(ChatboxSendResult.SENT_OVER_LIMIT, sendChatboxOutput(device, output, sender::send))
        assertEquals(listOf("$translation\n$original"), sender.payloads)
    }

    @Test fun oversizedOriginalPreviewDoesNotPreventFinalTranslationSend() = runBlocking {
        val sender = RecordingSender()
        val original = "x".repeat(144)
        val preview = "$original\n(Translating...)"
        assertEquals(ChatboxSendResult.SENT_OVER_LIMIT, sendChatboxOutput(device, preview, sender::send))
        val output = buildTranslationOutput(
            original, mapOf("English" to "hello"), listOf("English", originalOutputKey),
            showOriginalText = false,
        )
        assertEquals(ChatboxSendResult.SENT, sendChatboxOutput(device, output, sender::send))
        assertEquals(listOf(preview, "hello"), sender.payloads)
    }

    @Test fun emptyPayloadIsSkippedWithoutAnOverLimitWarning() = runBlocking {
        val sender = RecordingSender()
        for (text in listOf("", " \n\t", "\n".repeat(10))) {
            assertEquals(ChatboxSendResult.EMPTY, sendChatboxOutput(device, text, sender::send))
        }
        assertTrue(sender.payloads.isEmpty())
    }

    @Test fun transportFailureIsNotMisreportedAsOverLimit() = runBlocking {
        val sender = RecordingSender().apply { succeeds = false }
        val text = "x".repeat(145)
        assertEquals(ChatboxSendResult.FAILED, sendChatboxOutput(device, text, sender::send))
        assertEquals(listOf(text), sender.payloads)
    }

    @Test fun livePreviewDoesNotBlockButFormalSendDoes() = runBlocking {
        val sender = RecordingSender()
        val dictionary = HotwordDictionary(
            listOf(KeywordReplacement("alias", "blocked")), listOf("blocked"),
        )
        assertEquals(ChatboxSendResult.SENT,
            sendChatboxOutput(device, dictionary.replaceKeywords("alias"), sender::send))
        assertNull(dictionary.process("alias"))
        assertEquals(listOf("blocked"), sender.payloads)
    }
}

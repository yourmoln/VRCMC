package com.vrcmc.app

import kotlin.test.*

class HotwordDictionaryTest {
    @Test fun replacesAllOccurrencesWithoutCascading() {
        val dictionary = HotwordDictionary(listOf(
            KeywordReplacement("cat", "dog"), KeywordReplacement("dog", "fox"),
            KeywordReplacement("catfish", "fish"),
        ))
        assertEquals("dog fish dog fox", dictionary.process(" cat catfish cat dog "))
        assertEquals("CAT", dictionary.process("CAT"))
    }

    @Test fun blocksWholeSentencesBeforeAndAfterReplacement() {
        val dictionary = HotwordDictionary(
            listOf(KeywordReplacement("bad", "good"), KeywordReplacement("alias", "blocked")),
            listOf("bad", "blocked"),
        )
        assertNull(dictionary.process(" bad "))
        assertNull(dictionary.process("alias"))
        assertEquals("a good sentence", dictionary.process("a bad sentence"))
    }

    @Test fun supportsLiteralKeywordsUnicodeAndDeletion() {
        val dictionary = HotwordDictionary(listOf(
            KeywordReplacement(".*", ""), KeywordReplacement("你好", "您好"),
        ))
        assertEquals("您好", dictionary.process(".*你好"))
        assertEquals("", dictionary.process(".*"))
    }

    @Test fun preservesFullReplacementBeyondChatboxLimit() {
        val dictionary = HotwordDictionary(listOf(KeywordReplacement("a", "b".repeat(145))))
        assertEquals("b".repeat(145), dictionary.process("a"))
        assertEquals("b".repeat(145), dictionary.replaceKeywords("a"))
    }

    @Test fun previewsReplaceKeywordsWithoutBlockingSentences() {
        val dictionary = HotwordDictionary(
            listOf(KeywordReplacement("bad", "good"), KeywordReplacement("alias", "blocked")),
            listOf("bad", "blocked"),
        )
        assertEquals("good", dictionary.replaceKeywords("bad"))
        assertEquals("blocked", dictionary.replaceKeywords("alias"))
        assertEquals("blocked", dictionary.replaceKeywords("blocked"))
        assertNull(dictionary.process("bad"))
        assertNull(dictionary.process("alias"))
        assertNull(dictionary.process("blocked"))
    }

    @Test fun roundTripsJsonIncludingEscapedTextAndEmptyTargets() {
        val dictionary = HotwordDictionary(
            listOf(KeywordReplacement("\"你好\"", "line\nnext"), KeywordReplacement("delete", "")),
            listOf("blocked"),
        )
        assertEquals(dictionary, hotwordDictionaryFromJson(dictionary.toJson()))
        assertEquals(dictionary, hotwordDictionaryFromJson("\uFEFF" + dictionary.toJson()))
        assertEquals(HotwordDictionary(), hotwordDictionaryFromJson(HotwordDictionary().toJson()))
    }

    @Test fun rejectsMalformedImportsInsteadOfSilentlyDroppingRules() {
        listOf("{}", "[]", "null", "{\"other\": []}",
            "{\"blockedSentences\": [42]}", "{\"blockedSentences\": [\" \" ]}",
            "{\"replacements\": [{\"keyword\":\"\",\"target\":\"x\"}]}",
            "{\"replacements\": [{\"keyword\":\"x\"}]}",
            "{\"replacements\": [{\"keyword\":\"a\",\"target\":\"b\"},{\"keyword\":\"a\",\"target\":\"c\"}]}"
        ).forEach { json -> assertFails { hotwordDictionaryFromJson(json) } }
    }
}

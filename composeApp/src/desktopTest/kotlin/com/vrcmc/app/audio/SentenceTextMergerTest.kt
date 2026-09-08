package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SentenceTextMergerTest {
    @Test
    fun overlappingWordsAndArtificialPunctuationAreMerged() {
        assertEquals("我想学习日语然后去旅行。", mergeSentenceChunk("我想学习日语。", "学习日语然后去旅行。", true))
        assertEquals("I would like to learn Japanese.", mergeSentenceChunk("I would like to learn.", "to learn Japanese.", true))
        assertEquals("I need a translation service.", mergeSentenceChunk("I need a transla", "translation service.", true))
        assertEquals("We need a translation service.", mergeSentenceChunk("We need a translation", "a translation service.", true))
        assertEquals("一共十分钟，他很有信心。", mergeSentenceChunk("一共十分钟。", "中，他很有信心。", true))
    }

    @Test
    fun nonOverlappingChunksAndUnrelatedWordEndingsArePreserved() {
        assertEquals("测试测试成功", mergeSentenceChunk("测试", "测试成功", false))
        assertEquals("This is an example.", mergeSentenceChunk("This is", "an example.", true))
        assertEquals("The cat at home.", mergeSentenceChunk("The cat", "at home.", true))
        assertEquals("这是完整的句子。", mergeSentenceChunk("这是", "完整的句子。", true))
    }

    @Test
    fun continuousNarrationPublishesCompleteSentencesAndHoldsArtificialEnding() {
        val assembler = SentenceTextAssembler()
        assertEquals(listOf("第一句已经完整说完了。"), assembler.append("第一句已经完整说完了。接下来还需要继续进行。", false))
        assertEquals(listOf("接下来还需要继续进行测试，才能确认结果。"), assembler.append("继续进行测试，才能确认结果。还会继续验证其他的功能。", true))
        assertEquals(listOf("还会继续验证其他的功能。"), assembler.finish())
    }

    @Test
    fun cappedAsrPunctuationDoesNotTurnFragmentIntoSentence() {
        val assembler = SentenceTextAssembler()
        assertTrue(assembler.append("我需要一个能将语音进行。", false).isEmpty())
        assertTrue(assembler.append("进行翻译的工具。", true).isEmpty())
        assertEquals(listOf("我需要一个能将语音进行翻译的工具。"), assembler.finish())
    }

    @Test
    fun abbreviationsAndDecimalNumbersDoNotSplitSentences() {
        val assembler = SentenceTextAssembler()
        assertEquals(listOf("Dr. Smith paid 3.14 dollars."), assembler.append("Dr. Smith paid 3.14 dollars. Then he went home.", false))
        assertEquals(listOf("Then he went home."), assembler.finish())
    }
}

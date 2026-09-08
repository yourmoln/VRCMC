package com.vrcmc.app

/** Joins adjacent, overlapping ASR windows belonging to the same spoken utterance. */
internal fun mergeSentenceChunk(previous: String, next: String, hasOverlap: Boolean): String {
    val left = previous.trim()
    val right = next.trim()
    if (left.isEmpty()) return right
    if (right.isEmpty()) return left
    if (hasOverlap) {
        // Ignore punctuation inserted by ASR at the artificial audio boundary. Keep source
        // offsets so the newer window can replace an incomplete word with its full spelling.
        val leftIndices = left.indices.filter { left[it].isLetterOrDigit() }
        val rightIndices = right.indices.filter { right[it].isLetterOrDigit() }
        val leftKey = leftIndices.map { left[it].lowercaseChar() }.joinToString("")
        val rightKey = rightIndices.map { right[it].lowercaseChar() }.joinToString("")
        for (length in minOf(leftKey.length, rightKey.length, 100) downTo 1) {
            if (leftKey.takeLast(length) == rightKey.take(length)) {
                // A window may start on just the last syllable before a pause: "分钟。" +
                // "中，他…". Require punctuation after it to avoid swallowing repeated words.
                if (length == 1 && !(right.first().isCjkCharacter() && right.getOrNull(1)?.let { it in "，。！？、：；" } == true)) continue
                val start = leftIndices[leftIndices.size - length]
                // Latin matches must start at a word boundary unless the next window begins
                // with the same unfinished word (e.g. "transla" + "translation").
                if (start > 0 && left[start].isAsciiLetter() && left[start - 1].isAsciiLetter()) continue
                return left.substring(0, start) + right
            }
        }
        // An isolated final syllable at the start of an overlapping Chinese window can be
        // transcribed as a different character. Prefer the preceding window's complete word
        // when the new window has only one Han character before punctuation.
        if (right.length >= 2 && right[0] in '\u3400'..'\u9fff' && right[1] in "，。！？、：；" &&
            leftKey.length >= 2 && leftKey.takeLast(2).all { it in '\u3400'..'\u9fff' }
        ) {
            return left.trimEnd { it in "。.!?！？" } + right.drop(1)
        }
    }
    // With no reliable overlap match, a terminal period from the duration-limited ASR
    // window is not evidence of a real sentence boundary.
    val prefix = if (hasOverlap) left.trimEnd { it in "。.!?！？" } else left
    val separator = if (prefix.lastOrNull()?.isCjkCharacter() == true || right.first().isCjkCharacter()) "" else " "
    return prefix + separator + right
}

/** Keeps the unfinished tail across ASR windows and releases only stable sentence endings. */
internal class SentenceTextAssembler {
    private var pending = ""

    fun append(text: String, hasOverlap: Boolean): List<String> {
        pending = mergeSentenceChunk(pending, text, hasOverlap)
        return drain(final = false)
    }

    fun finish(): List<String> = drain(final = true)

    private fun drain(final: Boolean): List<String> {
        val sentences = mutableListOf<String>()
        var start = 0
        var index = 0
        while (index < pending.length) {
            if (!isSentenceEnd(pending, index)) { index++; continue }
            var end = index + 1
            while (end < pending.length && pending[end] in "。.!?！？\"'”’」』）)") end++
            // Preserve enough trailing words to deduplicate the 640 ms audio overlap.
            // In particular, never trust punctuation at the end of a capped ASR result.
            if (!final && pending.substring(end).count(Char::isLetterOrDigit) < 8) break
            pending.substring(start, end).trim().takeIf(String::isNotBlank)?.let(sentences::add)
            start = end
            index = end
        }
        pending = pending.substring(start).trimStart()
        if (final) {
            pending.trim().takeIf(String::isNotBlank)?.let(sentences::add)
            pending = ""
        }
        return sentences
    }
}

private fun isSentenceEnd(text: String, index: Int): Boolean {
    if (text[index] in "。！？!?") return true
    if (text[index] != '.') return false
    if (text.getOrNull(index - 1)?.isDigit() == true && text.getOrNull(index + 1)?.isDigit() == true) return false
    val word = text.substring(0, index).takeLastWhile(Char::isLetter).lowercase()
    return word.length != 1 && word !in setOf("mr", "mrs", "ms", "dr", "prof", "sr", "jr", "vs", "etc")
}

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

private fun Char.isCjkCharacter(): Boolean =
    this in '\u3040'..'\u30ff' || this in '\u3400'..'\u9fff' || this in '\uac00'..'\ud7af'

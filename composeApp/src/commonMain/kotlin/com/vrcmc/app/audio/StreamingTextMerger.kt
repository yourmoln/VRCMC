package com.vrcmc.app

internal class StreamingTextMerger(private val stableRepeats: Int = 2) {
    private var stablePrefix = ""
    private var candidatePrefix = ""
    private var candidateHits = 0
    private var lastPartial = ""
    private var currentText = ""

    fun ingestPartial(text: String): String {
        val normalized = normalizeStreamingText(text)
        if (normalized.isEmpty()) return currentText
        if (lastPartial.isEmpty()) {
            lastPartial = normalized
            currentText = normalized
            return normalized
        }
        val common = commonPrefix(lastPartial, normalized)
        if (common.length > stablePrefix.length) {
            if (common == candidatePrefix) candidateHits++ else {
                candidatePrefix = common
                candidateHits = 1
            }
            if (candidateHits >= stableRepeats.coerceAtLeast(1)) stablePrefix = common
        } else {
            candidatePrefix = common
            candidateHits = if (common.isEmpty()) 0 else 1
        }
        lastPartial = normalized
        currentText = normalized
        if (stablePrefix.isNotEmpty() && !currentText.startsWith(stablePrefix)) {
            currentText = stablePrefix
        }
        return currentText
    }

    fun ingestFinal(text: String): String {
        val finalText = normalizeStreamingText(text).ifEmpty { currentText.ifEmpty { stablePrefix } }
        reset()
        return finalText
    }

    fun reset() {
        stablePrefix = ""
        candidatePrefix = ""
        candidateHits = 0
        lastPartial = ""
        currentText = ""
    }
}

private fun normalizeStreamingText(text: String) = text.trim().split(Regex("\\s+")).joinToString(" ")

private fun commonPrefix(left: String, right: String): String {
    val length = minOf(left.length, right.length)
    var index = 0
    while (index < length && left[index] == right[index]) index++
    return left.substring(0, index).trimEnd()
}

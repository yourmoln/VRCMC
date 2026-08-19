package com.vrcmc.app

internal const val originalOutputKey = "__original__"

internal fun normalizeOutputOrder(
    languages: List<String>,
    configuredOrder: List<String>,
): List<String> {
    val selected =
        languages.filter { it.isNotBlank() }.distinct().take(2).ifEmpty { listOf("English") }
    val valid = selected + originalOutputKey
    if (configuredOrder.isEmpty()) return valid

    val normalized = configuredOrder.filter { it in valid }.distinct().toMutableList()
    valid
        .filterNot { it in normalized }
        .forEach { key ->
            val originalIndex = normalized.indexOf(originalOutputKey)
            if (key != originalOutputKey && originalIndex >= 0) normalized.add(originalIndex, key)
            else normalized.add(key)
        }
    return normalized
}

internal fun buildTranslationOutput(
    original: String,
    translations: Map<String, String>,
    outputOrder: List<String>,
    lineBreakOutput: Boolean = true,
    showOriginalText: Boolean = true,
): String {
    val values =
        outputOrder
        .mapNotNull { key ->
            when (key) {
                originalOutputKey -> original.takeIf { showOriginalText && it.isNotBlank() }
                else -> translations[key]?.takeIf(String::isNotBlank)
            }
        }
    if (lineBreakOutput) return values.joinToString("\n")
    return values.mapIndexed { index, value -> if (index == 1) "($value)" else value }.joinToString("")
}

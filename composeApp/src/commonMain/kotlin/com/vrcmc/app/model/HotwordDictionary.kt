package com.vrcmc.app

import kotlinx.serialization.json.*

data class KeywordReplacement(val keyword: String, val target: String)

data class HotwordDictionary(
    val replacements: List<KeywordReplacement> = emptyList(),
    val blockedSentences: List<String> = emptyList(),
) {
    fun process(text: String): String? {
        val original = text.trim()
        if (original in blockedSentences) return null
        return replaceKeywords(original).takeUnless { it in blockedSentences }
    }

    // Scan the original once so replacement targets cannot trigger another rule.
    fun replaceKeywords(text: String): String {
        val original = text.trim()
        val rules = replacements.sortedByDescending { it.keyword.length }
        return buildString {
            var index = 0
            while (index < original.length) {
                val rule = rules.firstOrNull {
                    it.keyword.isNotEmpty() && original.startsWith(it.keyword, index)
                }
                if (rule == null) append(original[index++])
                else {
                    append(rule.target)
                    index += rule.keyword.length
                }
            }
        }.trim()
    }

    fun toJson(): String = dictionaryJson.encodeToString(JsonObject.serializer(), buildJsonObject {
        put("replacements", buildJsonArray {
            replacements.forEach { rule -> add(buildJsonObject {
                put("keyword", rule.keyword)
                put("target", rule.target)
            }) }
        })
        put("blockedSentences", buildJsonArray { blockedSentences.forEach { add(it) } })
    })
}

private val dictionaryJson = Json { prettyPrint = true }

fun hotwordDictionaryFromJson(value: String): HotwordDictionary {
    val root = dictionaryJson.parseToJsonElement(value.removePrefix("\uFEFF")).jsonObject
    require(root.isNotEmpty() && root.keys.all { it in setOf("replacements", "blockedSentences") })
    fun JsonElement.stringValue(): String {
        val primitive = jsonPrimitive
        require(primitive.isString)
        return primitive.content
    }
    val replacements = root["replacements"]?.jsonArray?.map { element ->
        val rule = element.jsonObject
        require(rule.keys == setOf("keyword", "target"))
        val keyword = rule.getValue("keyword").stringValue()
        require(keyword.isNotBlank())
        KeywordReplacement(keyword, rule.getValue("target").stringValue())
    }.orEmpty()
    require(replacements.map { it.keyword }.distinct().size == replacements.size)
    val blocked = root["blockedSentences"]?.jsonArray?.map {
        it.stringValue().trim().also { sentence -> require(sentence.isNotBlank()) }
    }.orEmpty().distinct()
    return HotwordDictionary(replacements, blocked)
}

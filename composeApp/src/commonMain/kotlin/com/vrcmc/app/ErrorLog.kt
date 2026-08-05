package com.vrcmc.app

import kotlinx.serialization.json.*

data class ErrorLog(val timestamp: Long, val message: String)

internal const val maxErrorLogs = 100

internal fun List<ErrorLog>.toErrorLogsJson(): String = buildJsonArray {
    takeLast(maxErrorLogs).forEach { addJsonObject { put("timestamp", it.timestamp); put("message", it.message) } }
}.toString()

internal fun errorLogsFromJson(value: String): List<ErrorLog> = runCatching {
    if (value.isBlank()) return emptyList()
    Json.parseToJsonElement(value).jsonArray.mapNotNull { item ->
        val obj = item.jsonObject
        val timestamp = obj["timestamp"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
        val message = obj["message"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        ErrorLog(timestamp, message)
    }.takeLast(maxErrorLogs)
}.getOrDefault(emptyList())

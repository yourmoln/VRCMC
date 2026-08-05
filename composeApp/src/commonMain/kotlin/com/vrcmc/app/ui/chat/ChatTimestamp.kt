package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

internal fun shouldShowChatTimestamp(timestamp: Long, previousTimestamp: Long?): Boolean {
    if (previousTimestamp == null) return true
    val elapsed = timestamp - previousTimestamp
    return elapsed < 0 || elapsed >= 10 * 60 * 1_000L
}

internal fun chatTimestampVisibility(timestamps: List<Long>): List<Boolean> {
    var lastDisplayedTimestamp: Long? = null
    return timestamps.map { timestamp ->
        shouldShowChatTimestamp(timestamp, lastDisplayedTimestamp).also { shouldShow ->
            if (shouldShow) lastDisplayedTimestamp = timestamp
        }
    }
}

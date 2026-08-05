package com.vrcmc.app

expect fun currentTimeMillis(): Long

expect fun formatChatTime(
    timestamp: Long,
    now: Long,
    yesterdayLabel: String,
    dayBeforeYesterdayLabel: String,
): String

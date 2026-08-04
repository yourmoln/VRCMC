package com.vrcmc.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
actual fun formatChatTime(timestamp: Long, now: Long, yesterdayLabel: String, dayBeforeYesterdayLabel: String): String =
    formatJvmChatTime(timestamp, now, yesterdayLabel, dayBeforeYesterdayLabel)

private fun formatJvmChatTime(timestamp: Long, now: Long, yesterdayLabel: String, dayBeforeYesterdayLabel: String): String {
    val locale = Locale.getDefault()
    val messageDay = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val dayBeforeYesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -2) }
    val prefix = when {
        messageDay.isSameDay(today) -> ""
        messageDay.isSameDay(yesterday) -> "$yesterdayLabel "
        messageDay.isSameDay(dayBeforeYesterday) -> "$dayBeforeYesterdayLabel "
        else -> return SimpleDateFormat("yyyy-MM-dd HH:mm", locale).format(Date(timestamp))
    }
    return prefix + SimpleDateFormat("HH:mm", locale).format(Date(timestamp))
}

private fun Calendar.isSameDay(other: Calendar): Boolean =
    get(Calendar.ERA) == other.get(Calendar.ERA) &&
        get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
        get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)

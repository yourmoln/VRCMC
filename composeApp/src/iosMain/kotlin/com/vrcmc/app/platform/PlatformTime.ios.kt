package com.vrcmc.app

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

private const val appleReferenceDateOffsetSeconds = 978_307_200.0

actual fun currentTimeMillis(): Long =
    ((NSDate().timeIntervalSinceReferenceDate + appleReferenceDateOffsetSeconds) * 1000.0).toLong()

actual fun formatChatTime(
    timestamp: Long,
    now: Long,
    yesterdayLabel: String,
    dayBeforeYesterdayLabel: String,
): String {
    val messageDate = timestamp.toNSDate()
    val dayFormatter = NSDateFormatter().apply { dateFormat = "yyyy-MM-dd" }
    val messageDay = dayFormatter.stringFromDate(messageDate)
    val today = dayFormatter.stringFromDate(now.toNSDate())
    val yesterday = dayFormatter.stringFromDate((now - MILLIS_PER_DAY).toNSDate())
    val dayBeforeYesterday = dayFormatter.stringFromDate((now - 2 * MILLIS_PER_DAY).toNSDate())
    val prefix =
        when (messageDay) {
            today -> ""
            yesterday -> "$yesterdayLabel "
            dayBeforeYesterday -> "$dayBeforeYesterdayLabel "
            else ->
                return NSDateFormatter()
                    .apply { dateFormat = "yyyy-MM-dd HH:mm" }
                    .stringFromDate(messageDate)
        }
    return prefix + NSDateFormatter().apply { dateFormat = "HH:mm" }.stringFromDate(messageDate)
}

private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1_000L

private fun Long.toNSDate() =
    NSDate(timeIntervalSinceReferenceDate = this / 1000.0 - appleReferenceDateOffsetSeconds)

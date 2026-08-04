package com.vrcmc.app

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

private const val appleReferenceDateOffsetSeconds = 978_307_200.0

actual fun currentTimeMillis(): Long = ((NSDate().timeIntervalSinceReferenceDate + appleReferenceDateOffsetSeconds) * 1000.0).toLong()
actual fun formatChatTime(timestamp: Long): String = NSDateFormatter().apply {
    dateFormat = "yyyy-MM-dd HH:mm"
}.stringFromDate(NSDate(timeIntervalSinceReferenceDate = timestamp / 1000.0 - appleReferenceDateOffsetSeconds))

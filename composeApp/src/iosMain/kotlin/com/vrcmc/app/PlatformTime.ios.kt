package com.vrcmc.app

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
actual fun formatChatTime(timestamp: Long): String = NSDateFormatter().apply {
    dateFormat = "yyyy-MM-dd HH:mm"
}.stringFromDate(NSDate(timeIntervalSince1970 = timestamp / 1000.0))

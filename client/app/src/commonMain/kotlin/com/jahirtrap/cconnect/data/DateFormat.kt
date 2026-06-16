package com.jahirtrap.cconnect.data

expect fun formatDayTime(millis: Long): String
expect fun formatClock(millis: Long): String
expect fun formatDateShort(millis: Long): String
expect fun parseIsoMillis(iso: String): Long?

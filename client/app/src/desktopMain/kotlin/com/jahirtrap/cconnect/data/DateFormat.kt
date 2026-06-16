package com.jahirtrap.cconnect.data

import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale

actual fun formatDayTime(millis: Long): String =
    SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(millis))

actual fun formatClock(millis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))

actual fun formatDateShort(millis: Long): String =
    SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(millis))

actual fun parseIsoMillis(iso: String): Long? =
    runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()

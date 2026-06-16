package com.jahirtrap.cconnect.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val zone: ZoneId get() = ZoneId.systemDefault()

actual fun formatClock(millis: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).withZone(zone)
        .format(Instant.ofEpochMilli(millis))

actual fun formatDay(millis: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).withZone(zone)
        .format(Instant.ofEpochMilli(millis))

actual fun dayIndex(millis: Long): Int =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay().toInt()

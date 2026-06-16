package com.jahirtrap.cconnect.ui

import java.text.DateFormat
import java.util.Calendar
import java.util.Date

fun formatClock(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

fun formatDay(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

fun dayIndex(millis: Long): Int {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    val offset = cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)
    return ((millis + offset) / 86400000L).toInt()
}

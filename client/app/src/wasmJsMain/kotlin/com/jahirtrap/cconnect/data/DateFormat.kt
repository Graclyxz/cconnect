package com.jahirtrap.cconnect.data

actual fun formatDayTime(millis: Long): String = jsDayTime(millis.toDouble())
actual fun formatClock(millis: Long): String = jsClock(millis.toDouble())
actual fun formatDateShort(millis: Long): String = jsDateShort(millis.toDouble())

actual fun parseIsoMillis(iso: String): Long? {
    val ms = jsParseDate(iso)
    return if (ms.isNaN()) null else ms.toLong()
}

private fun jsDayTime(ms: Double): String =
    js("new Date(ms).toLocaleString([], {weekday:'short', hour:'2-digit', minute:'2-digit'})")

private fun jsClock(ms: Double): String =
    js("new Date(ms).toLocaleTimeString([], {hour12:false})")

private fun jsDateShort(ms: Double): String =
    js("new Date(ms).toLocaleString([], {year:'2-digit', month:'2-digit', day:'2-digit', hour:'2-digit', minute:'2-digit'})")

private fun jsParseDate(s: String): Double = js("Date.parse(s)")

package com.jahirtrap.cconnect.ui

actual fun formatClock(millis: Long): String = jsClock(millis.toDouble())

private fun jsClock(ms: Double): String =
    js("new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })")

actual fun formatDay(millis: Long): String = jsDay(millis.toDouble())

private fun jsDay(ms: Double): String =
    js("new Date(ms).toLocaleDateString([], { year: 'numeric', month: 'short', day: 'numeric' })")

actual fun dayIndex(millis: Long): Int = jsDayIndex(millis.toDouble())

private fun jsDayIndex(ms: Double): Int =
    js("((ms - new Date(ms).getTimezoneOffset() * 60000) / 86400000) | 0")

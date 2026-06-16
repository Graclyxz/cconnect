package com.jahirtrap.cconnect.ui

expect fun formatClock(millis: Long): String

expect fun formatDay(millis: Long): String

expect fun dayIndex(millis: Long): Int

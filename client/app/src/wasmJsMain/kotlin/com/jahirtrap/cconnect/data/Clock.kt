package com.jahirtrap.cconnect.data

actual fun nowMillis(): Long = jsNow().toLong()

private fun jsNow(): Double = js("Date.now()")

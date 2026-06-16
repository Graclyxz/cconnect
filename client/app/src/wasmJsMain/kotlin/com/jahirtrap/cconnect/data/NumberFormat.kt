package com.jahirtrap.cconnect.data

actual fun formatDecimal(value: Double, decimals: Int): String = jsToFixed(value, decimals)

private fun jsToFixed(value: Double, decimals: Int): String = js("value.toFixed(decimals)")

package com.jahirtrap.cconnect.data

import java.util.Locale

actual fun formatDecimal(value: Double, decimals: Int): String =
    String.format(Locale.getDefault(), "%.${decimals}f", value)

package com.jahirtrap.cconnect.ui.theme

import androidx.compose.ui.graphics.Color

private val SESSION_COLORS = mapOf(
    "red" to Color(0xFFE5484D),
    "orange" to Color(0xFFF76B15),
    "yellow" to Color(0xFFFFC53D),
    "green" to Color(0xFF30A46C),
    "cyan" to Color(0xFF00A2C7),
    "blue" to Color(0xFF3E63DD),
    "purple" to Color(0xFF8E4EC6),
    "pink" to Color(0xFFE93D82),
)

fun sessionColorOf(name: String?): Color? = name?.let { SESSION_COLORS[it] }

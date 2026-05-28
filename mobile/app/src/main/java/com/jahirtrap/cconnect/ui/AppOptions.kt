package com.jahirtrap.cconnect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.SunMoon
import com.jahirtrap.cconnect.R

// Single source of selectable theme modes and languages ("system" first).
val THEME_MODES = listOf("system", "light", "dark")
val LANGUAGE_TAGS = listOf("", "en", "es") // "" follows the system language

@Composable
fun themeLabel(mode: String): String = when (mode) {
    "light" -> stringResource(R.string.theme_light)
    "dark" -> stringResource(R.string.theme_dark)
    else -> stringResource(R.string.theme_system)
}

fun themeIcon(mode: String): ImageVector = when (mode) {
    "light" -> Lucide.Sun
    "dark" -> Lucide.Moon
    else -> Lucide.SunMoon
}

@Composable
fun languageLabel(tag: String): String = when (tag) {
    "en" -> "English"
    "es" -> "Español"
    else -> stringResource(R.string.language_system)
}

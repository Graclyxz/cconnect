package com.jahirtrap.cconnect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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

@Composable
fun languageLabel(tag: String): String = when (tag) {
    "en" -> "English"
    "es" -> "Español"
    else -> stringResource(R.string.language_system)
}

package com.jahirtrap.cconnect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.SunMoon
import com.jahirtrap.cconnect.resources.Res
import com.jahirtrap.cconnect.resources.*

// Single source of selectable theme modes and languages ("system" first).
val THEME_MODES = listOf("system", "light", "dark")
val LANGUAGE_TAGS = listOf("", "en", "es") // "" follows the system language

@Composable
fun themeLabel(mode: String): String = when (mode) {
    "light" -> stringResource(Res.string.theme_light)
    "dark" -> stringResource(Res.string.theme_dark)
    else -> stringResource(Res.string.theme_system)
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
    else -> stringResource(Res.string.language_system)
}

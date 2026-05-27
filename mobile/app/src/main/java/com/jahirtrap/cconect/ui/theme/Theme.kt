package com.jahirtrap.cconect.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode { SYSTEM, LIGHT, DARK }

fun themeModeOf(value: String): ThemeMode = when (value) {
    "light" -> ThemeMode.LIGHT
    "dark" -> ThemeMode.DARK
    else -> ThemeMode.SYSTEM
}

@Composable
fun CConectTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    accent: Color = accentAt(4),
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current

    // The accent is the ONLY color: from Material You (just its primary) or the palette.
    // The background/surfaces stay flat black/white regardless.
    val accentColor = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
    } else {
        accent
    }

    val base = if (dark) darkColorScheme() else lightColorScheme()
    val background = if (dark) Color(0xFF000000) else Color(0xFFFFFFFF)
    val surface = if (dark) Color(0xFF121212) else Color(0xFFFFFFFF)
    val surfaceVariant = if (dark) Color(0xFF1B1B1B) else Color(0xFFF0F0F0)
    val onBackground = if (dark) Color(0xFFECECEC) else Color(0xFF141414)
    val onSurfaceVariant = if (dark) Color(0xFFB5B5B5) else Color(0xFF4A4A4A)

    val colorScheme = base.copy(
        primary = accentColor,
        onPrimary = if (dark) Color(0xFF000000) else Color(0xFFFFFFFF),
        secondary = accentColor,
        tertiary = accentColor,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onBackground,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerHighest = surfaceVariant,
        surfaceContainerLow = background,
        surfaceContainerLowest = background,
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}

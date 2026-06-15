package com.jahirtrap.cconnect.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

fun themeModeOf(value: String): ThemeMode = when (value) {
    "light" -> ThemeMode.LIGHT
    "dark" -> ThemeMode.DARK
    else -> ThemeMode.SYSTEM
}

@Composable
fun dynamicAccent(themeMode: String): Color = MaterialTheme.colorScheme.primary

@Composable
fun CConnectTheme(
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

    // The accent is the ONLY color: from the palette.
    // The background/surfaces stay flat black/white regardless.
    val accentColor = accent

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

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = ExpressiveShapes,
        typography = ExpressiveTypography,
    ) {
        CompositionLocalProvider(
            LocalPalette provides paletteFor(dark),
            content = content,
        )
    }
}

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val ExpressiveTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Bold),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Bold),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

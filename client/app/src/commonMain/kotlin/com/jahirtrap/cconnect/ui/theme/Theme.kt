package com.jahirtrap.cconnect.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jahirtrap.cconnect.resources.Res
import com.jahirtrap.cconnect.ui.ProvideIsTouch
import com.jahirtrap.cconnect.resources.cconnect_color_bold
import com.jahirtrap.cconnect.resources.cconnect_color_regular
import com.jahirtrap.cconnect.resources.cconnect_flat_bold
import com.jahirtrap.cconnect.resources.cconnect_flat_regular
import org.jetbrains.compose.resources.Font

enum class ThemeMode { SYSTEM, LIGHT, DARK }

fun themeModeOf(value: String): ThemeMode = when (value) {
    "light" -> ThemeMode.LIGHT
    "dark" -> ThemeMode.DARK
    else -> ThemeMode.SYSTEM
}

@Composable
fun dynamicAccent(themeMode: String): Color = systemAccent() ?: MaterialTheme.colorScheme.primary

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CConnectTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    accent: Color = accentAt(4),
    fontStyle: String = "flat",
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    ApplySystemBarsAppearance(dark)

    // The accent is the ONLY color: from the palette.
    // The background/surfaces stay flat black/white regardless.
    val accentColor = if (dynamicColor) (systemAccent() ?: accent) else accent

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

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        typography = ExpressiveTypography.withFamily(appFontFamily(fontStyle)),
    ) {
        CompositionLocalProvider(
            LocalPalette provides paletteFor(dark),
            LocalMonoFontFamily provides appMonoFontFamily(fontStyle),
        ) {
            ProvideIsTouch(content)
        }
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

@Composable
fun appFontFamily(fontStyle: String): FontFamily {
    if (fontStyle != "flat" && fontStyle != "color") return FontFamily.Default
    val color = fontStyle == "color"
    return FontFamily(
        Font(if (color) Res.font.cconnect_color_regular else Res.font.cconnect_flat_regular, FontWeight.Normal),
        Font(if (color) Res.font.cconnect_color_bold else Res.font.cconnect_flat_bold, FontWeight.Bold),
    )
}

private fun Typography.withFamily(family: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

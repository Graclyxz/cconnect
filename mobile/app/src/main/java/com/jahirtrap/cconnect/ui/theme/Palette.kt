package com.jahirtrap.cconnect.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class Palette(
    val red: Color,
    val redBg: Color,
    val green: Color,
    val greenBg: Color,
    val blue: Color,
    val blueBg: Color,
    val yellow: Color,
    val orange: Color,
    val cyan: Color,
    val purple: Color,
    val gray: Color,
)

private val DarkPalette = Palette(
    red = Color(0xFFFF7B72),
    redBg = Color(0x26F85149),
    green = Color(0xFF56D364),
    greenBg = Color(0x2628A745),
    blue = Color(0xFF79C0FF),
    blueBg = Color(0x1A58A6FF),
    yellow = Color(0xFFFFD93D),
    orange = Color(0xFFFFA726),
    cyan = Color(0xFF26C6DA),
    purple = Color(0xFFC084FC),
    gray = Color(0xFF8B949E),
)

private val LightPalette = Palette(
    red = Color(0xFFCF222E),
    redBg = Color(0x33CF222E),
    green = Color(0xFF1A7F37),
    greenBg = Color(0x331A7F37),
    blue = Color(0xFF0969DA),
    blueBg = Color(0x330969DA),
    yellow = Color(0xFFBF8700),
    orange = Color(0xFFD17A1B),
    cyan = Color(0xFF0E7490),
    purple = Color(0xFF8250DF),
    gray = Color(0xFF656D76),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

fun paletteFor(dark: Boolean): Palette = if (dark) DarkPalette else LightPalette

val palette: Palette
    @Composable
    @ReadOnlyComposable
    get() = LocalPalette.current

package com.jahirtrap.cconnect.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import com.jahirtrap.cconnect.resources.Res
import com.jahirtrap.cconnect.resources.cconnect_mono_bold
import com.jahirtrap.cconnect.resources.cconnect_mono_regular

@Composable
fun appMonoFontFamily(fontStyle: String): FontFamily =
    if (fontStyle == "system") FontFamily.Monospace
    else FontFamily(
        Font(Res.font.cconnect_mono_regular, FontWeight.Normal),
        Font(Res.font.cconnect_mono_bold, FontWeight.Bold),
    )

val LocalMonoFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

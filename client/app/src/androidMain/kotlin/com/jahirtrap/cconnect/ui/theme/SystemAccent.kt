package com.jahirtrap.cconnect.ui.theme

import android.os.Build
import androidx.compose.ui.graphics.Color
import com.jahirtrap.cconnect.appContext

actual fun systemAccent(): Color? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        Color(appContext.getColor(android.R.color.system_accent1_500))
    else null

package com.jahirtrap.cconnect.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager

@Composable
fun ClearFocusOnImeHide() {
    if (!LocalIsTouch.current) return
    val focusManager = LocalFocusManager.current
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom == 0) { if (imeBottom == 0) focusManager.clearFocus() }
}

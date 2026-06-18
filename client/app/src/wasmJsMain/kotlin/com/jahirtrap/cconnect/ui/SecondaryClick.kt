package com.jahirtrap.cconnect.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.onClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton

@OptIn(ExperimentalFoundationApi::class)
actual fun Modifier.secondaryClick(action: () -> Unit): Modifier =
    onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary), onClick = action)

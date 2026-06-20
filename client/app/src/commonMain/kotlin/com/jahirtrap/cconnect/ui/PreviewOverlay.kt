package com.jahirtrap.cconnect.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object PreviewOverlay {
    private var count by mutableStateOf(0)
    val open: Boolean get() = count > 0
    fun enter() { count++ }
    fun leave() { count = (count - 1).coerceAtLeast(0) }
}

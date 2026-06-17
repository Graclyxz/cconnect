package com.jahirtrap.cconnect.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

private val handlers = ArrayDeque<(ClipKey) -> Boolean>()

fun dispatchClipboardShortcut(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val key = when {
        event.key == Key.Escape -> ClipKey.Cancel
        event.isCtrlPressed && event.key == Key.C -> ClipKey.Copy
        event.isCtrlPressed && event.key == Key.X -> ClipKey.Cut
        event.isCtrlPressed && event.key == Key.V -> ClipKey.Paste
        else -> return false
    }
    for (handler in handlers.asReversed()) if (handler(key)) return true
    return false
}

@Composable
actual fun ClipboardShortcutHandler(enabled: Boolean, onKey: (ClipKey) -> Boolean) {
    if (!enabled) return
    val current by rememberUpdatedState(onKey)
    DisposableEffect(Unit) {
        val handler: (ClipKey) -> Boolean = { current(it) }
        handlers.addLast(handler)
        onDispose { handlers.remove(handler) }
    }
}

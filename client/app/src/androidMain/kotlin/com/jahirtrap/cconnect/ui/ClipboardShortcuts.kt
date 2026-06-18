package com.jahirtrap.cconnect.ui

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

private val handlers = ArrayDeque<(ClipKey) -> Boolean>()

fun dispatchClipboardShortcut(event: KeyEvent): Boolean {
    if (event.action != KeyEvent.ACTION_DOWN) return false
    val key = when {
        event.keyCode == KeyEvent.KEYCODE_ESCAPE -> ClipKey.Cancel
        event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_C -> ClipKey.Copy
        event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_X -> ClipKey.Cut
        event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_V -> ClipKey.Paste
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

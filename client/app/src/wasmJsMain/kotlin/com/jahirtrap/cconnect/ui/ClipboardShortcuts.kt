package com.jahirtrap.cconnect.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

@Composable
actual fun ClipboardShortcutHandler(enabled: Boolean, onKey: (ClipKey) -> Boolean) {
    if (!enabled) return
    val current by rememberUpdatedState(onKey)
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { event ->
            val ke = event as? KeyboardEvent
            if (ke != null) {
                val ctrl = ke.ctrlKey || ke.metaKey
                val key = when {
                    ke.key == "Escape" -> ClipKey.Cancel
                    ctrl && ke.key.lowercase() == "c" -> ClipKey.Copy
                    ctrl && ke.key.lowercase() == "x" -> ClipKey.Cut
                    ctrl && ke.key.lowercase() == "v" -> ClipKey.Paste
                    else -> null
                }
                if (key != null && current(key)) event.preventDefault()
            }
        }
        document.addEventListener("keydown", listener)
        onDispose { document.removeEventListener("keydown", listener) }
    }
}

package com.jahirtrap.cconnect.ui

import androidx.compose.runtime.Composable

enum class ClipKey { Copy, Cut, Paste, Cancel }

@Composable
expect fun ClipboardShortcutHandler(enabled: Boolean, onKey: (ClipKey) -> Boolean)

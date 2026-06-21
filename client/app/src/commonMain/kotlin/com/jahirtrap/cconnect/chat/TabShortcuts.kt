package com.jahirtrap.cconnect.chat

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

fun tabShortcut(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when {
        event.isCtrlPressed && event.key == Key.Tab && event.isShiftPressed -> { TabsController.selectPrev(); true }
        event.isCtrlPressed && event.key == Key.Tab -> { TabsController.selectNext(); true }
        event.isCtrlPressed && event.key == Key.T -> { TabsController.newTab(); true }
        event.isCtrlPressed && event.key == Key.W -> { TabsController.closeActive(); true }
        event.isAltPressed && event.key == Key.DirectionRight -> { TabsController.selectNext(); true }
        event.isAltPressed && event.key == Key.DirectionLeft -> { TabsController.selectPrev(); true }
        else -> false
    }
}

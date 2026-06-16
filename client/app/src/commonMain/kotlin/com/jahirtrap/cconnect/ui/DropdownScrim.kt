package com.jahirtrap.cconnect.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

@Composable
fun DropdownScrim(onDismiss: () -> Unit) {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    Popup(
        popupPositionProvider = remember {
            object : PopupPositionProvider {
                override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
            }
        },
        properties = PopupProperties(focusable = false),
    ) {
        Box(
            modifier = Modifier
                .size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
    }
}

package com.jahirtrap.cconnect.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = rememberAboveOrBelowPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, modifier = modifier, content = { icon() })
    }
}

@Composable
private fun rememberAboveOrBelowPositionProvider(spacing: Dp = 4.dp): PopupPositionProvider {
    val density = LocalDensity.current
    val spacingPx = with(density) { spacing.roundToPx() }
    val statusBarTopPx = WindowInsets.systemBars.getTop(density)
    return remember(spacingPx, statusBarTopPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val rawX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                val above = anchorBounds.top - popupContentSize.height - spacingPx
                val below = anchorBounds.bottom + spacingPx
                val y = if (above >= statusBarTopPx) above else below
                val x = rawX.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                return IntOffset(x, y)
            }
        }
    }
}

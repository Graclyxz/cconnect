package com.jahirtrap.cconnect.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.horizontalScrollIndicator(state: ScrollState, thickness: Dp = 2.dp): Modifier {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    return drawWithContent {
        drawContent()
        if (state.maxValue <= 0) return@drawWithContent
        val viewport = size.width
        val total = viewport + state.maxValue
        val thumbW = (viewport / total) * viewport
        val thumbX = (state.value.toFloat() / state.maxValue) * (viewport - thumbW)
        val px = thickness.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(thumbX, size.height - px),
            size = Size(thumbW, px),
            cornerRadius = CornerRadius(px / 2, px / 2),
        )
    }
}

@Composable
fun Modifier.verticalScrollIndicator(state: ScrollState, thickness: Dp = 2.dp): Modifier {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    return drawWithContent {
        drawContent()
        if (state.maxValue <= 0) return@drawWithContent
        val viewport = size.height
        val total = viewport + state.maxValue
        val thumbH = (viewport / total) * viewport
        val thumbY = (state.value.toFloat() / state.maxValue) * (viewport - thumbH)
        val px = thickness.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - px, thumbY),
            size = Size(px, thumbH),
            cornerRadius = CornerRadius(px / 2, px / 2),
        )
    }
}

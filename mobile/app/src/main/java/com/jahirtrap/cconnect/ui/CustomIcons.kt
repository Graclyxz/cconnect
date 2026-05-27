package com.jahirtrap.cconnect.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object CustomIcons

val CustomIcons.Stop: ImageVector
    get() {
        cachedStop?.let { return it }
        return ImageVector.Builder(
            name = "Stop",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 3f)
                lineTo(19f, 3f)
                arcTo(2f, 2f, 0f, false, true, 21f, 5f)
                lineTo(21f, 19f)
                arcTo(2f, 2f, 0f, false, true, 19f, 21f)
                lineTo(5f, 21f)
                arcTo(2f, 2f, 0f, false, true, 3f, 19f)
                lineTo(3f, 5f)
                arcTo(2f, 2f, 0f, false, true, 5f, 3f)
                close()
            }
        }.build().also { cachedStop = it }
    }

private var cachedStop: ImageVector? = null

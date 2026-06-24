package com.jahirtrap.cconnect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleLeading: (@Composable () -> Unit)? = null,
    fullWidth: Boolean = true,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val barSides = if (fullWidth) WindowInsetsSides.Horizontal + WindowInsetsSides.Top else WindowInsetsSides.End
    TopAppBar(
        modifier = modifier,
        expandedHeight = 56.dp,
        windowInsets = TopAppBarDefaults.windowInsets.union(WindowInsets.displayCutout).only(barSides),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        navigationIcon = navigationIcon,
        title = {
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(lineHeight = 24.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (subtitleLeading != null) {
                            subtitleLeading()
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        actions = actions,
    )
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, box: Dp = 14.dp, dot: Dp = 8.dp) {
    Box(modifier = modifier.size(box), contentAlignment = Alignment.Center) {
        Box(Modifier.size(dot).clip(CircleShape).background(color))
    }
}

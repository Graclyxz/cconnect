package com.jahirtrap.cconnect.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jahirtrap.cconnect.ui.clickable
import com.jahirtrap.cconnect.ui.theme.palette

@Composable
fun SettingsGroup(
    label: String?,
    labelTrailing: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (label != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                labelTrailing?.invoke()
            }
        } else {
            Spacer(Modifier.height(16.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            content = content,
        )
    }
}

@Composable
fun PreferenceRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    alert: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (alert != null) {
                Text(alert, style = MaterialTheme.typography.bodySmall, color = palette.red.copy(alpha = alpha))
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

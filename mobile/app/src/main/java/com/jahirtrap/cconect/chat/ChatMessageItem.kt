package com.jahirtrap.cconect.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jahirtrap.cconect.R
import com.jahirtrap.cconect.data.ChatMessage
import com.jahirtrap.cconect.data.Role

@Composable
fun ChatMessageItem(message: ChatMessage) {
    when (message.role) {
        Role.USER -> Block(background = MaterialTheme.colorScheme.surface) {
            Text(message.text, color = MaterialTheme.colorScheme.onSurface)
        }

        Role.ASSISTANT -> Block(background = MaterialTheme.colorScheme.surfaceVariant) {
            Text(message.text, color = MaterialTheme.colorScheme.onSurface)
        }

        Role.THINKING -> Collapsible(label = stringResource(R.string.thinking), text = message.text)

        Role.TOOL -> Block(background = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                text = "${stringResource(R.string.tool)}: ${message.toolName ?: ""}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (message.text.isNotBlank()) {
                Text(
                    text = message.text,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Role.ERROR -> Block(background = MaterialTheme.colorScheme.errorContainer) {
            Text(message.text, color = MaterialTheme.colorScheme.onErrorContainer)
        }

        Role.SYSTEM -> Block(background = Color.Transparent) {
            Text(
                message.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Block(background: Color, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        content()
    }
}

@Composable
private fun Collapsible(label: String, text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = if (expanded) "▼ $label" else "▶ $label",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { expanded = !expanded },
        )
        if (expanded) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

package com.jahirtrap.cconnect.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jahirtrap.cconnect.R
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Terminal
import com.jahirtrap.cconnect.data.ChatMessage
import com.jahirtrap.cconnect.data.Role
import com.jahirtrap.cconnect.ui.MarkdownText

private val BIG = 16.dp
private val SMALL = 6.dp

@Composable
fun ChatMessageItem(message: ChatMessage, prevRole: Role? = null, nextRole: Role? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = gapAbove(prevRole, message.role), bottom = if (nextRole == null) BIG else 0.dp),
    ) {
        when (message.role) {
            Role.USER -> Band(MaterialTheme.colorScheme.surfaceVariant) {
                Text(message.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }

            Role.ASSISTANT -> Plain {
                MarkdownText(message.text, modifier = Modifier.fillMaxWidth())
            }

            Role.THINKING -> Collapsible(label = stringResource(R.string.thinking), text = message.text)

            Role.TOOL -> ToolBlock(name = message.toolName, input = message.text)

            Role.TOOL_RESULT -> Collapsible(label = stringResource(R.string.result), text = message.text)

            Role.SUMMARY -> Collapsible(label = stringResource(R.string.summary), text = message.text)

            Role.ERROR -> Band(MaterialTheme.colorScheme.errorContainer) {
                Text(message.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }

            Role.SYSTEM -> Plain {
                Text(message.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun group(role: Role?): Int = when (role) {
    Role.THINKING, Role.TOOL, Role.TOOL_RESULT -> 0
    Role.ASSISTANT -> 1
    Role.USER, Role.ERROR -> 2
    else -> 3
}

private fun gapAbove(prev: Role?, cur: Role): Dp {
    if (prev == null) return BIG
    val a = group(prev)
    val b = group(cur)
    if (a != 0 && b != 0) return BIG
    return if (a == 1 || b == 1) 0.dp else SMALL
}

@Composable
private fun Band(background: Color, content: @Composable () -> Unit) {
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
private fun Plain(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        content()
    }
}

@Composable
private fun Collapsible(label: String, text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Lucide.ChevronDown else Lucide.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
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

@Composable
private fun ToolBlock(name: String?, input: String) {
    var expanded by remember { mutableStateOf(false) }
    val preview = input.replace("\n", " ").trim()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { expanded = !expanded },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Lucide.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "  ${name.orEmpty()}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            if (!expanded && preview.isNotEmpty()) {
                Text(
                    text = "  $preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (expanded && input.isNotBlank()) {
            Text(
                text = input,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

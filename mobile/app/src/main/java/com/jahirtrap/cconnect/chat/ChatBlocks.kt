package com.jahirtrap.cconnect.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jahirtrap.cconnect.R
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleQuestionMark
import com.composables.icons.lucide.FilePen
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.Lightbulb
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SendHorizontal
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.SquareTerminal
import com.composables.icons.lucide.X
import com.jahirtrap.cconnect.ui.CustomIcons
import com.jahirtrap.cconnect.ui.PlayFilled
import com.jahirtrap.cconnect.data.ChatMessage
import com.jahirtrap.cconnect.data.CompactData
import com.jahirtrap.cconnect.data.DiffKind
import com.jahirtrap.cconnect.data.DiffLine
import com.jahirtrap.cconnect.data.InteractionData
import com.jahirtrap.cconnect.data.InteractionOption
import com.jahirtrap.cconnect.data.Role
import com.jahirtrap.cconnect.ui.CodeBlock
import com.jahirtrap.cconnect.ui.MarkdownText
import com.jahirtrap.cconnect.ui.theme.palette

private val BIG = 16.dp
private val SMALL = 6.dp

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    prevRole: Role? = null,
    nextRole: Role? = null,
    running: Boolean = false,
    onAnswer: ((String, String, String?) -> Unit)? = null,
    onSharedLink: ((String, String) -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = gapAbove(prevRole, message.role), bottom = bottomGap(message.role, nextRole)),
    ) {
        when (message.role) {
            Role.USER -> Band(MaterialTheme.colorScheme.surfaceVariant) {
                Text(message.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }

            Role.ASSISTANT -> Plain {
                MarkdownText(message.text, modifier = Modifier.fillMaxWidth(), selectable = false, onSharedLink = onSharedLink)
            }

            Role.THINKING -> Collapsible(label = stringResource(R.string.thinking), text = message.text, icon = Lucide.Lightbulb, labelOnly = message.labelOnly, running = running)

            Role.WORKING -> Collapsible(label = stringResource(R.string.working), text = "", icon = Lucide.Bot, labelOnly = true, running = running)

            Role.TOOL -> ToolBlock(name = message.toolName, input = message.text, result = message.result, running = running)

            Role.TOOL_RESULT -> Collapsible(label = stringResource(R.string.result), text = message.text, labelOnly = message.labelOnly)

            Role.SUMMARY -> Collapsible(label = stringResource(R.string.summary), text = message.text)

            Role.INTERACTION -> message.interaction?.let {
                InteractionBlock(data = it, toolName = message.toolName, input = message.text, onAnswer = onAnswer)
            }

            Role.FILE_CHANGE -> FileChangeBlock(path = message.path.orEmpty(), diffLines = message.diffLines.orEmpty(), labelOnly = message.labelOnly)

            Role.COMPACT -> message.compact?.let { CompactBlock(it) }

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
    Role.THINKING, Role.TOOL, Role.TOOL_RESULT, Role.INTERACTION, Role.FILE_CHANGE, Role.COMPACT -> 0
    Role.ASSISTANT -> 1
    Role.USER, Role.ERROR -> 2
    else -> 3
}

private fun bottomGap(cur: Role, next: Role?): Dp = when {
    next != null -> 0.dp
    cur == Role.ERROR -> 0.dp
    else -> BIG
}

private fun gapAbove(prev: Role?, cur: Role): Dp {
    if (prev == null) return BIG
    if (prev == cur) return 0.dp
    if (cur == Role.ERROR || prev == Role.ERROR) {
        val other = if (cur == Role.ERROR) prev else cur
        return if (other == Role.USER || other == Role.ASSISTANT) 0.dp else SMALL
    }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Collapsible(label: String, text: String, icon: ImageVector? = null, labelOnly: Boolean = false, running: Boolean = false) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().then(if (labelOnly) Modifier else Modifier.clickable { expanded = !expanded }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (running) {
                LoadingIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary)
                if (!labelOnly) Spacer(Modifier.size(2.dp))
            }
            if (!labelOnly) {
                Icon(
                    imageVector = if (expanded) Lucide.ChevronDown else Lucide.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (expanded && !labelOnly) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolBlock(name: String?, input: String, result: String? = null, running: Boolean = false) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val preview = input.replace("\n", " ").trim()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Lucide.SquareTerminal,
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
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (running) {
                LoadingIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(2.dp))
            }
            Icon(
                imageVector = if (expanded) Lucide.ChevronDown else Lucide.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        if (expanded) {
            if (input.isNotBlank()) {
                Text(
                    text = input,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (!result.isNullOrBlank()) {
                Spacer(Modifier.size(6.dp))
                CodeBlock(result, MaterialTheme.colorScheme.surfaceContainerHigh, stringResource(R.string.result))
            }
        }
    }
}

@Composable
private fun FileChangeBlock(path: String, diffLines: List<DiffLine>, labelOnly: Boolean = false) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().then(if (labelOnly) Modifier else Modifier.clickable { expanded = !expanded }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Lucide.FilePen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "  $path",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!labelOnly) {
                Icon(
                    imageVector = if (expanded) Lucide.ChevronDown else Lucide.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (expanded && !labelOnly && diffLines.isNotEmpty()) {
            val bg = MaterialTheme.colorScheme.surfaceContainerHigh
            val defaultFg = MaterialTheme.colorScheme.onSurfaceVariant
            val scroll = rememberScrollState()
            Surface(
                color = bg,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Column(
                    modifier = Modifier
                        .horizontalScroll(scroll)
                        .padding(vertical = 4.dp),
                ) {
                    diffLines.forEach { line ->
                        val (fg, lineBg, prefix) = diffStyleFor(line.kind, defaultFg)
                        Text(
                            text = if (line.text.isEmpty() && prefix.isEmpty()) " " else "$prefix${line.text}",
                            color = fg,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .background(lineBg)
                                .padding(horizontal = 10.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactBlock(data: CompactData) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val hasSummary = data.summary.isNotBlank()
    val triggerLabel = when (data.trigger) {
        "manual" -> stringResource(R.string.compact_manual)
        "auto" -> stringResource(R.string.compact_auto)
        else -> null
    }
    val stats = buildString {
        if (triggerLabel != null) append(triggerLabel)
        val pre = data.preTokens
        val post = data.postTokens
        if (pre != null && post != null) {
            if (isNotEmpty()) append(" • ")
            append("${fmtTokens(pre)} → ${fmtTokens(post)}")
        }
    }.ifBlank { null }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = hasSummary) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.compacted), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            if (stats != null) {
                Text(stats, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (hasSummary) {
                Spacer(Modifier.size(6.dp))
                Icon(if (expanded) Lucide.ChevronDown else Lucide.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        if (expanded && hasSummary) {
            MarkdownText(data.summary, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), selectable = false)
        }
    }
}

private fun fmtTokens(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000 -> "${n / 1_000}k"
    else -> n.toString()
}

@Composable
private fun diffStyleFor(kind: DiffKind, defaultFg: Color): Triple<Color, Color, String> {
    val p = palette
    return when (kind) {
        DiffKind.HEADER -> Triple(p.gray, Color.Transparent, "")
        DiffKind.HUNK -> Triple(p.blue, p.blueBg, "")
        DiffKind.ADD -> Triple(p.green, p.greenBg, "+")
        DiffKind.DEL -> Triple(p.red, p.redBg, "-")
        DiffKind.CTX -> Triple(defaultFg, Color.Transparent, " ")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InteractionBlock(
    data: InteractionData,
    toolName: String?,
    input: String,
    onAnswer: ((String, String, String?) -> Unit)?,
) {
    var freeText by remember { mutableStateOf("") }
    val resolved = data.resolved
    val title = data.title ?: toolName ?: stringResource(R.string.permission_title)
    val headerIcon = if (data.kind == "question") Lucide.CircleQuestionMark else Lucide.Shield
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                headerIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }
        if (input.isNotBlank()) {
            Text(
                text = input,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (resolved == null) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                data.options.forEach { opt ->
                    OutlinedButton(
                        onClick = { onAnswer?.invoke(data.requestId, opt.id, null) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(optionLabel(opt), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (data.freeText != "off") {
                Spacer(Modifier.height(8.dp))
                TextInputRow(
                    value = freeText,
                    onValueChange = { freeText = it },
                    placeholder = stringResource(R.string.interaction_text_hint),
                    onSend = { onAnswer?.invoke(data.requestId, "", freeText.trim()) },
                )
            }
        } else {
            val chosen = data.options.firstOrNull { it.id == resolved }
            val label = chosen?.let { optionLabel(it) }.orEmpty()
            val display = label.ifBlank { data.resolvedText.orEmpty() }
            val extra = if (label.isNotBlank()) data.resolvedText else null
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(CustomIcons.PlayFilled, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(10.dp))
                Spacer(Modifier.size(6.dp))
                Text(display, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!extra.isNullOrBlank()) {
                Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun optionLabel(opt: InteractionOption): String {
    if (!opt.label.isNullOrBlank()) return opt.label
    return when (opt.id) {
        "allow" -> stringResource(R.string.permission_allow)
        "allow_always" -> stringResource(R.string.permission_allow_always)
        "deny" -> stringResource(R.string.permission_deny)
        "different" -> stringResource(R.string.permission_different)
        else -> opt.id
    }
}

@Composable
private fun TextInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSend: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = 3,
            modifier = Modifier
                .weight(1f)
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inner()
            },
        )
        Spacer(Modifier.size(4.dp))
        IconButton(onClick = onSend, enabled = value.isNotBlank()) {
            Icon(Lucide.SendHorizontal, contentDescription = stringResource(R.string.send), modifier = Modifier.size(20.dp))
        }
        if (onCancel != null) {
            IconButton(onClick = onCancel) {
                Icon(Lucide.X, contentDescription = stringResource(R.string.cancel), modifier = Modifier.size(20.dp))
            }
        }
    }
}

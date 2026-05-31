package com.jahirtrap.cconnect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.jahirtrap.cconnect.data.remote.Backend
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.footnotes.FootnoteDefinition
import org.commonmark.ext.footnotes.FootnoteReference
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.footnotes.InlineFootnote
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.ins.Ins
import org.commonmark.ext.ins.InsExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak
import org.commonmark.node.Text as CmText
import org.commonmark.parser.Parser

private val parser: Parser = Parser.builder()
    .extensions(
        listOf(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            TaskListItemsExtension.create(),
            FootnotesExtension.create(),
            AutolinkExtension.create(),
            InsExtension.create(),
        )
    )
    .build()

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    selectable: Boolean = true,
    onSharedLink: ((url: String, filename: String) -> Unit)? = null,
) {
    val root = remember(markdown) { parser.parse(markdown) }
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val linkColor = MaterialTheme.colorScheme.primary
    val defaultHandler = LocalUriHandler.current
    val uriHandler = remember(onSharedLink, defaultHandler) {
        if (onSharedLink == null) defaultHandler
        else object : UriHandler {
            override fun openUri(uri: String) {
                val prefix = Backend.baseUrl + "/shared/"
                if (uri.startsWith(prefix)) {
                    val raw = uri.substring(prefix.length).substringBefore('?').substringBefore('#')
                    val filename = Uri.decode(raw.substringAfterLast('/')) ?: raw
                    onSharedLink(uri, filename)
                } else defaultHandler.openUri(uri)
            }
        }
    }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        if (selectable) {
            SelectionContainer(modifier = modifier) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Blocks(root, codeBg, linkColor, depth = 0)
                }
            }
        } else {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Blocks(root, codeBg, linkColor, depth = 0)
            }
        }
    }
}

@Composable
private fun Blocks(parent: Node, codeBg: Color, linkColor: Color, depth: Int) {
    var child = parent.firstChild
    while (child != null) {
        val node = child
        if (node is HtmlBlock && node.literal.contains("<details", ignoreCase = true)) {
            val summary = SUMMARY_RE.find(node.literal)?.groupValues?.get(1)?.trim()?.ifBlank { null } ?: "Details"
            val inner = ArrayList<Node>()
            var n = node.next
            while (n != null && !(n is HtmlBlock && n.literal.contains("</details>", ignoreCase = true))) {
                inner.add(n)
                n = n.next
            }
            DetailsBlock(summary, inner, codeBg, linkColor, depth)
            child = n?.next
            continue
        }
        RenderNode(node, codeBg, linkColor, depth)
        child = child.next
    }
}

@Composable
private fun RenderNode(node: Node, codeBg: Color, linkColor: Color, depth: Int) {
    when (node) {
        is Heading -> MdText(
            text = inline(node, linkColor, codeBg),
            codeBg = codeBg,
            style = (when (node.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }).copy(fontWeight = FontWeight.Bold),
        )

        is Paragraph -> MdText(
            text = inline(node, linkColor, codeBg),
            codeBg = codeBg,
            style = MaterialTheme.typography.bodyMedium,
        )

        is FencedCodeBlock -> CodeBlock(node.literal, codeBg, node.info?.trim()?.lowercase().orEmpty())
        is IndentedCodeBlock -> CodeBlock(node.literal, codeBg, "")
        is BulletList -> ListBlock(node, ordered = false, start = 1, codeBg = codeBg, linkColor = linkColor, depth = depth)
        is OrderedList -> ListBlock(node, ordered = true, start = node.markerStartNumber ?: 1, codeBg = codeBg, linkColor = linkColor, depth = depth)
        is BlockQuote -> QuoteBlock(node, codeBg, linkColor, depth)
        is TableBlock -> TableView(node, codeBg, linkColor)
        is ThematicBreak -> HorizontalDivider()
        is FootnoteDefinition -> Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "[${node.label}] ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Column { Blocks(node, codeBg, linkColor, depth) }
        }
    }
}

@Composable
private fun QuoteBlock(node: Node, codeBg: Color, linkColor: Color, depth: Int) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        )
        Column(
            modifier = Modifier.padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Blocks(node, codeBg, linkColor, depth)
        }
    }
}

@Composable
private fun DetailsBlock(summary: String, inner: List<Node>, codeBg: Color, linkColor: Color, depth: Int) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = CustomIcons.PlayFilled,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(10.dp).rotate(if (expanded) 90f else 0f),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                inner.forEach { RenderNode(it, codeBg, linkColor, depth) }
            }
        }
    }
}

@Composable
private fun TableView(table: Node, codeBg: Color, linkColor: Color) {
    val scroll = rememberScrollState()
    val cols = tableColumnCount(table)
    val cellWidth = 140.dp
    val totalWidth = cellWidth * cols.coerceAtLeast(1)
    Column(modifier = Modifier.fillMaxWidth().horizontalScrollIndicator(scroll).horizontalScroll(scroll)) {
        var section = table.firstChild
        while (section != null) {
            val header = section is TableHead
            var row = section.firstChild
            while (row != null) {
                Row {
                    var cell = row.firstChild
                    while (cell != null) {
                        if (cell is TableCell) {
                            MdText(
                                text = inline(cell, linkColor, codeBg),
                                codeBg = codeBg,
                                modifier = Modifier.width(cellWidth).padding(6.dp),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                                ),
                            )
                        }
                        cell = cell.next
                    }
                }
                HorizontalDivider(modifier = Modifier.width(totalWidth))
                row = row.next
            }
            section = section.next
        }
    }
}

private fun tableColumnCount(table: Node): Int {
    var section = table.firstChild
    while (section != null) {
        val row = section.firstChild
        if (row != null) {
            var count = 0
            var cell = row.firstChild
            while (cell != null) {
                if (cell is TableCell) count++
                cell = cell.next
            }
            if (count > 0) return count
        }
        section = section.next
    }
    return 0
}

@Composable
private fun ListBlock(list: Node, ordered: Boolean, start: Int, codeBg: Color, linkColor: Color, depth: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var item = list.firstChild
        var index = start
        while (item != null) {
            val marker = taskMarker(item)
            val bullet = when {
                marker != null -> if (marker) "☑  " else "☐  "
                ordered -> "$index. "
                else -> "${unorderedBullet(depth)}  "
            }
            Row {
                Text(text = bullet, style = MaterialTheme.typography.bodyMedium)
                Column { Blocks(item, codeBg, linkColor, depth + 1) }
            }
            item = item.next
            index++
        }
    }
}

private fun unorderedBullet(depth: Int): String = when (depth) {
    0 -> "•"
    1 -> "◦"
    else -> "▪"
}

// The task-list marker is a direct child of the ListItem (sibling of the paragraph).
private fun taskMarker(item: Node): Boolean? {
    var child = item.firstChild
    while (child != null) {
        if (child is TaskListItemMarker) return child.isChecked
        child = child.next
    }
    return null
}

@Composable
internal fun CodeBlock(code: String, bg: Color, lang: String) {
    val scroll = rememberScrollState()
    Surface(color = bg, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            CodeBlockHeader(lang, code)
            Text(
                text = code.trimEnd('\n'),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .horizontalScrollIndicator(scroll)
                    .horizontalScroll(scroll)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun CodeBlockHeader(lang: String, code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1000)
            copied = false
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 2.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = lang.ifBlank { "code" },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = {
                clipboard.setText(AnnotatedString(code.trimEnd('\n')))
                copied = true
            },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = if (copied) Lucide.Check else Lucide.Copy,
                contentDescription = "Copy",
                tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun inline(node: Node, linkColor: Color, codeBg: Color): AnnotatedString =
    buildAnnotatedString { appendInline(node, linkColor, codeBg) }

private fun AnnotatedString.Builder.appendInline(node: Node, linkColor: Color, codeBg: Color) {
    var child = node.firstChild
    while (child != null) {
        when (val n = child) {
            is CmText -> append(n.literal)
            is Emphasis -> styled(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(n, linkColor, codeBg) }
            is StrongEmphasis -> styled(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(n, linkColor, codeBg) }
            is Code -> {
                val start = length
                styled(SpanStyle(fontFamily = FontFamily.Monospace)) { append(n.literal) }
                addStringAnnotation(INLINE_CODE_TAG, n.literal, start, length)
            }
            is Link -> {
                val url = n.destination?.trim().orEmpty()
                val style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                if (url.isNotEmpty()) {
                    withLink(LinkAnnotation.Url(url = url, styles = TextLinkStyles(style = style))) {
                        appendInline(n, linkColor, codeBg)
                    }
                } else styled(style) { appendInline(n, linkColor, codeBg) }
            }
            is Strikethrough -> styled(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendInline(n, linkColor, codeBg) }
            is Ins -> styled(SpanStyle(textDecoration = TextDecoration.Underline)) { appendInline(n, linkColor, codeBg) }
            is Image -> {
                append("🖼 ")
                styled(SpanStyle(color = linkColor, fontStyle = FontStyle.Italic)) { appendInline(n, linkColor, codeBg) }
            }
            is FootnoteReference -> styled(SpanStyle(baselineShift = BaselineShift.Superscript, color = linkColor)) { append("[${n.label}]") }
            is InlineFootnote -> styled(SpanStyle(baselineShift = BaselineShift.Superscript, color = linkColor)) {
                append("[")
                appendInline(n, linkColor, codeBg)
                append("]")
            }
            is SoftLineBreak -> append(" ")
            is HardLineBreak -> append("\n")
            is HtmlInline -> if (n.literal.equals("<br>", true) || n.literal.equals("<br/>", true) || n.literal.equals("<br />", true)) append("\n")
            is TaskListItemMarker -> Unit
            else -> appendInline(n, linkColor, codeBg)
        }
        child = child.next
    }
}

private inline fun AnnotatedString.Builder.styled(style: SpanStyle, block: AnnotatedString.Builder.() -> Unit) {
    pushStyle(style)
    block()
    pop()
}

private val SUMMARY_RE = Regex("<summary>(.*?)</summary>", RegexOption.IGNORE_CASE)
private const val INLINE_CODE_TAG = "inline_code"

@Composable
private fun MdText(
    text: androidx.compose.ui.text.AnnotatedString,
    codeBg: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier.drawBehind {
            val result = layout ?: return@drawBehind
            val padX = 3.dp.toPx()
            val padY = 1.dp.toPx()
            val radius = 6.dp.toPx()
            text.getStringAnnotations(INLINE_CODE_TAG, 0, text.length).forEach { ann ->
                val firstLine = result.getLineForOffset(ann.start)
                val lastLine = result.getLineForOffset((ann.end - 1).coerceAtLeast(ann.start))
                for (line in firstLine..lastLine) {
                    val startOffset = if (line == firstLine) ann.start else result.getLineStart(line)
                    val endOffset = if (line == lastLine) ann.end else result.getLineEnd(line, visibleEnd = true)
                    if (endOffset <= startOffset) continue
                    val firstBox = result.getBoundingBox(startOffset)
                    val lastBox = result.getBoundingBox((endOffset - 1).coerceAtLeast(startOffset))
                    val left = minOf(firstBox.left, lastBox.left) - padX
                    val right = maxOf(firstBox.right, lastBox.right) + padX
                    val top = result.getLineTop(line) + padY
                    val bottom = result.getLineBottom(line) - padY
                    drawRoundRect(
                        color = codeBg,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
            }
        },
    )
}

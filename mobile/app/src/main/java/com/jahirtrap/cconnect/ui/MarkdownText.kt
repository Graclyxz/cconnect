package com.jahirtrap.cconnect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val root = remember(markdown) { parser.parse(markdown) }
    val codeBg = MaterialTheme.colorScheme.surface
    val linkColor = MaterialTheme.colorScheme.primary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Blocks(root, codeBg, linkColor, depth = 0)
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
        is Heading -> Text(
            text = inline(node, linkColor, codeBg),
            style = when (node.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            },
            fontWeight = FontWeight.Bold,
        )

        is Paragraph -> Text(
            text = inline(node, linkColor, codeBg),
            style = MaterialTheme.typography.bodyMedium,
        )

        is FencedCodeBlock -> {
            val lang = node.info?.trim()?.lowercase().orEmpty()
            if (lang == "diff" || lang == "patch" || looksLikeDiff(node.literal)) DiffBlock(node.literal, codeBg)
            else CodeBlock(node.literal, codeBg)
        }
        is IndentedCodeBlock -> CodeBlock(node.literal, codeBg)
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
        Text(
            text = if (expanded) "▼ $summary" else "▶ $summary",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        )
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
    Column(modifier = Modifier.fillMaxWidth().horizontalScroll(scroll)) {
        var section = table.firstChild
        while (section != null) {
            val header = section is TableHead
            var row = section.firstChild
            while (row != null) {
                Row {
                    var cell = row.firstChild
                    while (cell != null) {
                        if (cell is TableCell) {
                            Text(
                                text = inline(cell, linkColor, codeBg),
                                modifier = Modifier.width(cellWidth).padding(6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
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
private fun CodeBlock(code: String, bg: Color) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = code.trimEnd('\n'),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
        )
    }
}

private fun looksLikeDiff(code: String): Boolean {
    val lines = code.lines()
    if (lines.size < 2) return false
    val hasHunk = lines.any { it.startsWith("@@") }
    val changes = lines.count {
        (it.startsWith("+") && !it.startsWith("+++")) || (it.startsWith("-") && !it.startsWith("---"))
    }
    val hasFileHeaders = lines.any { it.startsWith("--- ") } && lines.any { it.startsWith("+++ ") }
    return (hasHunk && changes >= 2) || hasFileHeaders
}

private fun diffLineColors(line: String, defaultFg: Color): Pair<Color, Color> = when {
    line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") || line.startsWith("index ") ->
        Color(0xFF8B949E) to Color.Transparent
    line.startsWith("@@") -> Color(0xFF79C0FF) to Color(0x1A58A6FF)
    line.startsWith("+") -> Color(0xFF56D364) to Color(0x2628A745)
    line.startsWith("-") -> Color(0xFFFF7B72) to Color(0x26F85149)
    else -> defaultFg to Color.Transparent
}

@Composable
private fun DiffBlock(code: String, bg: Color) {
    val defaultFg = MaterialTheme.colorScheme.onSurfaceVariant
    val scroll = rememberScrollState()
    Surface(color = bg, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.horizontalScroll(scroll).padding(vertical = 6.dp)) {
            code.trimEnd('\n').lines().forEach { line ->
                val (fg, lineBg) = diffLineColors(line, defaultFg)
                Text(
                    text = if (line.isEmpty()) " " else line,
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

private fun inline(node: Node, linkColor: Color, codeBg: Color): AnnotatedString =
    buildAnnotatedString { appendInline(node, linkColor, codeBg) }

private fun AnnotatedString.Builder.appendInline(node: Node, linkColor: Color, codeBg: Color) {
    var child = node.firstChild
    while (child != null) {
        when (val n = child) {
            is CmText -> append(n.literal)
            is Emphasis -> styled(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(n, linkColor, codeBg) }
            is StrongEmphasis -> styled(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(n, linkColor, codeBg) }
            is Code -> styled(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(n.literal) }
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

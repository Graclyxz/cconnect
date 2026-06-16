package com.jahirtrap.cconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp

@Composable
actual fun MarkdownText(
    markdown: String,
    modifier: Modifier,
    selectable: Boolean,
    onSharedLink: ((url: String, filename: String) -> Unit)?,
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val linkColor = MaterialTheme.colorScheme.primary
    if (selectable) {
        SelectionContainer(modifier = modifier) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                blocks.forEach { RenderBlock(it, codeBg, linkColor) }
            }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            blocks.forEach { RenderBlock(it, codeBg, linkColor) }
        }
    }
}

@Composable
private fun RenderBlock(block: MdBlock, codeBg: Color, linkColor: Color) {
    when (block) {
        is MdBlock.Heading -> Text(
            text = inlineMd(block.text, linkColor),
            style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.fillMaxWidth(),
        )

        is MdBlock.Paragraph -> Text(
            text = inlineMd(block.text, linkColor),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )

        is MdBlock.Code -> CodeBlock(block.code, codeBg, block.lang)

        is MdBlock.Bullet -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            block.items.forEach { item ->
                Text(
                    text = inlineMd("•  $item", linkColor),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        is MdBlock.Quote -> Text(
            text = inlineMd(block.text, linkColor),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
        )

        MdBlock.Rule -> HorizontalDivider()
    }
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Code(val code: String, val lang: String) : MdBlock
    data class Bullet(val items: List<String>) : MdBlock
    data class Quote(val text: String) : MdBlock
    object Rule : MdBlock
}

private val ORDERED = Regex("^(\\d+)\\. (.*)")

private fun parseBlocks(md: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val lines = md.replace("\r\n", "\n").split("\n")
    val para = StringBuilder()
    fun flushPara() {
        if (para.isNotBlank()) out.add(MdBlock.Paragraph(para.trim().toString()))
        para.setLength(0)
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                flushPara()
                val lang = trimmed.removePrefix("```").trim()
                val sb = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) { sb.append(lines[i]).append('\n'); i++ }
                out.add(MdBlock.Code(sb.toString(), lang))
                i++
            }

            trimmed.startsWith("#") -> {
                flushPara()
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                out.add(MdBlock.Heading(level, trimmed.drop(level).trim()))
                i++
            }

            trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                flushPara(); out.add(MdBlock.Rule); i++
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                flushPara()
                val items = ArrayList<String>()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")) { items.add(t.drop(2).trim()); i++ } else break
                }
                out.add(MdBlock.Bullet(items))
            }

            ORDERED.containsMatchIn(trimmed) -> {
                flushPara()
                val items = ArrayList<String>()
                while (i < lines.size) {
                    val m = ORDERED.find(lines[i].trim())
                    if (m != null) { items.add(m.groupValues[2].trim()); i++ } else break
                }
                out.add(MdBlock.Bullet(items))
            }

            trimmed.startsWith(">") -> {
                flushPara()
                val sb = StringBuilder()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    sb.append(lines[i].trim().removePrefix(">").trim()).append(' '); i++
                }
                out.add(MdBlock.Quote(sb.trim().toString()))
            }

            trimmed.isEmpty() -> { flushPara(); i++ }

            else -> { para.append(line).append(' '); i++ }
        }
    }
    flushPara()
    return out
}

private fun inlineMd(text: String, linkColor: Color): AnnotatedString =
    buildAnnotatedString { appendInlineMd(text, linkColor) }

private fun AnnotatedString.Builder.appendInlineMd(text: String, linkColor: Color) {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end < 0) { append(text.substring(i)); i = text.length } else {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); appendInlineMd(text.substring(i + 2, end), linkColor); pop(); i = end + 2
                }
            }

            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end < 0) { append(text.substring(i)); i = text.length } else {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)); appendInlineMd(text.substring(i + 2, end), linkColor); pop(); i = end + 2
                }
            }

            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end < 0) { append(text.substring(i)); i = text.length } else {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace)); append(text.substring(i + 1, end)); pop(); i = end + 1
                }
            }

            text[i] == '*' || text[i] == '_' -> {
                val ch = text[i]
                val end = text.indexOf(ch, i + 1)
                if (end < 0) { append(ch); i++ } else {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); appendInlineMd(text.substring(i + 1, end), linkColor); pop(); i = end + 1
                }
            }

            text[i] == '[' -> {
                val close = text.indexOf(']', i)
                if (close >= 0 && close + 1 < text.length && text[close + 1] == '(') {
                    val paren = text.indexOf(')', close + 2)
                    if (paren >= 0) {
                        val label = text.substring(i + 1, close)
                        val url = text.substring(close + 2, paren)
                        withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) {
                            appendInlineMd(label, linkColor)
                        }
                        i = paren + 1
                    } else { append(text[i]); i++ }
                } else { append(text[i]); i++ }
            }

            else -> { append(text[i]); i++ }
        }
    }
}

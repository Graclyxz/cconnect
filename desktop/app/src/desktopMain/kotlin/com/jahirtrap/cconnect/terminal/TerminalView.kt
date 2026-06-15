package com.jahirtrap.cconnect.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

@Composable
fun Terminal(
    terminalEmulator: TerminalEmulator,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Black,
    foregroundColor: Color = Color.White,
    selectionBackgroundColor: Color = Color.White,
    selectionForegroundColor: Color = Color.Black,
    keyboardEnabled: Boolean = true,
    showSoftKeyboard: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val style = remember(foregroundColor) {
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = foregroundColor)
    }
    val measurer = rememberTextMeasurer()
    val cell = remember(style) { measurer.measure("M", style).size }
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    LaunchedEffect(showSoftKeyboard) { if (showSoftKeyboard) runCatching { focusRequester.requestFocus() } }

    BoxWithConstraints(
        modifier = modifier
            .background(backgroundColor)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!keyboardEnabled) return@onPreviewKeyEvent false
                keyToBytes(event)?.let { terminalEmulator.send(it); true } ?: false
            },
    ) {
        val cols = (constraints.maxWidth / cell.width).coerceAtLeast(1)
        val rows = (constraints.maxHeight / cell.height).coerceAtLeast(1)
        LaunchedEffect(cols, rows) { terminalEmulator.resize(cols, rows) }

        val frame = terminalEmulator.frame
        val snapshot = remember(frame) { terminalEmulator.snapshot() }
        LaunchedEffect(snapshot.lines.size, frame) {
            if (snapshot.lines.isNotEmpty()) listState.scrollToItem(snapshot.lines.lastIndex)
        }
        val rowHeight = with(density) { cell.height.toDp() }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(snapshot.lines.size) { index ->
                val cursorCol = if (index == snapshot.cursorRow) snapshot.cursorCol else -1
                Text(
                    text = buildLine(snapshot.lines[index], cursorCol, backgroundColor, foregroundColor, selectionBackgroundColor, selectionForegroundColor),
                    style = style,
                    softWrap = false,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun buildLine(
    cells: List<TerminalCell>,
    cursorCol: Int,
    backgroundColor: Color,
    foregroundColor: Color,
    selectionBackgroundColor: Color,
    selectionForegroundColor: Color,
): AnnotatedString {
    var end = cells.size
    while (end > 0) {
        val c = cells[end - 1]
        if (end - 1 != cursorCol && c.char == ' ' && c.bg == backgroundColor) end-- else break
    }
    if (end == 0 && cursorCol < 0) return AnnotatedString(" ")
    return buildAnnotatedString {
        var i = 0
        val limit = maxOf(end, if (cursorCol >= 0) cursorCol + 1 else 0)
        while (i < limit) {
            val cell = cells.getOrNull(i) ?: TerminalCell(' ', foregroundColor, backgroundColor, false)
            val cursor = i == cursorCol
            val fg = if (cursor) selectionForegroundColor else cell.fg
            val bg = if (cursor) selectionBackgroundColor else cell.bg
            val bold = cell.bold
            var run = cell.char.toString()
            var j = i + 1
            if (!cursor) {
                while (j < limit && j != cursorCol) {
                    val next = cells.getOrNull(j) ?: break
                    if (next.fg == fg && next.bg == bg && next.bold == bold) {
                        run += next.char
                        j++
                    } else break
                }
            }
            withStyle(SpanStyle(color = fg, background = bg, fontWeight = if (bold) FontWeight.Bold else null)) {
                append(run)
            }
            i = j
        }
    }
}

private fun keyToBytes(event: KeyEvent): ByteArray? {
    if (event.type != KeyEventType.KeyDown) return null
    when (event.key) {
        Key.Enter, Key.NumPadEnter -> return byteArrayOf(0x0D)
        Key.Backspace -> return byteArrayOf(0x7F)
        Key.Tab -> return byteArrayOf(0x09)
        Key.Escape -> return byteArrayOf(0x1B)
        Key.DirectionUp -> return "[A".toByteArray()
        Key.DirectionDown -> return "[B".toByteArray()
        Key.DirectionRight -> return "[C".toByteArray()
        Key.DirectionLeft -> return "[D".toByteArray()
        Key.MoveHome -> return "[H".toByteArray()
        Key.MoveEnd -> return "[F".toByteArray()
        Key.PageUp -> return "[5~".toByteArray()
        Key.PageDown -> return "[6~".toByteArray()
        Key.Delete -> return "[3~".toByteArray()
    }
    val awt = event.nativeKeyEvent as? java.awt.event.KeyEvent ?: return null
    val ch = awt.keyChar
    if (ch == java.awt.event.KeyEvent.CHAR_UNDEFINED) return null
    val code = ch.code
    return when {
        event.isCtrlPressed && code in 1..31 -> byteArrayOf(code.toByte())
        event.isCtrlPressed && ch.uppercaseChar() in 'A'..'Z' -> byteArrayOf((ch.uppercaseChar().code - 64).toByte())
        code >= 0x20 -> ch.toString().toByteArray(Charsets.UTF_8)
        else -> null
    }
}

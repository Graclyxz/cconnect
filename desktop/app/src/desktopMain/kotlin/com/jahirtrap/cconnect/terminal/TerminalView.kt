package com.jahirtrap.cconnect.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.delay
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
    var input by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    BoxWithConstraints(
        modifier = modifier
            .background(backgroundColor)
            .pointerInput(Unit) { detectTapGestures { runCatching { focusRequester.requestFocus() } } },
    ) {
        val cols = (constraints.maxWidth / cell.width).coerceAtLeast(1)
        val rows = (constraints.maxHeight / cell.height).coerceAtLeast(1)
        LaunchedEffect(cols, rows) { terminalEmulator.resize(cols, rows) }

        val frame = terminalEmulator.frame
        val snapshot = remember(frame) { terminalEmulator.snapshot() }
        LaunchedEffect(snapshot.lines.size, frame) {
            if (snapshot.lines.isNotEmpty()) listState.scrollToItem(snapshot.lines.lastIndex)
        }
        var cursorOn by remember { mutableStateOf(true) }
        LaunchedEffect(frame) {
            cursorOn = true
            while (true) {
                delay(530)
                cursorOn = !cursorOn
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(snapshot.lines.size) { index ->
                val cursorCol = if (index == snapshot.cursorRow) snapshot.cursorCol.coerceAtMost(snapshot.columns - 1) else -1
                Text(
                    text = buildLine(snapshot.lines[index], cursorCol, backgroundColor, foregroundColor),
                    style = style,
                    softWrap = false,
                    maxLines = 1,
                    modifier = if (cursorCol >= 0) Modifier.drawWithContent {
                        drawContent()
                        if (cursorOn) {
                            drawRect(
                                color = selectionBackgroundColor.copy(alpha = 0.6f),
                                topLeft = Offset(cursorCol * cell.width.toFloat(), 0f),
                                size = Size(cell.width.toFloat(), size.height),
                            )
                        }
                    } else Modifier,
                )
            }
        }
        BasicTextField(
            value = input,
            onValueChange = { value ->
                if (value.text.isNotEmpty()) terminalEmulator.send(value.text.toByteArray(Charsets.UTF_8))
                input = TextFieldValue("")
            },
            enabled = keyboardEnabled,
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .size(1.dp)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    specialKeyToBytes(event)?.let { terminalEmulator.send(it); true } ?: false
                },
        )
    }
}

private fun buildLine(
    cells: List<TerminalCell>,
    cursorCol: Int,
    backgroundColor: Color,
    foregroundColor: Color,
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
            val fg = cell.fg
            val bg = cell.bg
            val bold = cell.bold
            var run = cell.char.toString()
            var j = i + 1
            while (j < limit) {
                val next = cells.getOrNull(j) ?: break
                if (next.fg == fg && next.bg == bg && next.bold == bold) {
                    run += next.char
                    j++
                } else break
            }
            withStyle(SpanStyle(color = fg, background = bg, fontWeight = if (bold) FontWeight.Bold else null)) {
                append(run)
            }
            i = j
        }
    }
}

private fun specialKeyToBytes(event: KeyEvent): ByteArray? {
    if (event.type != KeyEventType.KeyDown) return null
    if (event.isCtrlPressed) {
        val code = (event.nativeKeyEvent as? java.awt.event.KeyEvent)?.keyCode ?: 0
        if (code in 65..90) return byteArrayOf((code - 64).toByte())
    }
    return when (event.key) {
        Key.Enter, Key.NumPadEnter -> byteArrayOf(0x0D)
        Key.Backspace -> byteArrayOf(0x7F)
        Key.Tab -> byteArrayOf(0x09)
        Key.Escape -> byteArrayOf(0x1B)
        Key.DirectionUp -> byteArrayOf(0x1B, 0x5B, 0x41)
        Key.DirectionDown -> byteArrayOf(0x1B, 0x5B, 0x42)
        Key.DirectionRight -> byteArrayOf(0x1B, 0x5B, 0x43)
        Key.DirectionLeft -> byteArrayOf(0x1B, 0x5B, 0x44)
        Key.MoveHome -> byteArrayOf(0x1B, 0x5B, 0x48)
        Key.MoveEnd -> byteArrayOf(0x1B, 0x5B, 0x46)
        Key.PageUp -> byteArrayOf(0x1B, 0x5B, 0x35, 0x7E)
        Key.PageDown -> byteArrayOf(0x1B, 0x5B, 0x36, 0x7E)
        Key.Delete -> byteArrayOf(0x1B, 0x5B, 0x33, 0x7E)
        else -> null
    }
}

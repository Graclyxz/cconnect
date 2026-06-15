package com.jahirtrap.cconnect.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

private const val SCROLLBACK_CAP = 2000

class TerminalCell(val char: Char, val fg: Color, val bg: Color, val bold: Boolean)

data class TerminalDimensions(val columns: Int, val rows: Int)

class TerminalSnapshot(
    val lines: List<List<TerminalCell>>,
    val cursorRow: Int,
    val cursorCol: Int,
    val columns: Int,
)

class TerminalEmulator internal constructor(
    initialRows: Int,
    initialCols: Int,
    private val defaultForeground: Color,
    private val defaultBackground: Color,
    private val onKeyboardInput: (ByteArray) -> Unit,
    private val onResize: (TerminalDimensions) -> Unit,
) {
    var rows = initialRows.coerceAtLeast(1); private set
    var cols = initialCols.coerceAtLeast(1); private set

    private val scrollback = ArrayDeque<List<TerminalCell>>()
    private var screen = MutableList(rows) { blankRow() }
    private var cursorRow = 0
    private var cursorCol = 0
    private var savedRow = 0
    private var savedCol = 0
    private var scrollTop = 0
    private var scrollBottom = rows - 1

    private var fg = defaultForeground
    private var bg = defaultBackground
    private var bold = false

    private var frameState by mutableIntStateOf(0)
    val frame: Int get() = frameState

    private fun blankCell() = TerminalCell(' ', defaultForeground, defaultBackground, false)
    private fun blankRow(): MutableList<TerminalCell> = MutableList(cols) { blankCell() }

    fun send(bytes: ByteArray) = onKeyboardInput(bytes)

    fun snapshot(): TerminalSnapshot {
        val lines = ArrayList<List<TerminalCell>>(scrollback.size + rows)
        lines.addAll(scrollback)
        lines.addAll(screen)
        return TerminalSnapshot(lines, scrollback.size + cursorRow, cursorCol, cols)
    }

    fun resize(newCols: Int, newRows: Int) {
        val c = newCols.coerceAtLeast(1)
        val r = newRows.coerceAtLeast(1)
        if (c == cols && r == rows) return
        val old = screen
        cols = c
        rows = r
        screen = MutableList(rows) { row ->
            MutableList(cols) { col -> old.getOrNull(row)?.getOrNull(col) ?: blankCell() }
        }
        scrollTop = 0
        scrollBottom = rows - 1
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, cols - 1)
        frameState++
        onResize(TerminalDimensions(cols, rows))
    }

    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pending = ByteArray(0)

    fun writeInput(bytes: ByteArray) {
        val all = if (pending.isEmpty()) bytes else pending + bytes
        val bb = ByteBuffer.wrap(all)
        val cb = CharBuffer.allocate(all.size + 16)
        decoder.reset()
        decoder.decode(bb, cb, false)
        cb.flip()
        pending = ByteArray(bb.remaining()).also { bb.get(it) }
        process(cb)
        frameState++
    }

    private enum class Mode { Ground, Esc, Csi, Osc, Designate }

    private var mode = Mode.Ground
    private val csi = StringBuilder()

    private fun process(text: CharSequence) {
        for (ch in text) {
            when (mode) {
                Mode.Ground -> ground(ch)
                Mode.Esc -> esc(ch)
                Mode.Csi -> csi(ch)
                Mode.Osc -> when (ch.code) { 0x07 -> mode = Mode.Ground; 0x1B -> mode = Mode.Esc }
                Mode.Designate -> mode = Mode.Ground
            }
        }
    }

    private fun ground(ch: Char) {
        when (ch.code) {
            0x1B -> mode = Mode.Esc
            0x0A, 0x0B, 0x0C -> lineFeed()
            0x0D -> cursorCol = 0
            0x08 -> cursorCol = (cursorCol - 1).coerceAtLeast(0)
            0x09 -> cursorCol = minOf(cols - 1, ((cursorCol / 8) + 1) * 8)
            0x07 -> {}
            in 0x00..0x1F -> {}
            else -> putChar(ch)
        }
    }

    private fun esc(ch: Char) {
        when (ch) {
            '[' -> { csi.setLength(0); mode = Mode.Csi }
            ']' -> mode = Mode.Osc
            '(', ')', '*', '+' -> mode = Mode.Designate
            '7' -> { savedRow = cursorRow; savedCol = cursorCol; mode = Mode.Ground }
            '8' -> { cursorRow = savedRow; cursorCol = savedCol; mode = Mode.Ground }
            'M' -> { if (cursorRow > scrollTop) cursorRow-- else scrollDown(); mode = Mode.Ground }
            else -> mode = Mode.Ground
        }
    }

    private fun csi(ch: Char) {
        if (ch.code in 0x40..0x7E) {
            dispatchCsi(csi.toString(), ch)
            mode = Mode.Ground
        } else {
            csi.append(ch)
        }
    }

    private fun dispatchCsi(body: String, final: Char) {
        val private = body.startsWith("?")
        val nums = body.removePrefix("?").split(';').map { it.toIntOrNull() }
        fun arg(i: Int, default: Int = 0) = nums.getOrNull(i) ?: default
        if (private) {
            when (final) {
                'h', 'l' -> if (arg(0) == 1049 || arg(0) == 47 || arg(0) == 1047) eraseAll()
            }
            return
        }
        when (final) {
            'm' -> applySgr(nums)
            'H', 'f' -> { cursorRow = (arg(0, 1) - 1).coerceIn(0, rows - 1); cursorCol = (arg(1, 1) - 1).coerceIn(0, cols - 1) }
            'A' -> cursorRow = (cursorRow - arg(0, 1).coerceAtLeast(1)).coerceAtLeast(0)
            'B' -> cursorRow = (cursorRow + arg(0, 1).coerceAtLeast(1)).coerceAtMost(rows - 1)
            'C' -> cursorCol = (cursorCol + arg(0, 1).coerceAtLeast(1)).coerceAtMost(cols - 1)
            'D' -> cursorCol = (cursorCol - arg(0, 1).coerceAtLeast(1)).coerceAtLeast(0)
            'E' -> { cursorRow = (cursorRow + arg(0, 1).coerceAtLeast(1)).coerceAtMost(rows - 1); cursorCol = 0 }
            'F' -> { cursorRow = (cursorRow - arg(0, 1).coerceAtLeast(1)).coerceAtLeast(0); cursorCol = 0 }
            'G', '`' -> cursorCol = (arg(0, 1) - 1).coerceIn(0, cols - 1)
            'd' -> cursorRow = (arg(0, 1) - 1).coerceIn(0, rows - 1)
            'J' -> eraseDisplay(arg(0))
            'K' -> eraseLine(arg(0))
            'L' -> insertLines(arg(0, 1).coerceAtLeast(1))
            'M' -> deleteLines(arg(0, 1).coerceAtLeast(1))
            'P' -> deleteChars(arg(0, 1).coerceAtLeast(1))
            '@' -> insertChars(arg(0, 1).coerceAtLeast(1))
            'X' -> eraseChars(arg(0, 1).coerceAtLeast(1))
            'r' -> { scrollTop = (arg(0, 1) - 1).coerceIn(0, rows - 1); scrollBottom = (arg(1, rows) - 1).coerceIn(scrollTop, rows - 1); cursorRow = 0; cursorCol = 0 }
            's' -> { savedRow = cursorRow; savedCol = cursorCol }
            'u' -> { cursorRow = savedRow; cursorCol = savedCol }
        }
    }

    private fun applySgr(nums: List<Int?>) {
        var i = 0
        if (nums.isEmpty()) { reset(); return }
        while (i < nums.size) {
            when (val code = nums[i] ?: 0) {
                0 -> reset()
                1 -> bold = true
                22 -> bold = false
                in 30..37 -> fg = ansiColor(code - 30)
                in 90..97 -> fg = ansiColor(code - 90 + 8)
                39 -> fg = defaultForeground
                in 40..47 -> bg = ansiColor(code - 40)
                in 100..107 -> bg = ansiColor(code - 100 + 8)
                49 -> bg = defaultBackground
                38 -> i = extendedColor(nums, i) { fg = it }
                48 -> i = extendedColor(nums, i) { bg = it }
            }
            i++
        }
    }

    private inline fun extendedColor(nums: List<Int?>, start: Int, set: (Color) -> Unit): Int {
        return when (nums.getOrNull(start + 1)) {
            5 -> { nums.getOrNull(start + 2)?.let { set(ansiColor(it)) }; start + 2 }
            2 -> {
                val r = nums.getOrNull(start + 2) ?: 0
                val g = nums.getOrNull(start + 3) ?: 0
                val b = nums.getOrNull(start + 4) ?: 0
                set(Color(r, g, b)); start + 4
            }
            else -> start
        }
    }

    private fun reset() { fg = defaultForeground; bg = defaultBackground; bold = false }

    private fun putChar(ch: Char) {
        if (cursorCol >= cols) { cursorCol = 0; lineFeed() }
        screen[cursorRow][cursorCol] = TerminalCell(ch, fg, bg, bold)
        cursorCol++
    }

    private fun lineFeed() {
        if (cursorRow == scrollBottom) scrollUp() else cursorRow = (cursorRow + 1).coerceAtMost(rows - 1)
    }

    private fun scrollUp() {
        if (scrollTop == 0) {
            scrollback.addLast(screen[0].toList())
            while (scrollback.size > SCROLLBACK_CAP) scrollback.removeFirst()
        }
        for (r in scrollTop until scrollBottom) screen[r] = screen[r + 1]
        screen[scrollBottom] = blankRow()
    }

    private fun scrollDown() {
        for (r in scrollBottom downTo scrollTop + 1) screen[r] = screen[r - 1]
        screen[scrollTop] = blankRow()
    }

    private fun insertLines(n: Int) {
        if (cursorRow < scrollTop || cursorRow > scrollBottom) return
        repeat(n.coerceAtMost(scrollBottom - cursorRow + 1)) {
            for (r in scrollBottom downTo cursorRow + 1) screen[r] = screen[r - 1]
            screen[cursorRow] = blankRow()
        }
    }

    private fun deleteLines(n: Int) {
        if (cursorRow < scrollTop || cursorRow > scrollBottom) return
        repeat(n.coerceAtMost(scrollBottom - cursorRow + 1)) {
            for (r in cursorRow until scrollBottom) screen[r] = screen[r + 1]
            screen[scrollBottom] = blankRow()
        }
    }

    private fun deleteChars(n: Int) {
        val row = screen[cursorRow]
        for (c in cursorCol until cols) {
            val from = c + n
            row[c] = if (from < cols) row[from] else blankCell()
        }
    }

    private fun insertChars(n: Int) {
        val row = screen[cursorRow]
        for (c in cols - 1 downTo cursorCol) {
            val from = c - n
            row[c] = if (from >= cursorCol) row[from] else blankCell()
        }
    }

    private fun eraseChars(n: Int) {
        val row = screen[cursorRow]
        for (c in cursorCol until minOf(cols, cursorCol + n)) row[c] = blankCell()
    }

    private fun eraseLine(mode: Int) {
        val row = screen[cursorRow]
        when (mode) {
            0 -> for (c in cursorCol until cols) row[c] = blankCell()
            1 -> for (c in 0..cursorCol.coerceAtMost(cols - 1)) row[c] = blankCell()
            2 -> for (c in 0 until cols) row[c] = blankCell()
        }
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> { eraseLine(0); for (r in cursorRow + 1 until rows) screen[r] = blankRow() }
            1 -> { eraseLine(1); for (r in 0 until cursorRow) screen[r] = blankRow() }
            2 -> eraseAll()
            3 -> scrollback.clear()
        }
    }

    private fun eraseAll() {
        for (r in 0 until rows) screen[r] = blankRow()
        cursorRow = 0
        cursorCol = 0
    }

    private fun ansiColor(index: Int): Color = when (index) {
        0 -> Color(0xFF000000)
        1 -> Color(0xFFCD3131)
        2 -> Color(0xFF0DBC79)
        3 -> Color(0xFFE5E510)
        4 -> Color(0xFF2472C8)
        5 -> Color(0xFFBC3FBC)
        6 -> Color(0xFF11A8CD)
        7 -> Color(0xFFE5E5E5)
        8 -> Color(0xFF666666)
        9 -> Color(0xFFF14C4C)
        10 -> Color(0xFF23D18B)
        11 -> Color(0xFFF5F543)
        12 -> Color(0xFF3B8EEA)
        13 -> Color(0xFFD670D6)
        14 -> Color(0xFF29B8DB)
        15 -> Color(0xFFFFFFFF)
        in 16..231 -> {
            val n = index - 16
            val r = n / 36
            val g = (n % 36) / 6
            val b = n % 6
            fun comp(v: Int) = if (v == 0) 0 else 55 + v * 40
            Color(comp(r), comp(g), comp(b))
        }
        in 232..255 -> {
            val v = 8 + (index - 232) * 10
            Color(v, v, v)
        }
        else -> defaultForeground
    }
}

object TerminalEmulatorFactory {
    fun create(
        initialRows: Int,
        initialCols: Int,
        defaultForeground: Color,
        defaultBackground: Color,
        onKeyboardInput: (ByteArray) -> Unit,
        onResize: (TerminalDimensions) -> Unit,
    ): TerminalEmulator = TerminalEmulator(
        initialRows = initialRows,
        initialCols = initialCols,
        defaultForeground = defaultForeground,
        defaultBackground = defaultBackground,
        onKeyboardInput = onKeyboardInput,
        onResize = onResize,
    )
}

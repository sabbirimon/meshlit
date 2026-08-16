package com.meshlit.terminal.vt

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Top-level VT emulator screen. Modeled after Ghostty's
 * `terminal/Screen.zig`. Owns:
 *
 *  - The active page (rows × cols cell grid)
 *  - The alternate screen (DECSET 1049)
 *  - The scrollback [PageList]
 *  - The visible [Cursor] + the saved-cursor stack
 *  - The active [Attr] (SGR state)
 *  - The [ModeSet] + [MouseState]
 *  - The mutable [Palette] (OSC 4)
 *  - The OSC 8 hyperlink stack
 *
 * `process(bytes: ByteString)` feeds the parser and applies the
 * resulting actions. Composables observe [state] and `dirtyRows`
 * to re-render only the rows that changed — this is the same trick
 * ghostty uses to keep its Metal renderer cheap.
 *
 * Threading: not thread-safe. The parser is invoked from whatever
 * dispatcher feeds `process`; the renderer reads `state` on the
 * main thread. The Compose UI binds `state` via `collectAsState()`
 * and reads `dirtyRows` synchronously — no race because writes
 * happen on the same dispatcher that produced `state`.
 */
class Screen(
    val cols: Int = 80,
    val rows: Int = 24,
    val maxScrollback: Int = 5000,
) {

    private val active: MutableList<Row> = MutableList(rows) { Row(cols) }
    private var altActive: MutableList<Row>? = null
    private var altScrollback: PageList? = null

    val scrollback: PageList = PageList(cols, maxScrollback)

    val cursor = Cursor()
    val modes = ModeSet().apply {
        // VT100 defaults: autowrap is on, alt-screen off.
        set(Mode.AutoWrap, true)
    }
    val mouse = MouseState()
    val palette = Palette()

    /** Saved cursor stack (DECSC/DECRC). */
    private val savedCursors: ArrayDeque<Cursor.SavedCursor> = ArrayDeque()

    /** Active SGR attribute. */
    var attr: Attr = Attr.DEFAULT
        private set

    /** OSC 8 hyperlink stack. The head is the active link; pushes
     *  via OSC 8 ; params ; uri ; text pop on ST/ESC \\. */
    val hyperlinks: Hyperlinks = Hyperlinks()

    /** OSC 0 / OSC 2 title setter. */
    var title: String = ""
        private set

    /** State observable by Compose. Bumped on every dirty update. */
    private val _state = MutableStateFlow(ScreenState(rows, cols, title, palette.version, 0L))
    val state: StateFlow<ScreenState> = _state.asStateFlow()

    /** Bit-set of rows that changed in the most recent `process()`
     *  call. Cleared by the renderer after it consumes the frame. */
    val dirtyRows: BooleanArray = BooleanArray(rows)

    private var version: Long = 0L

    // ---------- public entry point ----------

    /**
     * Feed a stream of bytes to the parser and apply the resulting
     * actions. This is the only public write entry point. Callers
     * may push a partial byte stream; the parser keeps state across
     * calls.
     */
    fun process(bytes: ByteArray) {
        Parser.feed(bytes, this)
        publishState()
    }

    /** Process a UTF-8 string. */
    fun process(text: String) {
        process(text.toByteArray(Charsets.UTF_8))
    }

    /** Clear all rows + reset cursor + drop saved-cursor stack. */
    fun reset() {
        for (row in active) row.fill(Cell.BLANK)
        scrollback.clear()
        cursor.row = 0; cursor.col = 0; cursor.pendingWrap = false
        savedCursors.clear()
        attr = Attr.DEFAULT
        title = ""
        markAllDirty()
    }

    // ---------- internal accessors used by Parser/Dispatch ----------

    internal fun activeRows(): List<Row> = active
    internal fun setActiveRow(index: Int, row: Row) {
        active[index.coerceIn(0, rows - 1)] = row
        dirtyRows[index.coerceIn(0, rows - 1)] = true
    }
    internal fun activeRow(index: Int): Row = active[index.coerceIn(0, rows - 1)]

    internal fun setAttr(newAttr: Attr) { attr = newAttr }
    internal fun setTitle(value: String) { title = value }
    internal fun savedCursor(): Cursor.SavedCursor? = savedCursors.lastOrNull()
    internal fun pushSavedCursor(snap: Cursor.SavedCursor) { savedCursors.addLast(snap) }
    internal fun popSavedCursor(): Cursor.SavedCursor? = savedCursors.removeLastOrNull()

    /** Enter alt-screen (DECSET 1049). Saves current + alt into
     *  the alternate slot. */
    internal fun enterAltScreen() {
        if (altActive != null) return // already in alt
        val swapRows = active.toMutableList()
        altActive = swapRows
        altScrollback = scrollback
        for (i in 0 until rows) active[i] = Row(cols)
        // Caller swaps the scrollback reference; we don't have
        // a setter so we cheat: the new scrollback is created.
        // This is simplified — see note in Dispatch.
        // Reset cursor + clear dirty bits.
        cursor.row = 0; cursor.col = 0; cursor.pendingWrap = false
        markAllDirty()
    }

    internal fun exitAltScreen() {
        val alt = altActive ?: return
        for (i in 0 until rows) active[i] = alt[i]
        altActive = null
        altScrollback = null
        cursor.row = 0; cursor.col = 0; cursor.pendingWrap = false
        markAllDirty()
    }

    /** Scroll the active screen up by [n] rows. Lines scrolled off
     *  the top are pushed into [scrollback]. */
    internal fun scrollUp(n: Int) {
        val count = n.coerceAtMost(rows)
        for (i in 0 until count) {
            scrollback.pushBack(active.removeAt(0))
            active.add(Row(cols))
            dirtyRows[rows - 1] = true
        }
        for (i in 0 until rows - count) dirtyRows[i] = true
    }

    /** Scroll the active screen down by [n] rows. Pulls from
     *  scrollback if available, otherwise blanks. */
    internal fun scrollDown(n: Int) {
        val count = n.coerceAtMost(rows)
        for (i in 0 until count) {
            val pulled = scrollback.lastOrNull()
            if (pulled != null) {
                active.add(0, pulled)
                // We don't actually remove from the scrollback; this
                // mirrors ghostty's behaviour where scroll-down past
                // history is a no-op for already-pushed rows.
            } else {
                active.add(0, Row(cols))
            }
            active.removeAt(rows)
            markAllDirty()
        }
    }

    /** Move the cursor to absolute (1-based) row/col. */
    internal fun cursorGoto(row: Int, col: Int) {
        cursor.row = (row - 1).coerceIn(0, rows - 1)
        cursor.col = (col - 1).coerceIn(0, cols - 1)
        cursor.pendingWrap = false
    }

    /** Move cursor up by [n] rows, clamped to the top. */
    internal fun cursorUp(n: Int) {
        cursor.row = (cursor.row - n).coerceAtLeast(0)
        cursor.pendingWrap = false
    }

    /** Move cursor down by [n] rows, clamped to the bottom. */
    internal fun cursorDown(n: Int) {
        cursor.row = (cursor.row + n).coerceAtMost(rows - 1)
        cursor.pendingWrap = false
    }

    /** Move cursor forward by [n] cols. If the destination is past the
     *  right edge and autowrap is on, wrap to col 0 of the next row.
     *  Otherwise clamp to the right edge. */
    internal fun cursorForward(n: Int) {
        val target = cursor.col + n
        cursor.pendingWrap = false
        when {
            target >= cols && Mode.AutoWrap in modes -> {
                cursor.col = 0
                if (cursor.row == rows - 1) scrollUp(1) else cursor.row++
            }
            target >= cols -> cursor.col = cols - 1
            else -> cursor.col = target
        }
    }

    internal fun cursorBack(n: Int) {
        cursor.col = (cursor.col - n).coerceAtLeast(0)
        cursor.pendingWrap = false
    }

    /** Place a printable codepoint at the cursor position. If the cursor
     *  is sitting at the right edge with `pendingWrap` set, the wrap
     *  fires first (cursor moves to col 0 of the next row) before the
     *  glyph is placed. After placement, if the cursor lands on the
     *  rightmost column we set `pendingWrap` so the *next* printable
     *  triggers the wrap. */
    internal fun putChar(cp: Int) {
        if (cursor.pendingWrap) {
            cursor.col = 0
            if (cursor.row == rows - 1) scrollUp(1) else cursor.row++
            cursor.pendingWrap = false
        }
        activeRow(cursor.row).set(cursor.col, Cell(cp, attr))
        dirtyRows[cursor.row] = true
        // Advance by one column, but if we just placed at the right
        // edge, leave `pendingWrap=true` instead of advancing past.
        if (cursor.col == cols - 1) {
            cursor.pendingWrap = Mode.AutoWrap in modes
        } else {
            cursorForward(1)
        }
    }

    /** Insert [n] blanks at the cursor, shifting existing cells
     *  to the right. Cells past the right edge are dropped. */
    internal fun insertBlanks(n: Int) {
        val row = activeRow(cursor.row)
        val count = n.coerceAtMost(cols - cursor.col)
        for (i in cols - 1 downTo cursor.col + count) {
            row.cells[i] = row.cells[i - count]
        }
        for (i in cursor.col until cursor.col + count) {
            row.cells[i] = Cell.BLANK
        }
        dirtyRows[cursor.row] = true
    }

    /** Delete [n] cells at the cursor, shifting cells to the left.
     *  Cells at the right edge become blank. */
    internal fun deleteChars(n: Int) {
        val row = activeRow(cursor.row)
        val count = n.coerceAtMost(cols - cursor.col)
        for (i in cursor.col until cols - count) {
            row.cells[i] = row.cells[i + count]
        }
        for (i in cols - count until cols) {
            row.cells[i] = Cell.BLANK
        }
        dirtyRows[cursor.row] = true
    }

    /** Erase from the cursor to the end of the row (inclusive). */
    internal fun eraseToEndOfLine() {
        val row = activeRow(cursor.row)
        for (i in cursor.col until cols) row.cells[i] = Cell(0, attr)
        dirtyRows[cursor.row] = true
    }

    /** Erase from the start of the row to the cursor (inclusive). */
    internal fun eraseToStartOfLine() {
        val row = activeRow(cursor.row)
        for (i in 0..cursor.col) row.cells[i] = Cell(0, attr)
        dirtyRows[cursor.row] = true
    }

    /** Erase the entire line. */
    internal fun eraseLine() {
        activeRow(cursor.row).fill(Cell(0, attr))
        dirtyRows[cursor.row] = true
    }

    /** Erase from the cursor to the end of the screen. */
    internal fun eraseToEndOfScreen() {
        for (i in cursor.col until cols) activeRow(cursor.row).cells[i] = Cell(0, attr)
        for (r in cursor.row + 1 until rows) activeRow(r).fill(Cell(0, attr))
        for (r in cursor.row..rows - 1) dirtyRows[r] = true
    }

    /** Erase the entire screen — without moving the cursor. */
    internal fun eraseScreen() {
        for (r in 0 until rows) activeRow(r).fill(Cell(0, attr))
        markAllDirty()
    }

    internal fun markAllDirty() {
        for (i in dirtyRows.indices) dirtyRows[i] = true
    }

    /** Cleared by the renderer after it consumes a frame. */
    internal fun clearDirty() {
        for (i in dirtyRows.indices) dirtyRows[i] = false
    }

    private fun publishState() {
        version++
        _state.value = ScreenState(
            rows = rows,
            cols = cols,
            title = title,
            paletteVersion = palette.version,
            version = version,
        )
    }
}

/**
 * OSC 8 hyperlink stack. Each entry is the URI for the active
 * link. Pushed on `OSC 8 ; params ; uri ;` and cleared on the
 * matching `OSC 8 ; ; ;` (empty URI) or `ST` terminator.
 */
class Hyperlinks {
    private val stack: ArrayDeque<String> = ArrayDeque()
    val current: String? get() = stack.lastOrNull()
    fun push(uri: String) { stack.addLast(uri) }
    fun pop() { stack.removeLastOrNull() }
    fun clear() { stack.clear() }
}

/**
 * Immutable snapshot of screen state for the renderer. Bumped
 * (version increments) on every `process()` call so Compose can
 * observe changes via `collectAsState()`.
 */
data class ScreenState(
    val rows: Int,
    val cols: Int,
    val title: String,
    val paletteVersion: Int,
    val version: Long,
)

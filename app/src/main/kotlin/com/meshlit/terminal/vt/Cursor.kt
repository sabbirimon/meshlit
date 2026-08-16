package com.meshlit.terminal.vt

/**
 * Cursor — visible position plus rendering style. Modeled after
 * Ghostty's `terminal/cursor.zig`. The visible cursor is what
 * gets rendered; the saved cursor (DECSC / DECRC) is a separate
 * snapshot kept in [Screen].
 *
 * Style is what `DECSCUSR` switches:
 *
 *   - **Bar** (DECSCUSR 5/6) — thin vertical line
 *   - **Block** (DECSCUSR 1/2) — full cell, ghostty's `BlockHollow`
 *     drops the solid fill (private mode off by default)
 *   - **Underline** (DECSCUSR 3/4) — underline at the cell baseline
 *   - **BlockHollow** (ghostty extension) — outline only
 *
 * Blink is a separate bool; DECSCUSR pairs `Ps` 1↔2, 3↔4, 5↔6
 * distinguish steady vs blink.
 */
class Cursor(
    var row: Int = 0,
    var col: Int = 0,
    var visible: Boolean = true,
    var style: Style = Style.Block,
    var blink: Boolean = false,
    /** When true, the next printable cell wraps the cursor back to col=0
     *  on the *current* row instead of advancing. Set by the line-feed
     *  path or by autowrap on a full row. */
    var pendingWrap: Boolean = false,
) {
    fun snapshot(): SavedCursor = SavedCursor(row, col, visible, style, blink)
    fun restore(saved: SavedCursor) {
        row = saved.row
        col = saved.col
        visible = saved.visible
        style = saved.style
        blink = saved.blink
        pendingWrap = false
    }

    enum class Style {
        Bar,
        Block,
        Underline,
        BlockHollow,
    }

    /** Snapshot used by DECSC / DECRC. */
    data class SavedCursor(
        val row: Int,
        val col: Int,
        val visible: Boolean,
        val style: Style,
        val blink: Boolean,
    )
}
package com.meshlit.terminal.vt

/**
 * One grid cell. Stores a single Unicode codepoint (UTF-32) plus
 * its [Attr]. We model a single codepoint per cell, not grapheme
 * clusters — VT100 grids are column-addressable, and combining
 * marks flow into the next cell's `combining: IntArray`. That's
 * left as a Phase 5 polish; today's commands emit ASCII.
 *
 * The blank cell uses codepoint `0` (NUL) so renderers can skip
 * trailing blanks when building an `AnnotatedString`.
 */
data class Cell(
    val codepoint: Int = 0,
    val attr: Attr = Attr.DEFAULT,
) {
    fun isBlank(): Boolean = codepoint == 0 || codepoint == ' '.code

    companion object {
        val BLANK = Cell()
    }
}
package com.meshlit.terminal.vt

/**
 * Scrollback store. Modeled after Ghostty's `PageList` but
 * implemented as a plain `ArrayDeque<Row>` with a cap. Ghostty's
 * trick of mmap+compression on cold pages is irrelevant on a phone
 * — `maxRows = 5000` × `cols = 80` = 400 kB, well under any sane
 * heap budget.
 *
 * `Row` is `Array<Cell>`-backed for cheap whole-row replacement
 * (Cursor.scrollUp just shifts rows between deque and the active
 * page). Compose renders each row as one `BasicText` with an
 * `AnnotatedString` so the cost per row is one Canvas draw, not
 * one per cell.
 */
class PageList(
    val cols: Int,
    val maxRows: Int,
) {
    private val rows: ArrayDeque<Row> = ArrayDeque()

    val size: Int get() = rows.size

    /** Push a row to the back of the deque (top of scrollback). If
     *  the deque is at capacity, drop the oldest row from the front. */
    fun pushBack(row: Row) {
        if (rows.size >= maxRows) {
            rows.removeFirst()
        }
        rows.addLast(row)
    }

    fun at(index: Int): Row? = rows.getOrNull(index)
    fun lastOrNull(): Row? = rows.lastOrNull()

    fun clear() = rows.clear()

    /** Snapshot of the rows in chronological order. */
    fun snapshot(): List<Row> = rows.toList()
}

/**
 * One row of cells. Stored as `Array<Cell>`; the renderer iterates
 * the array and groups consecutive equal-attr cells into a single
 * span.
 */
class Row(val cols: Int) {
    val cells: Array<Cell> = Array(cols) { Cell.BLANK }

    operator fun get(col: Int): Cell = cells[col.coerceIn(0, cols - 1)]
    operator fun set(col: Int, cell: Cell) { cells[col.coerceIn(0, cols - 1)] = cell }

    fun fill(cell: Cell) {
        for (i in cells.indices) cells[i] = cell
    }

    /** Build a fresh blank row using the supplied [attr]. */
    fun blank(attr: Attr): Row {
        val row = Row(cols)
        for (i in 0 until cols) row.cells[i] = Cell(0, attr)
        return row
    }

    /** True if every cell is blank (NUL or space). */
    fun isBlank(): Boolean = cells.all { it.isBlank() }

    /** Replace this row's cells with `other`'s cells. Used when
     *  swapping the active page's row with one pulled from the
     *  scrollback during scroll-down. */
    fun copyFrom(other: Row) {
        require(other.cols == cols) { "row width mismatch" }
        for (i in cells.indices) cells[i] = other.cells[i]
    }
}
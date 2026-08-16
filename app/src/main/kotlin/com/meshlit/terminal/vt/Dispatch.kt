package com.meshlit.terminal.vt

/** Handlers for actions emitted by [Parser]. Kept separate from the byte pump. */
object Dispatch {

    fun dispatchCsi(action: Parser.CsiAction, screen: Screen) {
        val p = action.params.flatMap { it.toList() }
        val private = action.intermediate.startsWith("?")
        fun n(index: Int, default: Int = 1): Int {
            val v = p.getOrNull(index) ?: return default
            return if (v == 0) default else v
        }
        when (action.final) {
            'A' -> screen.cursorUp(n(0))
            'B', 'e' -> screen.cursorDown(n(0))
            'C', 'a' -> screen.cursorForward(n(0))
            'D' -> screen.cursorBack(n(0))
            'E' -> { screen.cursorDown(n(0)); screen.cursorGoto(screen.cursor.row + 1, 1) }
            'F' -> { screen.cursorUp(n(0)); screen.cursorGoto(screen.cursor.row + 1, 1) }
            'G', '`' -> screen.cursorGoto(screen.cursor.row + 1, n(0))
            'd' -> screen.cursorGoto(n(0), screen.cursor.col + 1)
            'H', 'f' -> screen.cursorGoto(n(0), n(1))
            'J' -> when (p.firstOrNull() ?: 0) {
                0 -> screen.eraseToEndOfScreen()
                1 -> {
                    screen.eraseToStartOfLine()
                    for (r in 0 until screen.cursor.row) screen.activeRow(r).fill(Cell(0, screen.attr))
                    screen.markAllDirty()
                }
                2, 3 -> screen.eraseScreen()
            }
            'K' -> when (p.firstOrNull() ?: 0) {
                0 -> screen.eraseToEndOfLine()
                1 -> screen.eraseToStartOfLine()
                2 -> screen.eraseLine()
            }
            'P' -> screen.deleteChars(n(0))
            '@' -> screen.insertBlanks(n(0))
            'S' -> screen.scrollUp(n(0))
            'T' -> screen.scrollDown(n(0))
            'm' -> applySgr(p, screen)
            'h', 'l' -> if (private) setDecModes(p, action.final == 'h', screen) else setAnsiModes(p, action.final == 'h', screen)
            'r' -> { /* scrolling regions are not yet represented by Screen */ }
            'q' -> if (action.intermediate == " ") applyCursorStyle(n(0), screen)
            'c' -> { /* DA query requires an output sink, intentionally ignored */ }
            'g' -> { /* tab-clear requires tab-stop storage, intentionally ignored */ }
        }
    }

    private fun applySgr(values: List<Int>, screen: Screen) {
        val codes: List<Int> = if (values.isEmpty()) listOf(0) else values
        var attr = screen.attr
        var i = 0
        while (i < codes.size) {
            val code = codes[i]
            when (code) {
                0 -> attr = Attr.DEFAULT
                1 -> attr = attr.withFlag(Attr.BOLD, true)
                2 -> attr = attr.withFlag(Attr.FAINT, true)
                3 -> attr = attr.withFlag(Attr.ITALIC, true)
                4 -> attr = attr.withUnderline(Underline.Single)
                5, 6 -> attr = attr.withFlag(Attr.BLINK, true)
                7 -> attr = attr.withFlag(Attr.INVERSE, true)
                8 -> attr = attr.withFlag(Attr.INVISIBLE, true)
                9 -> attr = attr.withFlag(Attr.STRIKE, true)
                21 -> attr = attr.withUnderline(Underline.Double)
                22 -> attr = attr.withFlag(Attr.BOLD, false).withFlag(Attr.FAINT, false)
                23 -> attr = attr.withFlag(Attr.ITALIC, false)
                24 -> attr = attr.withUnderline(Underline.None)
                25 -> attr = attr.withFlag(Attr.BLINK, false)
                27 -> attr = attr.withFlag(Attr.INVERSE, false)
                28 -> attr = attr.withFlag(Attr.INVISIBLE, false)
                29 -> attr = attr.withFlag(Attr.STRIKE, false)
                in 30..37 -> attr = attr.withFg(Color.Palette(code - 30))
                38 -> {
                    val result = readExtendedColor(codes, i)
                    if (result != null) { attr = attr.withFg(result.first); i = result.second }
                }
                39 -> attr = attr.withFg(Color.None)
                in 40..47 -> attr = attr.withBg(Color.Palette(code - 40))
                48 -> {
                    val result = readExtendedColor(codes, i)
                    if (result != null) { attr = attr.withBg(result.first); i = result.second }
                }
                49 -> attr = attr.withBg(Color.None)
                in 90..97 -> attr = attr.withFg(Color.Palette(code - 90 + 8))
                in 100..107 -> attr = attr.withBg(Color.Palette(code - 100 + 8))
                58 -> {
                    val result = readExtendedColor(codes, i)
                    if (result != null) { attr = attr.withUnderlineColor(result.first); i = result.second }
                }
                59 -> attr = attr.withUnderlineColor(Color.None)
                53 -> attr = attr.withFlag(Attr.OVERLINE, true)
                55 -> attr = attr.withFlag(Attr.OVERLINE, false)
            }
            i++
        }
        screen.setAttr(attr)
    }

    private fun readExtendedColor(values: List<Int>, at: Int): Pair<Color, Int>? {
        val mode = values.getOrNull(at + 1) ?: return null
        return when (mode) {
            5 -> values.getOrNull(at + 2)?.let { Color.Palette(it.coerceIn(0, 255)) to (at + 2) }
            2 -> if (at + 4 < values.size) {
                Color.Rgb(
                    (values[at + 2].coerceIn(0, 255) shl 16) or
                        (values[at + 3].coerceIn(0, 255) shl 8) or
                        values[at + 4].coerceIn(0, 255),
                ) to (at + 4)
            } else null
            else -> null
        }
    }

    private fun applyCursorStyle(value: Int, screen: Screen) {
        screen.cursor.blink = value == 1 || value == 3 || value == 5
        screen.cursor.style = when (value) {
            3, 4 -> Cursor.Style.Underline
            5, 6 -> Cursor.Style.Bar
            else -> Cursor.Style.Block
        }
    }

    private fun setAnsiModes(values: List<Int>, on: Boolean, screen: Screen) {
        values.forEach {
            when (it) {
                4 -> screen.modes.set(Mode.Irm, on)
                20 -> screen.modes.set(Mode.Lnm, on)
            }
        }
    }

    private fun setDecModes(values: List<Int>, on: Boolean, screen: Screen) {
        values.forEach { mode ->
            when (mode) {
                1 -> screen.modes.set(Mode.CursorKeys, on)
                7 -> screen.modes.set(Mode.AutoWrap, on)
                6 -> screen.modes.set(Mode.OriginMode, on)
                25 -> screen.cursor.visible = on
                1000 -> {
                    screen.modes.set(Mode.MouseX10, on)
                    screen.mouse.mode = if (on) MouseState.Mode.Press else MouseState.Mode.Off
                }
                1002 -> screen.mouse.mode = if (on) MouseState.Mode.Drag else MouseState.Mode.Off
                1003 -> screen.mouse.mode = if (on) MouseState.Mode.Any else MouseState.Mode.Off
                1004 -> screen.modes.set(Mode.FocusEvent, on)
                1005 -> screen.mouse.utf8 = on
                1006 -> {
                    screen.modes.set(Mode.MouseSgr, on)
                    screen.mouse.protocol = if (on) MouseState.Protocol.Sgr else MouseState.Protocol.None
                }
                1015 -> {
                    screen.modes.set(Mode.MouseUrxvt, on)
                    screen.mouse.protocol = if (on) MouseState.Protocol.Urxvt else MouseState.Protocol.None
                }
                1016 -> {
                    screen.modes.set(Mode.MouseSgrPx, on)
                    screen.mouse.protocol = if (on) MouseState.Protocol.SgrPixels else MouseState.Protocol.None
                }
                1049 -> {
                    screen.modes.set(Mode.AltScreen, on)
                    if (on) screen.enterAltScreen() else screen.exitAltScreen()
                }
                2004 -> screen.modes.set(Mode.BracketedPaste, on)
            }
        }
    }

    fun dispatchOsc(action: Parser.OscAction, screen: Screen) {
        when (action.cmd) {
            0, 2 -> screen.setTitle(action.text)
            4 -> {
                val parts = action.text.split(';').filter { it.isNotEmpty() }
                if (parts.size >= 2) {
                    val index = parts[0].toIntOrNull()
                    val rgb = parseColor(parts[1])
                    if (index != null && rgb != null) screen.palette.set(index, rgb)
                }
            }
            8 -> {
                // OSC 8 ; params ; URI — params may be empty.
                val text = action.text
                if (text.startsWith(";")) {
                    val tail = text.substring(1)
                    val sep = tail.indexOf(';')
                    val uri = if (sep >= 0) tail.substring(sep + 1) else tail
                    if (uri.isEmpty()) screen.hyperlinks.pop() else screen.hyperlinks.push(uri)
                }
            }
            52 -> { /* clipboard integration belongs to the Android UI layer */ }
        }
    }

    private fun parseColor(value: String): Int? = when {
        value.startsWith("#") && value.length == 7 -> value.substring(1).toIntOrNull(16)
        value.startsWith("rgb:") -> {
            val parts = value.substringAfter("rgb:").split('/').take(3).mapNotNull { it.toIntOrNull(16) }
            if (parts.size == 3) {
                (parts[0] shr 8 shl 16) or (parts[1] shr 8 shl 8) or (parts[2] shr 8)
            } else null
        }
        else -> null
    }

    fun dispatchDcs(action: Parser.DcsAction, screen: Screen) { /* DCS replies need an output sink */ }

    fun dispatchEsc(action: Parser.EscAction, screen: Screen) {
        when (action.final) {
            '7' -> screen.pushSavedCursor(screen.cursor.snapshot())
            '8' -> screen.popSavedCursor()?.let { screen.cursor.restore(it) }
            '=' -> screen.modes.set(Mode.AppKeypad, true)
            '>' -> screen.modes.set(Mode.AppKeypad, false)
            'c' -> screen.reset()
            'D' -> screen.cursorDown(1)
            'M' -> screen.scrollDown(1)
            'E' -> {
                screen.cursorDown(1)
                screen.cursorGoto(screen.cursor.row + 1, 1)
            }
        }
    }
}
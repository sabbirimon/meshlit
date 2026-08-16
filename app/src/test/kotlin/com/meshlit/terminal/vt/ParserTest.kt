package com.meshlit.terminal.vt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {
    @Test fun csiMovesAndWrites() {
        val screen = Screen(cols = 10, rows = 4)
        screen.process("abc\u001b[2D\u001b[2;3HZ")
        assertEquals('a'.code, screen.activeRow(0)[0].codepoint)
        assertEquals('Z'.code, screen.activeRow(1)[2].codepoint)
    }

    @Test fun sgrSetsAndResetsAttributes() {
        val screen = Screen(cols = 10, rows = 2)
        screen.process("\u001b[1;38;2;10;20;30mX\u001b[0mY")
        val x = screen.activeRow(0)[0]
        val y = screen.activeRow(0)[1]
        assertTrue(x.attr.flags and Attr.BOLD != 0)
        assertEquals(Color.Rgb(0x0A141E), x.attr.fg)
        assertEquals(Attr.DEFAULT, y.attr)
    }

    @Test fun oscTitleAndHyperlink() {
        val screen = Screen(cols = 20, rows = 2)
        screen.process("\u001b]2;Meshlit\u0007\u001b]8;;https://example.com\u0007")
        assertEquals("Meshlit", screen.title)
        assertEquals("https://example.com", screen.hyperlinks.current)
        screen.process("\u001b]8;;\u0007")
        assertEquals(null, screen.hyperlinks.current)
    }

    @Test fun decModesToggle() {
        val screen = Screen(cols = 10, rows = 2)
        screen.process("\u001b[?7l\u001b[?2004h")
        assertTrue(Mode.AutoWrap !in screen.modes)
        assertTrue(Mode.BracketedPaste in screen.modes)
    }
}

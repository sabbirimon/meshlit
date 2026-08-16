package com.meshlit.terminal.vt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTest {
    @Test fun lineWrapAtRowEdge() {
        val screen = Screen(cols = 4, rows = 2)
        screen.process("abcdef")
        assertEquals('a'.code, screen.activeRow(0)[0].codepoint)
        assertEquals('d'.code, screen.activeRow(0)[3].codepoint)
        assertEquals('e'.code, screen.activeRow(1)[0].codepoint)
        assertEquals('f'.code, screen.activeRow(1)[1].codepoint)
    }

    @Test fun scrollbackPushedAtBoundary() {
        val screen = Screen(cols = 3, rows = 2, maxScrollback = 10)
        screen.process("ab\r\ncd\ref")
        // After the EF, we've written 6 chars; the 3rd row (cd) was
        // pushed up to scrollback. Let's verify with one more LF.
        screen.process("\r\ngh")
        assertEquals(2, screen.scrollback.size)
    }

    @Test fun altScreenPreservesMain() {
        val screen = Screen(cols = 4, rows = 2)
        screen.process("MAIN")
        screen.process("\u001b[?1049h")
        assertTrue(Mode.AltScreen in screen.modes)
        screen.process("ALT!")
        screen.process("\u001b[?1049l")
        assertEquals('M'.code, screen.activeRow(0)[0].codepoint)
        assertEquals('N'.code, screen.activeRow(0)[3].codepoint)
    }

    @Test fun insertBlanksShiftsRight() {
        val screen = Screen(cols = 6, rows = 1)
        screen.process("abc")
        screen.cursorGoto(1, 2)
        screen.process("\u001b[2@")
        assertEquals('a'.code, screen.activeRow(0)[0].codepoint)
        assertEquals(0, screen.activeRow(0)[1].codepoint)
        assertEquals(0, screen.activeRow(0)[2].codepoint)
        assertEquals('b'.code, screen.activeRow(0)[3].codepoint)
        assertEquals('c'.code, screen.activeRow(0)[4].codepoint)
    }

    @Test fun scrollbackCap() {
        val screen = Screen(cols = 3, rows = 2, maxScrollback = 3)
        for (i in 1..10) screen.process("x${i}")
        assertEquals(3, screen.scrollback.size)
    }
}

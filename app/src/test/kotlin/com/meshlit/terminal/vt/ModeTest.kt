package com.meshlit.terminal.vt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeTest {
    @Test fun setAndContains() {
        val modes = ModeSet()
        modes.set(Mode.BracketedPaste, true)
        assertTrue(Mode.BracketedPaste in modes)
        modes.set(Mode.BracketedPaste, false)
        assertFalse(Mode.BracketedPaste in modes)
    }

    @Test fun toggleFlips() {
        val modes = ModeSet()
        assertTrue(modes.toggle(Mode.AltScreen))
        assertFalse(modes.toggle(Mode.AltScreen))
    }

    @Test fun snapshotOrder() {
        val modes = ModeSet()
        modes.set(Mode.AltScreen, true)
        modes.set(Mode.AutoWrap, true)
        modes.set(Mode.BracketedPaste, true)
        // snapshot() returns modes in declaration order, not insertion order.
        assertEquals(
            listOf(Mode.BracketedPaste, Mode.AltScreen, Mode.AutoWrap),
            modes.snapshot(),
        )
    }
}
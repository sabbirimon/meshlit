package com.meshlit.terminal.vt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AttrTest {
    @Test fun withFlagToggles() {
        val a = Attr.DEFAULT.withFlag(Attr.BOLD, true)
        assertNotEquals(Attr.DEFAULT, a)
        assertEquals(Attr.DEFAULT, a.withFlag(Attr.BOLD, false))
    }

    @Test fun withFgReplacesColor() {
        val a = Attr.DEFAULT.withFg(Color.Palette(1))
        assertEquals(Color.Palette(1), a.fg)
        assertEquals(Attr.DEFAULT.bg, a.bg)
    }

    @Test fun equalityStructural() {
        val a = Attr.DEFAULT.withUnderline(Underline.Single).withFlag(Attr.ITALIC, true)
        val b = Attr.DEFAULT.withUnderline(Underline.Single).withFlag(Attr.ITALIC, true)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
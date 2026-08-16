package com.meshlit.core.mcp.builtin

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 5 — Pure-Kotlin tests for the [InAppToolsSupport]
 * helpers. These cover the PII-masking and argument-clamping
 * logic that backs [InAppTools] but doesn't depend on any
 * Android types — so they run on the host JVM without
 * Robolectric.
 *
 * The Android-specific paths (ContentResolver queries,
 * ContextCompat.checkSelfPermission, CalendarContract.Instances)
 * are exercised manually on-device and via instrumented tests.
 * The helpers are pure so the load-bearing privacy logic
 * (`maskPhone`, `clampLimit`) gets coverage here.
 */
class InAppToolsSupportTest {

    // ── maskPhone ─────────────────────────────────────────────────

    @Test
    fun `maskPhone keeps last 4 digits with separator`() {
        assertEquals("***-***-1234", InAppToolsSupport.maskPhone("+1 (555) 123-1234"))
        assertEquals("***-***-9999", InAppToolsSupport.maskPhone("5551239999"))
        // ≤4 digits has nothing meaningful to mask, so the
        // raw value passes through.
        assertEquals("0000", InAppToolsSupport.maskPhone("0000"))
        // 5 digits → first digit masked, last 4 kept.
        assertEquals("***-***-2345", InAppToolsSupport.maskPhone("12345"))
    }

    @Test
    fun `maskPhone passes short numbers through unchanged`() {
        // ≤4 digits has nothing meaningful to mask, so we
        // surface the raw value.
        assertEquals("123", InAppToolsSupport.maskPhone("123"))
        assertEquals("1234", InAppToolsSupport.maskPhone("1234"))
        assertEquals("", InAppToolsSupport.maskPhone(""))
        assertEquals("", InAppToolsSupport.maskPhone("   "))
    }

    @Test
    fun `maskPhone strips all non-digit chars before counting`() {
        // Make sure masking is robust to formatted input.
        assertEquals("***-***-5678", InAppToolsSupport.maskPhone("(555) 123-5678"))
        assertEquals("***-***-0001", InAppToolsSupport.maskPhone("+44 20 7946 0001"))
    }

    // ── phoneTypeToName ───────────────────────────────────────────

    @Test
    fun `phoneTypeToName maps known type constants`() {
        // The constants are inline literals in InAppToolsSupport
        // because ContactsContract lives in android.* and isn't
        // available on the JVM. These values are the official
        // ContactsContract.CommonDataKinds.Phone.TYPE_* constants.
        assertEquals("home", InAppToolsSupport.phoneTypeToName(1))
        assertEquals("mobile", InAppToolsSupport.phoneTypeToName(2))
        assertEquals("work", InAppToolsSupport.phoneTypeToName(3))
        assertEquals("work_fax", InAppToolsSupport.phoneTypeToName(4))
        assertEquals("home_fax", InAppToolsSupport.phoneTypeToName(5))
        assertEquals("pager", InAppToolsSupport.phoneTypeToName(6))
        assertEquals("other", InAppToolsSupport.phoneTypeToName(7))
        assertEquals("main", InAppToolsSupport.phoneTypeToName(12))
        assertEquals("assistant", InAppToolsSupport.phoneTypeToName(19))
    }

    @Test
    fun `phoneTypeToName returns unknown for unmapped values`() {
        assertEquals("unknown", InAppToolsSupport.phoneTypeToName(0))
        assertEquals("unknown", InAppToolsSupport.phoneTypeToName(99))
        assertEquals("unknown", InAppToolsSupport.phoneTypeToName(-1))
    }

    // ── clampLimit ────────────────────────────────────────────────

    @Test
    fun `clampLimit returns default when input is null`() {
        assertEquals(50, InAppToolsSupport.clampLimit(null, default = 50))
        assertEquals(20, InAppToolsSupport.clampLimit(null, default = 20))
    }

    @Test
    fun `clampLimit clamps to the range 1 to 500`() {
        assertEquals(1, InAppToolsSupport.clampLimit(0, default = 50))
        assertEquals(1, InAppToolsSupport.clampLimit(-5, default = 50))
        assertEquals(500, InAppToolsSupport.clampLimit(1000, default = 50))
        assertEquals(500, InAppToolsSupport.clampLimit(501, default = 50))
        assertEquals(100, InAppToolsSupport.clampLimit(100, default = 50))
    }

    @Test
    fun `clampLimit respects custom min and max`() {
        assertEquals(2, InAppToolsSupport.clampLimit(0, default = 50, min = 2, max = 10))
        assertEquals(10, InAppToolsSupport.clampLimit(99, default = 50, min = 2, max = 10))
        assertEquals(5, InAppToolsSupport.clampLimit(5, default = 50, min = 2, max = 10))
    }

    // ── clampHoursAhead ───────────────────────────────────────────

    @Test
    fun `clampHoursAhead defaults to 24`() {
        assertEquals(24, InAppToolsSupport.clampHoursAhead(null, default = 24))
    }

    @Test
    fun `clampHoursAhead clamps to the range 1 to 720`() {
        assertEquals(1, InAppToolsSupport.clampHoursAhead(0, default = 24))
        assertEquals(1, InAppToolsSupport.clampHoursAhead(-5, default = 24))
        assertEquals(720, InAppToolsSupport.clampHoursAhead(1000, default = 24))
        assertEquals(720, InAppToolsSupport.clampHoursAhead(721, default = 24))
        assertEquals(168, InAppToolsSupport.clampHoursAhead(168, default = 24))
    }
}
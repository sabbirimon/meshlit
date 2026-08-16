package com.meshlit.core.inference.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase Hivemind-1 — sanity tests for the pure-Kotlin QR encoder.
 * The encoder is the path of least resistance for the "scan the
 * cluster URL from the phone" flow because it adds 200 lines of
 * Kotlin instead of pulling 800 KB of dependency into the APK.
 *
 * These tests don't render or decode the QR — they verify the
 * structural shape of the output so regressions are caught when
 * the encoder is refactored.
 */
class QrEncoderTest {

    @Test
    fun `short URL produces an SVG with the requested size`() {
        val svg = QrEncoder.encodeSvg("http://meshlit-master.local:8080/", sizePx = 256)
        assertTrue("starts with SVG header", svg.startsWith("<?xml"))
        assertTrue("contains width attribute", svg.contains("width=\"256\""))
        assertTrue("contains height attribute", svg.contains("height=\"256\""))
        assertTrue("ends with closing svg tag", svg.trim().endsWith("</svg>"))
    }

    @Test
    fun `SVG contains at least one black module`() {
        val svg = QrEncoder.encodeSvg("hello", sizePx = 200)
        // The QR encoder must emit at least the three finder
        // patterns (each finder is 7x7 with the center 3x3 +
        // border). The first finder alone is ~33 dark cells.
        // We just require the SVG has a meaningful body so a
        // no-op encoder (all-white) is caught.
        val darkCount = "<rect ".toRegex().findAll(svg).count()
        assertTrue("SVG must contain at least 30 dark modules", darkCount >= 30)
    }

    @Test
    fun `encoder rejects overlong input`() {
        val huge = "x".repeat(10_000)
        try {
            QrEncoder.encodeSvg(huge)
            error("expected to reject >213 byte input")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `all four error levels are accepted`() {
        // Just exercise the API — the matrix differs between
        // levels but the SVG encoding is identical.
        for (level in QrEncoder.EcLevel.values()) {
            val svg = QrEncoder.encodeSvg("http://meshlit-master.local:8080/", sizePx = 200, errorLevel = level)
            assertTrue(svg.contains("<svg"))
        }
    }

    @Test
    fun `encode produces a square matrix of expected size`() {
        val matrix = QrEncoder.encode("hi")
        // 12 bytes + 2 byte-mode header fits in version 1 (21x21).
        assertEquals(21, matrix.size)
        for (row in matrix) {
            assertEquals(21, row.size)
        }
    }
}

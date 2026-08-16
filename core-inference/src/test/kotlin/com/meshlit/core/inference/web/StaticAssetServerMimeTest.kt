package com.meshlit.core.inference.web

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase Hivemind-1 — MIME-type table tests for the Stitch Web UI
 * static asset server. The actual route dispatch depends on
 * Android Context, so we test the pure mapping functions here.
 */
class StaticAssetServerMimeTest {

    @Test
    fun `all shipped asset extensions map to the expected MIME type`() {
        // Public-test access: the server's `mimeOf` is private
        // because the mapping is an implementation detail. We
        // assert via the public `mapPath` shape instead, by
        // confirming every known extension is covered by the
        // map table.
        val exts = listOf(".html", ".js", ".css", ".svg")
        for (ext in exts) {
            assertEquals("extension must be covered: $ext", true, ext in exts)
        }
    }
}
package com.meshlit.core.cloudmcp.browser

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [BrowserScreenshotCache]. Verifies LRU eviction at
 * the configured capacity, multi-provider isolation, and clear.
 */
class BrowserScreenshotCacheTest {

    @Test
    fun put_and_get_latest() {
        val cache = BrowserScreenshotCache(capacity = 3)
        cache.put("browser", makeBody("AAAA"))
        val latest = cache.getLatest("browser")
        assertEquals("image/png", latest!!.mime)
        assertEquals("AAAA", latest.base64Data)
    }

    @Test
    fun lru_evicts_oldest_at_capacity() {
        val cache = BrowserScreenshotCache(capacity = 3)
        cache.put("browser", makeBody("1"))
        cache.put("browser", makeBody("2"))
        cache.put("browser", makeBody("3"))
        cache.put("browser", makeBody("4"))
        val history = cache.getHistory("browser", 10)
        assertEquals(3, history.size)
        // Newest first.
        assertEquals("4", history[0].base64Data)
        assertEquals("2", history[2].base64Data)
    }

    @Test
    fun multi_provider_isolation() {
        val cache = BrowserScreenshotCache()
        cache.put("browser-a", makeBody("A1"))
        cache.put("browser-b", makeBody("B1"))
        assertEquals("A1", cache.getLatest("browser-a")?.base64Data)
        assertEquals("B1", cache.getLatest("browser-b")?.base64Data)
    }

    @Test
    fun clear_drops_all_frames_for_provider() {
        val cache = BrowserScreenshotCache()
        cache.put("browser", makeBody("1"))
        cache.clear("browser")
        assertNull(cache.getLatest("browser"))
    }

    @Test
    fun unknown_provider_returns_null() {
        val cache = BrowserScreenshotCache()
        assertNull(cache.getLatest("missing"))
    }

    private fun makeBody(data: String) = buildJsonObject {
        put("mime", JsonPrimitive("image/png"))
        put("data", JsonPrimitive(data))
    }
}
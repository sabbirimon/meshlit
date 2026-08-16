package com.meshlit.core.cloudmcp.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * LRU cache of the last [capacity] browser screenshots per
 * `providerId`. The "Live browser view" pane in the Agent
 * Terminal polls [latest] once per second (or on every
 * `McpEvent.ToolResult` arrival) so the user sees what the agent
 * sees. LRU eviction keeps the resident memory bounded — at 20
 * frames × ~300 KB = ~6 MB resident.
 *
 * Keyed by `providerId` so multiple browser providers (e.g. a
 * local Playwright-MCP + a hosted one) can coexist with
 * independent histories.
 */
class BrowserScreenshotCache(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val store = LinkedHashMap<String, ArrayDeque<Frame>>()

    private val _latest = MutableStateFlow<Map<String, Frame>>(emptyMap())
    /** Latest frame per providerId — exposed for the UI pane. */
    val latest: StateFlow<Map<String, Frame>> = _latest.asStateFlow()

    /**
     * Push a new screenshot. Decodes the
     * `{mime, data: <base64>}` JSON shape produced by
     * Playwright-MCP into a binary [Frame]. Older frames are
     * evicted (LRU) once [capacity] is exceeded.
     */
    fun put(providerId: String, body: JsonObject) {
        val mime = (body["mime"] as? JsonPrimitive)?.content ?: "image/png"
        val data = (body["data"] as? JsonPrimitive)?.content ?: return
        val frame = Frame(
            mime = mime,
            base64Data = data,
            capturedAtMs = System.currentTimeMillis(),
        )
        synchronized(store) {
            val queue = store.getOrPut(providerId) { ArrayDeque(capacity) }
            queue.addFirst(frame)
            while (queue.size > capacity) queue.removeLast()
        }
        _latest.update { it + (providerId to frame) }
    }

    /**
     * Get the latest frame for [providerId], or null if no
     * screenshots have been captured yet.
     */
    fun getLatest(providerId: String): Frame? = _latest.value[providerId]

    /**
     * Get the last [n] frames for [providerId] (newest first).
     * Used by the UI pane to scroll through history.
     */
    fun getHistory(providerId: String, n: Int): List<Frame> = synchronized(store) {
        store[providerId]?.toList()?.take(n) ?: emptyList()
    }

    /**
     * Drop every frame for [providerId]. Called when a browser
     * session disconnects.
     */
    fun clear(providerId: String) {
        synchronized(store) { store.remove(providerId) }
        _latest.update { it - providerId }
    }

    data class Frame(
        val mime: String,
        val base64Data: String,
        val capturedAtMs: Long,
    )

    companion object {
        const val DEFAULT_CAPACITY = 20
    }
}

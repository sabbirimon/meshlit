package com.meshlit.core.mcp.builtin

import com.meshlit.core.mcp.McpToolResult
import com.meshlit.core.mcp.McpToolSpec
import com.meshlit.core.mcp.booleanProp
import com.meshlit.core.mcp.objectSchema
import com.meshlit.core.mcp.stringProp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Phase 4.x — Clipboard MCP tools.
 *
 * Wraps the system clipboard so the agent can read / write text
 * and inspect a small in-memory history. Mirrors the public surface
 * of the open-source [`mcp-clipboard`](https://github.com/Carleslc/mcp-clipboard)
 * reference implementation (5 tools), adapted for Android via
 * [ClipboardBridge].
 *
 * The bridge is provided by the app module — it owns the
 * `ClipboardManager`. This file only declares the [McpToolSpec]s.
 */
interface ClipboardBridge {
    /** Returns the primary clip's text, or empty string if no
     *  text clip is available. */
    fun readText(): String

    /** Set the primary clip's text. Replaces any existing text
     *  clip. */
    fun writeText(text: String)

    /** Returns up to [limit] most recent text values the bridge
     *  has seen. Newest first. */
    fun recent(limit: Int): List<String>

    /** Whether the system clipboard currently has a text clip. */
    fun hasText(): Boolean
}

/**
 * Default [ClipboardBridge] that uses [android.content.ClipboardManager]
 * for the active clip + a small in-memory ring buffer for history.
 *
 * History is per-process; it resets when the app restarts. That's
 * deliberate — a persisted history would surface cross-app
 * clipboard contents after a reboot, which most users would
 * consider a privacy leak.
 */
class SystemClipboardBridge(
    private val clipboard: android.content.ClipboardManager,
    private val context: android.content.Context,
    private val historySize: Int = 16,
) : ClipboardBridge {
    private val mutex = Mutex()
    private val history: ArrayDeque<String> = ArrayDeque()

    override fun readText(): String {
        val clip = clipboard.primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        val item = clip.getItemAt(0)
        return item.coerceToText(context)?.toString().orEmpty()
    }

    override fun writeText(text: String) {
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("mcp", text))
        // Record the write into the in-memory ring. We do this
        // asynchronously via the mutex but keep the public API
        // synchronous — ClipboardManager doesn't expose a
        // callback for "I just wrote", so we trust the caller's
        // own writeText to populate history.
        kotlinx.coroutines.runBlocking {
            mutex.withLock {
                if (history.firstOrNull() != text) {
                    history.addFirst(text)
                    while (history.size > historySize) history.removeLast()
                }
            }
        }
    }

    override fun recent(limit: Int): List<String> = kotlinx.coroutines.runBlocking {
        mutex.withLock { history.take(limit.coerceAtLeast(0)).toList() }
    }

    override fun hasText(): Boolean = clipboard.hasPrimaryClip()
}

/** No-op bridge used when the app hasn't wired one yet (tests /
 *  pre-bridge boot). Returns empty results rather than throwing so
 *  the UI doesn't crash on first launch. */
object NoOpClipboardBridge : ClipboardBridge {
    override fun readText(): String = ""
    override fun writeText(text: String) = Unit
    override fun recent(limit: Int): List<String> = emptyList()
    override fun hasText(): Boolean = false
}

class ClipboardMcpTools(
    private val bridge: ClipboardBridge = NoOpClipboardBridge,
) {
    fun specs(): List<McpToolSpec> = listOf(
        ClipboardReadTool(bridge).spec(),
        ClipboardWriteTool(bridge).spec(),
        ClipboardHistoryTool(bridge).spec(),
        ClipboardHasTextTool(bridge).spec(),
        ClipboardClearTool(bridge).spec(),
    )
}

private class ClipboardReadTool(private val bridge: ClipboardBridge) {
    fun spec(): McpToolSpec = McpToolSpec(
        name = "clipboard_read",
        description = "Returns the current primary clipboard text. " +
            "Empty string when no text clip is present.",
        inputSchema = objectSchema(emptyMap()),
    ) { _ ->
        val text = bridge.readText()
        McpToolResult.Text(text)
    }
}

private class ClipboardWriteTool(private val bridge: ClipboardBridge) {
    fun spec(): McpToolSpec = McpToolSpec(
        name = "clipboard_write",
        description = "Replaces the primary clipboard text with the given string. " +
            "The value is also added to the in-memory recent history.",
        inputSchema = objectSchema(
            properties = mapOf(
                "text" to stringProp(description = "Text to place on the clipboard"),
            ),
            required = listOf("text"),
        ),
    ) { args ->
        val text = args.text() ?: return@McpToolSpec McpToolResult.Error(
            McpToolResult.ErrorCode.INVALID_ARGS,
            "missing required string 'text'",
        )
        bridge.writeText(text)
        McpToolResult.Json(buildJsonObject { put("ok", true) })
    }
}

private class ClipboardHistoryTool(private val bridge: ClipboardBridge) {
    fun spec(): McpToolSpec = McpToolSpec(
        name = "clipboard_history",
        description = "Returns up to N most recent text values written via " +
            "clipboard_write (newest first). Defaults to 8.",
        inputSchema = objectSchema(
            properties = mapOf(
                "limit" to com.meshlit.core.mcp.integerProp(description = "Max entries (1-32)"),
            ),
        ),
    ) { args ->
        val obj = args as? JsonObject
        val limit = obj?.get("limit")?.jsonPrimitive?.content?.toIntOrNull() ?: 8
        val recent = bridge.recent(limit.coerceIn(1, 32))
        McpToolResult.Json(buildJsonObject {
            put("count", recent.size)
            put("entries", buildJsonArray {
                recent.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
        })
    }
}

private class ClipboardHasTextTool(private val bridge: ClipboardBridge) {
    fun spec(): McpToolSpec = McpToolSpec(
        name = "clipboard_has_text",
        description = "Returns true if the primary clipboard currently holds text.",
        inputSchema = objectSchema(emptyMap()),
    ) { _ ->
        McpToolResult.Json(buildJsonObject { put("has_text", bridge.hasText()) })
    }
}

private class ClipboardClearTool(private val bridge: ClipboardBridge) {
    fun spec(): McpToolSpec = McpToolSpec(
        name = "clipboard_clear",
        description = "Replaces the primary clipboard text with an empty string. " +
            "Used to wipe sensitive values after pasting.",
        inputSchema = objectSchema(emptyMap()),
    ) { _ ->
        bridge.writeText("")
        McpToolResult.Json(buildJsonObject { put("ok", true) })
    }
}

private fun JsonElement.text(): String? =
    (this as? JsonObject)?.get("text")?.jsonPrimitive?.content

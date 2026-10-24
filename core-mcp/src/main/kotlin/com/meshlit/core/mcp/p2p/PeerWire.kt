package com.meshlit.core.mcp.p2p
import kotlinx.serialization.json.*
/** Reject excessive nesting before parsing any untrusted peer/WebView packet. */
object PeerWire {
    fun objectValue(value: String): JsonObject {
        require(value.length <= 65536)
        var depth = 0; var quoted = false; var escaped = false
        value.forEach { c ->
            if (quoted) { if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false }
            else when (c) { '"' -> quoted = true; '{', '[' -> { depth++; require(depth <= 16) }; '}', ']' -> { depth--; require(depth >= 0) } }
        }
        require(depth == 0 && !quoted)
        return Json.parseToJsonElement(value) as? JsonObject ?: error("Peer object required")
    }
}

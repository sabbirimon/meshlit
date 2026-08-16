package com.meshlit.core.mcp.builtin

import com.meshlit.core.mcp.McpToolResult
import com.meshlit.core.mcp.McpToolSpec
import com.meshlit.core.mcp.booleanProp
import com.meshlit.core.mcp.integerProp
import com.meshlit.core.mcp.objectSchema
import com.meshlit.core.mcp.stringProp
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Phase 4.x — Web-fetch MCP tools.
 *
 * Lets the agent pull a single HTTP(S) URL and get the body back
 * as plain text. Wraps the public surface of the open-source
 * [`mcp-server-fetch`](https://github.com/modelcontextprotocol/servers/tree/main/src/fetch)
 * server (1 tool) but runs in-process via [WebFetchBridge].
 *
 * The bridge is a thin abstraction over `HttpURLConnection` so
 * tests can swap in a fake (no real network calls).
 *
 * Hard rules:
 *  - HTTPS only by default; http:// is allowed but only when
 *    `allow_http=true` is passed explicitly. We don't want the
 *    agent silently exfiltrating over plain http.
 *  - 1 MiB response cap. Larger responses are truncated with a
 *    `[... truncated, 1 MiB cap ...]` marker.
 *  - 10 s connect + read timeout. Aggressive by design — the
 *    agent should not block on a slow server.
 */
interface WebFetchBridge {
    suspend fun fetch(
        url: String,
        maxBytes: Int,
        timeoutMs: Int,
        allowHttp: Boolean,
    ): WebFetchResult

    data class WebFetchResult(
        val status: Int,
        val contentType: String?,
        val body: String,
        val truncated: Boolean,
        val finalUrl: String?,
    )
}

/** No-op bridge used when the app hasn't wired one yet. */
object NoOpWebFetchBridge : WebFetchBridge {
    override suspend fun fetch(
        url: String,
        maxBytes: Int,
        timeoutMs: Int,
        allowHttp: Boolean,
    ): WebFetchBridge.WebFetchResult =
        WebFetchBridge.WebFetchResult(
            status = 0,
            contentType = null,
            body = "",
            truncated = false,
            finalUrl = null,
        )
}

class WebFetchMcpTools(
    private val bridge: WebFetchBridge = NoOpWebFetchBridge,
) {
    fun specs(): List<McpToolSpec> = listOf(
        FetchTool(bridge).spec(),
    )
}

private class FetchTool(private val bridge: WebFetchBridge) {
    fun spec(): McpToolSpec = McpToolSpec(
        name = "fetch_url",
        description = "Fetch a single URL via HTTP(S) and return the response body as text. " +
            "Default cap 1 MiB; pass max_bytes to override. " +
            "HTTPS-only by default; pass allow_http=true to permit plain http.",
        inputSchema = objectSchema(
            properties = mapOf(
                "url" to stringProp(description = "Absolute URL to fetch (http or https)"),
                "max_bytes" to integerProp(description = "Max bytes to read (default 1 MiB)"),
                "timeout_ms" to integerProp(description = "Connect+read timeout (default 10000)"),
                "allow_http" to booleanProp(description = "Permit plain http:// (default false)"),
            ),
            required = listOf("url"),
        ),
    ) { args ->
        val obj = args as? JsonObject
            ?: return@McpToolSpec McpToolResult.Error(
                McpToolResult.ErrorCode.INVALID_ARGS, "args must be a JSON object",
            )
        val url = obj.string("url")
            ?: return@McpToolSpec McpToolResult.Error(
                McpToolResult.ErrorCode.INVALID_ARGS, "missing required string 'url'",
            )
        val scheme = url.substringBefore("://", missingDelimiterValue = "").lowercase()
        if (scheme != "http" && scheme != "https") {
            return@McpToolSpec McpToolResult.Error(
                McpToolResult.ErrorCode.INVALID_ARGS,
                "url must start with http:// or https://, got '$scheme'",
            )
        }
        val allowHttp = obj.boolean("allow_http") ?: false
        if (scheme == "http" && !allowHttp) {
            return@McpToolSpec McpToolResult.Error(
                McpToolResult.ErrorCode.PERMISSION_DENIED,
                "plain http:// rejected — pass allow_http=true to override",
            )
        }
        val maxBytes = obj.int("max_bytes") ?: (1024 * 1024)
        val timeoutMs = obj.int("timeout_ms") ?: 10_000
        val r = bridge.fetch(url, maxBytes.coerceIn(64, 16 * 1024 * 1024), timeoutMs.coerceIn(100, 60_000), allowHttp)
        McpToolResult.Json(buildJsonObject {
            put("status", r.status)
            put("content_type", r.contentType?.let { kotlinx.serialization.json.JsonPrimitive(it) }
                ?: kotlinx.serialization.json.JsonNull)
            put("body", kotlinx.serialization.json.JsonPrimitive(r.body))
            put("truncated", r.truncated)
            put("final_url", r.finalUrl?.let { kotlinx.serialization.json.JsonPrimitive(it) }
                ?: kotlinx.serialization.json.JsonNull)
        })
    }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.boolean(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.let { content ->
        when (content.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

/**
 * Concrete [WebFetchBridge] backed by `HttpURLConnection`. The
 * in-process implementation so the user doesn't need to spawn a
 * Node.js subprocess to call `mcp-server-fetch`.
 */
class HttpUrlConnectionWebFetchBridge : WebFetchBridge {
    override suspend fun fetch(
        url: String,
        maxBytes: Int,
        timeoutMs: Int,
        allowHttp: Boolean,
    ): WebFetchBridge.WebFetchResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var conn: java.net.HttpURLConnection? = null
        try {
            conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MeshlitMCP/1.0")
            }
            val status = conn.responseCode
            val contentType = conn.contentType
            val stream = runCatching {
                if (status in 200..299) conn.inputStream else conn.errorStream
            }.getOrNull()
            if (stream == null) {
                return@withContext WebFetchBridge.WebFetchResult(
                    status = status,
                    contentType = contentType,
                    body = "",
                    truncated = false,
                    finalUrl = conn.url?.toString(),
                )
            }
            val bytes = stream.use { it.readNBytes(maxBytes) }
            val truncated = bytes.size >= maxBytes
            val body = String(bytes, Charsets.UTF_8)
            WebFetchBridge.WebFetchResult(
                status = status,
                contentType = contentType,
                body = body,
                truncated = truncated,
                finalUrl = conn.url?.toString(),
            )
        } catch (t: Throwable) {
            WebFetchBridge.WebFetchResult(
                status = 0,
                contentType = null,
                body = "fetch failed: ${t.message ?: t.javaClass.simpleName}",
                truncated = false,
                finalUrl = null,
            )
        } finally {
            conn?.disconnect()
        }
    }
}
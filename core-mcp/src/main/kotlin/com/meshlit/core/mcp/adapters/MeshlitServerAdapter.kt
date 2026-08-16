@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.meshlit.core.mcp.adapters

import com.meshlit.core.common.logger
import com.meshlit.core.mcp.McpClientPool
import com.meshlit.core.mcp.McpSdkAdapter
import com.meshlit.core.mcp.McpToolRegistry
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * HTTP/SSE transport that exposes the local [McpToolRegistry] over
 * the network so external MCP clients can connect IN to Meshlit.
 *
 * Endpoints:
 *  - `POST /mcp` — JSON-RPC 2.0 envelope (`initialize`, `tools/list`,
 *    `tools/call`, `ping`). Same shape as [McpSdkAdapter] so any
 *    MCP-aware client (Claude Desktop, IDE plugins, custom scripts)
 *    can drive Meshlit without a custom SDK.
 *  - `GET /mcp/health` — returns `{ "status": "ok" }`.
 *
 * Wire shape: every request body is a single JSON-RPC envelope. The
 * response is a JSON-RPC envelope with `result` (success) or `error`
 * (failure). Streaming tool calls are not yet supported — every
 * call is request/response.
 *
 * Why this lives here: it lets Claude Desktop, Ollama-compatible
 * tools, or any other MCP client treat a Meshlit device as a Tool
 * server. From the user's perspective this is "share my MCPs with
 * another app on the LAN."
 */
class MeshlitServerAdapter(
    private val registry: McpToolRegistry,
    private val pool: McpClientPool? = null,
    private val port: Int = 7700,
    host: String = "0.0.0.0",
) : NanoHTTPD(host, port) {

    private val log = logger("MeshlitServerAdapter")
    private val json = Json { ignoreUnknownKeys = true }
    private val sdk = McpSdkAdapter(registry, pool)

    init {
        // `McpSdkAdapter.handleRpc` is a suspend function — NanoHTTPD's
        // `serve` is synchronous. We bridge with `runBlocking`. This
        // is acceptable because tool handlers are short-lived (file
        // reads, shell exec with timeouts); long-running tools should
        // opt into streaming in a future revision.
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.trimEnd('/').ifBlank { "/" }
        return when {
            uri == "/mcp/health" -> jsonOk(buildJsonObject {
                put("status", JsonPrimitive("ok"))
            })
            uri == "/mcp" && session.method == Method.POST -> handleRpc(session)
            else -> notFound(uri)
        }
    }

    private fun handleRpc(session: IHTTPSession): Response {
        val body = readBody(session)
        val parsed = runCatching { json.parseToJsonElement(body) }.getOrElse { t ->
            return jsonError(-32700, "parse error: ${t.message}")
        }
        val envelope = parsed as? JsonObject ?: return jsonError(-32600, "envelope must be a JSON object")
        val resp = runBlocking { sdk.handleRpc(envelope) }
        return jsonOk(resp)
    }

    private fun readBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        try {
            session.parseBody(files)
        } catch (_: Throwable) {
            // Fall through; we'll read whatever made it into `files` or `session.parameters`.
        }
        // POST body lands in `files` under a synthetic name when
        // content-type is `application/json`; otherwise it sits in
        // `session.parameters` as the "postData" key.
        val fromFiles = files["postData"]
        if (fromFiles != null) return fromFiles
        val fromParams = session.parameters["postData"]?.firstOrNull()
        if (fromParams != null) return fromParams
        return session.queryParameterString.orEmpty()
    }

    private fun jsonOk(body: JsonObject): Response {
        val raw = json.encodeToString(JsonObject.serializer(), body)
        return newFixedLengthResponse(Response.Status.OK, "application/json", raw)
    }

    private fun jsonError(code: Int, message: String): Response =
        jsonOk(buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", JsonPrimitive(null))
            put("error", buildJsonObject {
                put("code", JsonPrimitive(code))
                put("message", JsonPrimitive(message))
            })
        })

    private fun notFound(uri: String): Response {
        log.warn("meshlit.http.404", "unknown path", mapOf("uri" to uri))
        return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            "application/json",
            """{"error":"not_found","path":"${uri.replace("\"", "\\\"")}"}""",
        )
    }

    private fun put(builder: kotlinx.serialization.json.JsonObjectBuilder, key: String, value: JsonPrimitive) {
        builder.put(key, value)
    }

    private fun buildJsonObject(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
        kotlinx.serialization.json.buildJsonObject(block)
}

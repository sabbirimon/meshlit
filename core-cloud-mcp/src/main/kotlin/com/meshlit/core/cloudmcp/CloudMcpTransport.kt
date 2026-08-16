package com.meshlit.core.cloudmcp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * SSE + JSON-RPC transport. One transport per [CloudMcpSession].
 *
 * Cloud-MCP servers are remote over HTTPS — unlike the in-process
 * `:core-mcp` (NanoHTTPD subprocess) transport. Wire format:
 *
 *   request:  POST {baseUrl}  body = JSON-RPC envelope
 *                              (initialize / tools/list / tools/call)
 *   response: 200 with `text/event-stream` body
 *                              events = JSON-RPC responses / errors
 *
 * Inbound events are fan-out into [events] as [McpEvent.ToolResult]
 * for `tools/call` responses and lifecycle markers for protocol
 * handshakes. The transport itself is dumb — it doesn't know what
 * a "tool" is. Higher layers ([ToolRegistry], [CloudMcpCoordinator])
 * interpret the events.
 *
 * SSE parsing: hand-rolled (see [SseParser]) to stay consistent
 * with the existing `RemoteInferenceClient.kt:136-174` pattern —
 * the project doesn't depend on `okhttp3.sse`.
 */
class CloudMcpTransport(
    private val httpClient: OkHttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) {
    private val _events = MutableSharedFlow<McpEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    /** Inbound events from the MCP server. */
    val events: Flow<McpEvent> = _events.asSharedFlow()

    /** Currently open streaming call, if any. */
    @Volatile
    private var currentCall: Call? = null

    private val sseParser = SseParser()

    /**
     * Open a streaming connection to [baseUrl]. Sends a GET with
     * `Accept: text/event-stream`; the body is parsed line-by-line
     * via [SseParser] and each dispatched event is fanned into
     * [events].
     *
     * `providerId` is stamped on every inbound event so the
     * coordinator can fan out to subscribers.
     */
    fun connect(providerId: String, baseUrl: String, headers: Map<String, String>) {
        val request = Request.Builder()
            .url(baseUrl)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .get()
            .build()

        currentCall?.cancel()
        sseParser
        val call = httpClient.newCall(request)
        currentCall = call

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                _events.tryEmit(
                    McpEvent.Error(
                        providerId = providerId,
                        message = e.message ?: "stream failure",
                        terminal = true,
                    ),
                )
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    _events.tryEmit(
                        McpEvent.Error(
                            providerId = providerId,
                            message = "HTTP ${response.code} ${response.message}",
                            terminal = true,
                        ),
                    )
                    response.close()
                    return
                }
                _events.tryEmit(McpEvent.Connected(providerId, tools = emptyList()))
                val source = response.body?.source()
                if (source == null) {
                    _events.tryEmit(
                        McpEvent.Error(
                            providerId = providerId,
                            message = "empty body",
                            terminal = true,
                        ),
                    )
                    return
                }
                try {
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        sseParser.feed(line).forEach { ev ->
                            handleSseFrame(providerId, ev)
                        }
                    }
                    sseParser.flush()?.let { handleSseFrame(providerId, it) }
                } catch (e: IOException) {
                    _events.tryEmit(
                        McpEvent.Error(
                            providerId = providerId,
                            message = "stream closed: ${e.message}",
                            terminal = true,
                        ),
                    )
                } finally {
                    response.close()
                    _events.tryEmit(McpEvent.Disconnected(providerId))
                }
            }
        })
    }

    /**
     * Build the JSON-RPC envelope for [method]. The transport
     * doesn't itself post the envelope — callers (the LLM
     * orchestrator or a one-shot handshake) use a separate
     * OkHttp POST so the streaming channel stays one-way. This
     * method exists so tests can verify envelope shape and so
     * the [CloudMcpSession] doesn't reinvent the JSON builder.
     */
    fun buildRequest(
        method: String,
        params: JsonObject,
        requestId: Long,
    ): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", requestId)
        put("method", method)
        put("params", params)
    }

    /**
     * One-shot POST for handshake / tools/list / tools/call that
     * don't stream a response. Returns the parsed JSON body or
     * `null` on transport failure.
     */
    fun postJson(
        baseUrl: String,
        headers: Map<String, String>,
        envelope: JsonObject,
    ): JsonObject? {
        val mediaType = "application/json".toMediaType()
        val body = json.encodeToString(JsonObject.serializer(), envelope)
            .toRequestBody(mediaType)
        val request = Request.Builder()
            .url(baseUrl)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(body)
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val text = response.body?.string() ?: return null
                json.parseToJsonElement(text) as? JsonObject
            }
        } catch (e: IOException) {
            null
        }
    }

    fun close() {
        currentCall?.cancel()
        currentCall = null
    }

    private fun handleSseFrame(providerId: String, ev: SseEvent) {
        if (ev.data.isBlank()) return
        val parsed: JsonElement = try {
            json.parseToJsonElement(ev.data)
        } catch (e: IOException) {
            _events.tryEmit(
                McpEvent.Error(
                    providerId = providerId,
                    message = "Malformed SSE frame: ${e.message}",
                ),
            )
            return
        }
        // For v1 we don't dispatch individual frame events — the
        // [CloudMcpSession] watches [events] and correlates by
        // request id. Surfacing the raw frame is enough here.
        // (Future: route `tools/list_changed` notifications.)
    }
}

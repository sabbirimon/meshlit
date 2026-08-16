package com.meshlit.core.cloudmcp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * One session per cloud provider. Owns the transport, the
 * `initialize` → `tools/list` handshake state machine, and the
 * per-call correlation between the LLM's `tool_calls` request id
 * and the eventual `ToolResult` event.
 *
 * Lifecycle:
 *  1. `connect()` — opens the SSE stream; first inbound frame is
 *     a `Connected` event.
 *  2. `handshake()` — POSTs `initialize` + `tools/list`; on
 *     success the registered tools populate the [ToolRegistry].
 *  3. `callTool(name, args)` — POSTs `tools/call`; returns the
 *     result body.
 *
 * The session also surfaces inbound events filtered to its own
 * provider id through [events].
 */
class CloudMcpSession(
    val providerId: String,
    private val transport: CloudMcpTransport,
    private val registry: ToolRegistry,
) {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Inbound events scoped to this provider. */
    val events: Flow<McpEvent> = transport.events.filter { it.providerId == providerId }

    fun connect(config: ProviderConfig, credential: String?) {
        val headers = mutableMapOf<String, String>()
        when (config.authKind) {
            AuthKind.BearerToken -> credential?.let { headers["Authorization"] = "Bearer $it" }
            AuthKind.OAuth2 -> credential?.let { headers["Authorization"] = "Bearer $it" }
            AuthKind.AwsIam -> {} // v1: not wired; transport will fail with explicit message
            AuthKind.None -> {}
        }
        _state.update { it.copy(connection = ConnectionState.Connecting) }
        transport.connect(providerId = providerId, baseUrl = config.baseUrl, headers = headers)
    }

    /**
     * Run the JSON-RPC handshake (`initialize` + `tools/list`).
     * On success populates the [ToolRegistry] and flips
     * [SessionState.connection] to [ConnectionState.Connected].
     *
     * Pure one-shot — does not own a coroutine scope. Caller is
     * responsible for the scope.
     */
    fun handshake(config: ProviderConfig, credential: String?): List<McpTool> {
        val headers = mutableMapOf<String, String>()
        when (config.authKind) {
            AuthKind.BearerToken -> credential?.let { headers["Authorization"] = "Bearer $it" }
            AuthKind.OAuth2 -> credential?.let { headers["Authorization"] = "Bearer $it" }
            AuthKind.AwsIam -> {
                _state.update {
                    it.copy(connection = ConnectionState.Error)
                }
                return emptyList()
            }
            AuthKind.None -> {}
        }
        val init = transport.postJson(
            baseUrl = config.baseUrl,
            headers = headers,
            envelope = transport.buildRequest(
                method = "initialize",
                params = buildJsonObject {
                    put("protocolVersion", "2024-11-05")
                    put("capabilities", buildJsonObject {})
                    put(
                        "clientInfo",
                        buildJsonObject {
                            put("name", "meshlit")
                            put("version", "1.0")
                        },
                    )
                },
                requestId = 1L,
            ),
        ) ?: run {
            _state.update { it.copy(connection = ConnectionState.Error) }
            return emptyList()
        }

        val listResp = transport.postJson(
            baseUrl = config.baseUrl,
            headers = headers,
            envelope = transport.buildRequest(
                method = "tools/list",
                params = buildJsonObject {},
                requestId = 2L,
            ),
        )
        val tools = listResp
            ?.get("result")
            ?.jsonObject
            ?.get("tools")
            ?.jsonArray
            ?.mapNotNull { node ->
                val obj = node.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val desc = obj["description"]?.jsonPrimitive?.content ?: ""
                val schema = obj["inputSchema"]?.jsonObject ?: buildJsonObject {}
                McpTool(name = name, description = desc, inputSchema = schema, providerId = providerId)
            }
            .orEmpty()

        registry.putAll(providerId, tools)
        _state.update { it.copy(connection = ConnectionState.Connected) }
        return tools
    }

    /**
     * Invoke a single tool by name. Synchronous — the cloud-MCP
     * server's `tools/call` is a normal POST that returns the
     * full result (not a streaming delta). The [McpEvent.ToolResult]
     * is also emitted on the inbound flow for the agent loop UI.
     */
    fun callTool(
        config: ProviderConfig,
        credential: String?,
        name: String,
        args: JsonObject,
        callId: String,
    ): McpEvent.ToolResult {
        val headers = mutableMapOf<String, String>()
        when (config.authKind) {
            AuthKind.BearerToken -> credential?.let { headers["Authorization"] = "Bearer $it" }
            AuthKind.OAuth2 -> credential?.let { headers["Authorization"] = "Bearer $it" }
            AuthKind.AwsIam, AuthKind.None -> {}
        }
        val response = transport.postJson(
            baseUrl = config.baseUrl,
            headers = headers,
            envelope = transport.buildRequest(
                method = "tools/call",
                params = buildJsonObject {
                    put("name", name)
                    put("arguments", args)
                },
                requestId = System.nanoTime(),
            ),
        )
        return if (response == null) {
            McpEvent.ToolResult(
                providerId = providerId,
                callId = callId,
                ok = false,
                body = "transport error",
            )
        } else if (response["error"] != null) {
            McpEvent.ToolResult(
                providerId = providerId,
                callId = callId,
                ok = false,
                body = response["error"].toString(),
            )
        } else {
            McpEvent.ToolResult(
                providerId = providerId,
                callId = callId,
                ok = true,
                body = (response["result"]?.toString()).orEmpty(),
            )
        }
    }

    fun close() {
        transport.close()
        registry.removeProvider(providerId)
        _state.update { it.copy(connection = ConnectionState.Disconnected) }
    }
}

/** One provider's connection state — surfaced by the hub UI. */
data class SessionState(
    val connection: ConnectionState = ConnectionState.Idle,
)

enum class ConnectionState {
    Idle,
    Connecting,
    Connected,
    Disconnected,
    Error,
}
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.meshlit.core.mcp

import com.meshlit.core.common.logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Wire-protocol compatibility shim. When the Anthropic MCP Kotlin
 * SDK (`io.modelcontextprotocol:kotlin-sdk`) is on the classpath
 * the app installs an SDK-backed transport that forwards MCP
 * `tools/list` and `tools/call` requests straight to this class.
 *
 * When the SDK is **not** on the classpath — the default for the
 * pure core-mcp unit tests — the adapter exposes a JSON-RPC-shaped
 * façade that the app can serve via NanoHTTPD or Ktor. That keeps
 * the registry / built-in / Hermes / user-added paths working
 * without forcing a runtime dependency.
 *
 * The adapter is invoked by:
 *  - the embedded HTTP server (when a remote peer calls `tools/list`)
 *  - the Hermes bridge (when the local model emits <tool_call>)
 *  - test fixtures (when a JUnit test wants to verify the wire shape)
 *
 * In every case the contract is the same: a JSON-RPC 2.0 envelope
 * with `method`, `params`, and `id`, and a JSON-RPC 2.0 response
 * with either `result` or `error`.
 */
class McpSdkAdapter(
    private val registry: McpToolRegistry,
    private val pool: McpClientPool? = null,
) {
    private val log = logger("McpSdkAdapter")
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Dispatch a JSON-RPC 2.0 envelope to the registry.
     *
     * Supported methods:
     *  - `initialize`                → returns server info + capabilities
     *  - `tools/list`                → returns the registered tools
     *  - `tools/call`                → invokes one tool (`name` + `arguments`)
     *  - `ping`                      → returns an empty object
     *
     * Anything else returns a JSON-RPC `MethodNotFound` error.
     */
    suspend fun handleRpc(envelope: JsonObject): JsonObject {
        val method = (envelope["method"] as? JsonPrimitive)?.content
            ?: return err(-32600, "missing 'method'")
        val id = envelope["id"] ?: JsonPrimitive(null)
        val params = (envelope["params"] as? JsonObject) ?: JsonObject(emptyMap())
        return when (method) {
            "initialize" -> ok(id, buildJsonObject {
                put("serverInfo", buildJsonObject {
                    put("name", "meshlit-mcp")
                    put("version", "0.1.0")
                })
                put("capabilities", buildJsonObject {
                    put("tools", buildJsonObject { put("listChanged", false) })
                })
            })
            "ping" -> ok(id, buildJsonObject { })
            "tools/list" -> ok(id, buildJsonObject {
                put("tools", kotlinx.serialization.json.buildJsonArray {
                    registry.list().forEach { tool ->
                        add(buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("inputSchema", tool.inputSchema)
                            put("origin", tool.origin.name)
                        })
                    }
                })
            })
            "tools/call" -> {
                val name = (params["name"] as? JsonPrimitive)?.content
                    ?: return err(-32602, "tools/call: missing 'name'")
                val arguments = params["arguments"] ?: JsonObject(emptyMap())
                val result = if (name.startsWith("user.") && pool != null) {
                    pool.invoke(name, arguments)
                } else {
                    registry.invoke(McpToolRequest(name = name, arguments = arguments))
                }
                ok(id, registry.toWireResponse(result))
            }
            else -> {
                log.warn("mcp.rpc.unknown", "method not found", mapOf("method" to method))
                err(-32601, "method not found: $method")
            }
        }
    }

    private fun ok(id: JsonElement, result: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }

    private fun err(code: Int, message: String): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", JsonPrimitive(null))
        put("error", buildJsonObject {
            put("code", code)
            put("message", message)
        })
    }
}

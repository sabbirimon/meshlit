package com.meshlit.core.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Static description of a single MCP tool. `inputSchema` follows the
 * JSON-Schema convention the MCP protocol uses — `{type: "object",
 * properties: {...}, required: [...]}` — so an LLM can introspect
 * the shape and call [McpToolRegistry.invoke] with the right
 * argument bag.
 *
 * The tool body itself lives in [handler]; the registry calls
 * `handler(args)` and wraps the result into [McpToolResult].
 *
 * [origin] tags whether the tool was compiled into the APK
 * ([Origin.BuiltIn]) or added at runtime by the user
 * ([Origin.UserAdded]). User-added tools spawn an external process
 * (per [com.meshlit.core.mcp.UserMcpServer]) and proxy JSON-RPC
 * over stdin/stdout; built-in tools run in-process. The dispatcher
 * treats them uniformly — see [McpToolRegistry.invoke].
 */
data class McpToolSpec(
    val name: String,
    val description: String,
    val inputSchema: JsonElement = EMPTY_OBJECT_SCHEMA,
    val origin: Origin = Origin.BuiltIn,
    val requiredResource: String? = null,
    val handler: suspend (args: JsonElement) -> McpToolResult,
) {
    enum class Origin { BuiltIn, UserAdded }

    init {
        require(name.isNotBlank()) { "tool name must not be blank" }
        require(!name.contains(' ')) { "tool name must not contain spaces: '$name'" }
    }
}

/**
 * Tool invocation result. The success branch carries either a JSON
 * value (structured data — file lists, model metadata) or a plain
 * `String` (free-form text — shell command stdout). Errors are
 * categorised so the LLM can react (retry vs. give up).
 */
sealed class McpToolResult {
    data class Text(val text: String) : McpToolResult()
    data class Json(val value: JsonElement) : McpToolResult()
    data class Error(val code: ErrorCode, val message: String) : McpToolResult()

    enum class ErrorCode(val wireValue: String) {
        INVALID_ARGS("invalid_args"),
        NOT_FOUND("not_found"),
        PERMISSION_DENIED("permission_denied"),
        TIMEOUT("timeout"),
        EXEC_FAILED("exec_failed"),
        IO_ERROR("io_error"),
    }
}

/** Convenience builder for the most common schema: a flat object
 *  whose `properties` map is name → JSON-Schema type. */
fun objectSchema(
    properties: Map<String, JsonElement>,
    required: List<String> = emptyList(),
): JsonElement = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        properties.forEach { (k, v) -> put(k, v) }
    })
    if (required.isNotEmpty()) {
        put("required", kotlinx.serialization.json.buildJsonArray {
            required.forEach { add(JsonPrimitive(it)) }
        })
    }
}

/** Schema helper: a primitive type with an optional description. */
fun stringProp(description: String? = null, enumValues: List<String>? = null): JsonElement =
    buildJsonObject {
        put("type", "string")
        if (description != null) put("description", description)
        if (enumValues != null) put("enum", kotlinx.serialization.json.buildJsonArray {
            enumValues.forEach { add(JsonPrimitive(it)) }
        })
    }

fun integerProp(description: String? = null): JsonElement =
    buildJsonObject {
        put("type", "integer")
        if (description != null) put("description", description)
    }

fun booleanProp(description: String? = null): JsonElement =
    buildJsonObject {
        put("type", "boolean")
        if (description != null) put("description", description)
    }

private val EMPTY_OBJECT_SCHEMA: JsonElement = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject { })
    put("required", kotlinx.serialization.json.buildJsonArray { })
}

/** Wrapper for the wire-level MCP request body. The MCP spec uses
 *  JSON-RPC 2.0; this data class is the minimum we need to dispatch
 *  to a tool. */
@Serializable
data class McpToolRequest(
    val name: String,
    val arguments: JsonElement = JsonNull,
)

/** Wrapper for the wire-level MCP response body. `isError` is set
 *  when the tool returned [McpToolResult.Error] — distinct from a
 *  JSON-RPC-level transport error (`McpToolResponse.error`). */
@Serializable
data class McpToolResponse(
    val content: JsonElement,
    val isError: Boolean = false,
)

package com.meshlit.core.mcp

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * In-process tool registry. Dispatches a wire-level [McpToolRequest]
 * to the matching [McpToolSpec.handler] and wraps the result.
 *
 * The registry is **synchronous at the surface** but each handler is
 * `suspend`, so an LLM-side dispatcher that wants to fan out a
 * batch can run them on `Dispatchers.IO`. Per-handler exceptions
 * are caught and translated into [McpToolResult.Error] — handlers
 * that throw are bugs, not failures.
 *
 * Built-in tools register themselves at startup; user-added tools
 * register on demand from [com.meshlit.core.mcp.McpUserRepository].
 * The two paths use the same [register] entry point so dispatch is
 * uniform.
 */
class McpToolRegistry {

    private val log = logger("McpToolRegistry")

    private val tools = LinkedHashMap<String, McpToolSpec>()

    /** All registered tools, sorted by name. Iteration order is the
     *  natural sort — handy for the `tools/list` MCP wire response. */
    fun list(): List<McpToolSpec> = tools.values.sortedBy { it.name }

    /** Look up a tool by name. */
    fun get(name: String): McpToolSpec? = tools[name]

    /** Register a tool. Replaces any existing tool with the same name
     *  (the registry is single-source-of-truth; no two tools share a
     *  name). */
    fun register(spec: McpToolSpec) {
        val prior = tools.put(spec.name, spec)
        if (prior != null && prior.origin == McpToolSpec.Origin.UserAdded
            && spec.origin == McpToolSpec.Origin.BuiltIn
        ) {
            log.warn(
                "mcp.registry.overwrite",
                "user-added tool shadowed by built-in",
                mapOf("name" to spec.name),
            )
        }
        log.info(
            "mcp.registry.add",
            "tool registered",
            mapOf("name" to spec.name, "origin" to spec.origin.name),
        )
    }

    /** Bulk-register, e.g. at startup. */
    fun registerAll(specs: Iterable<McpToolSpec>) {
        specs.forEach { register(it) }
    }

    /** Drop a tool. No-op if the tool was never registered. */
    fun unregister(name: String) {
        if (tools.remove(name) != null) {
            log.info("mcp.registry.remove", "tool removed", mapOf("name" to name))
        }
    }

    /** Dispatch [request] to the matching handler. */
    suspend fun invoke(request: McpToolRequest): McpToolResult {
        val tool = tools[request.name]
            ?: return McpToolResult.Error(
                McpToolResult.ErrorCode.NOT_FOUND,
                "tool '${request.name}' is not registered",
            )
        // Args validation is best-effort — we check that `request.arguments`
        // is either JsonNull or a JsonObject, and that any keys listed in
        // `required` are present. Per-tool semantic validation (e.g.
        // "path must be inside allowed roots") is the handler's job.
        if (!validateArgs(tool, request.arguments)) {
            return McpToolResult.Error(
                McpToolResult.ErrorCode.INVALID_ARGS,
                "arguments for '${request.name}' do not match schema",
            )
        }
        return try {
            tool.handler(request.arguments)
        } catch (t: Throwable) {
            log.warn(
                "mcp.invoke.fail",
                "tool handler threw",
                mapOf("tool" to tool.name, "err" to (t.message ?: t.javaClass.simpleName)),
            )
            McpToolResult.Error(
                McpToolResult.ErrorCode.EXEC_FAILED,
                t.message ?: t.javaClass.simpleName,
            )
        }
    }

    /** Build a JSON-RPC 2.0 `result` payload from a [McpToolResult]. */
    fun toWireResponse(result: McpToolResult): JsonObject = when (result) {
        is McpToolResult.Text -> JsonObject(mapOf(
            "content" to JsonObject(mapOf(
                "type" to JsonPrimitive("text"),
                "text" to JsonPrimitive(result.text),
            )),
            "isError" to JsonPrimitive(false),
        ))
        is McpToolResult.Json -> JsonObject(mapOf(
            "content" to result.value,
            "isError" to JsonPrimitive(false),
        ))
        is McpToolResult.Error -> JsonObject(mapOf(
            "content" to JsonObject(mapOf(
                "type" to JsonPrimitive("text"),
                "text" to JsonPrimitive("${result.code.wireValue}: ${result.message}"),
            )),
            "isError" to JsonPrimitive(true),
        ))
    }

    private fun validateArgs(tool: McpToolSpec, args: JsonElement): Boolean {
        // Null/empty args are always OK — tools with no required
        // arguments accept JsonNull.
        if (args !is JsonObject) return args.toString() == "null" || args.toString() == "{}"
        val required = tool.inputSchema
            .let { (it as? JsonObject)?.get("required") }
            ?.let { (it as? kotlinx.serialization.json.JsonArray)?.toList() ?: emptyList() }
            ?: emptyList()
        return required.all { req ->
            val key = (req as? JsonPrimitive)?.content ?: return@all true
            args.containsKey(key)
        }
    }

    /** Convenience wrapper that returns the JSON-RPC `result` directly,
     *  ready to be sent over the wire. */
    suspend fun dispatchToWire(request: McpToolRequest): MeshlitResult<JsonObject> {
        val r = invoke(request)
        return MeshlitResult.Success(toWireResponse(r))
    }
}
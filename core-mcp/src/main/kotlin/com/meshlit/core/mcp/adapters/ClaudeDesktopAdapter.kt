@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.meshlit.core.mcp.adapters

import com.meshlit.core.common.logger
import com.meshlit.core.mcp.McpClientPool
import com.meshlit.core.mcp.McpSdkAdapter
import com.meshlit.core.mcp.McpToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Stdio JSON-RPC 2.0 transport that lets Claude Desktop (or any
 * MCP-aware client that speaks the stdio protocol) connect to a
 * Meshlit device as a tool server.
 *
 * Wire format: one JSON object per line. The client writes a line,
 * the server writes one line back. This is the same protocol Claude
 * Desktop speaks when configured with an "mcpServers" entry in
 * `claude_desktop_config.json`:
 *
 * ```json
 * {
 *   "mcpServers": {
 *     "meshlit": {
 *       "command": "meshlit-mcp-bridge",
 *       "args": ["--stdio"]
 *     }
 *   }
 * }
 * ```
 *
 * On startup we read stdin in a loop, hand each line to
 * [McpSdkAdapter.handleRpc], and write the response to stdout. The
 * `McpClientPool` is plumbed through so user-added MCP servers are
 * also reachable from Claude Desktop (their tools show up under the
 * `<server-name>.<tool>` namespace).
 *
 * The adapter is intentionally JUnit-friendly: pass a custom
 * [LineReader] + [LineWriter] (or skip start() and call [handleLine]
 * directly) so tests can drive it without a real stdin/stdout.
 */
class ClaudeDesktopAdapter(
    private val registry: McpToolRegistry,
    private val pool: McpClientPool? = null,
    private val reader: LineReader = StdioLineReader(),
    private val writer: LineWriter = StdioLineWriter(),
) {
    private val log = logger("ClaudeDesktopAdapter")
    private val json = Json { ignoreUnknownKeys = true }
    private val sdk = McpSdkAdapter(registry, pool)
    private var loopJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One line of inbound JSON (no trailing newline). */
    fun interface LineReader {
        fun readLine(): String?
    }

    /** One line of outbound JSON (no trailing newline). */
    fun interface LineWriter {
        fun writeLine(line: String)
    }

    /**
     * Start the read/respond loop. Blocks until [stop] is called or
     * stdin returns null (EOF). On Android this is what the host
     * process uses to bridge to a TCP/USB connection.
     */
    fun start() {
        if (loopJob != null) return
        loopJob = scope.launch {
            log.info("claude.stdio.start", "listening on stdin")
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val resp = handleLine(line)
                if (resp != null) writer.writeLine(resp)
            }
            log.info("claude.stdio.stop", "EOF on stdin")
        }
    }

    /** Cancel the read loop and drain. */
    fun stop() {
        scope.cancel()
        loopJob = null
    }

    /**
     * Single-shot handler: parse `line`, dispatch through the SDK,
     * return the serialized response (or null for notifications).
     * Public so tests can drive the adapter without spawning stdin.
     */
    fun handleLine(line: String): String? {
        val parsed = runCatching { json.parseToJsonElement(line) }.getOrElse { t ->
            return errorEnvelope(null, -32700, "parse error: ${t.message}")
        }
        val envelope = parsed as? JsonObject ?: return errorEnvelope(null, -32600, "envelope must be a JSON object")
        val resp = runBlocking { sdk.handleRpc(envelope) }
        // JSON-RPC notifications do not get a response. The SDK
        // emits a response object for every inbound request; we
        // check the original `id` for null to detect notifications.
        val id = envelope["id"]
        val isNotification = id == null || id.toString() == "null"
        return if (isNotification) null else json.encodeToString(JsonObject.serializer(), resp)
    }

    private fun errorEnvelope(id: Any?, code: Int, message: String): String {
        val body = buildJsonObject {
            put("jsonrpc", kotlinx.serialization.json.JsonPrimitive("2.0"))
            put("id", kotlinx.serialization.json.JsonPrimitive(id?.toString().orEmpty()))
            put("error", buildJsonObject {
                put("code", kotlinx.serialization.json.JsonPrimitive(code))
                put("message", kotlinx.serialization.json.JsonPrimitive(message))
            })
        }
        return json.encodeToString(JsonObject.serializer(), body)
    }

    private fun buildJsonObject(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
        kotlinx.serialization.json.buildJsonObject(block)

    private fun put(builder: kotlinx.serialization.json.JsonObjectBuilder, key: String, value: kotlinx.serialization.json.JsonPrimitive) {
        builder.put(key, value)
    }
}

/** Default stdin reader. Wraps System.`in` so we can mock in tests. */
private class StdioLineReader : ClaudeDesktopAdapter.LineReader {
    private val buffered = System.`in`.bufferedReader()
    override fun readLine(): String? = buffered.readLine()
}

/** Default stdout writer. Wraps System.out. */
private class StdioLineWriter : ClaudeDesktopAdapter.LineWriter {
    private val out = System.out.bufferedWriter()
    override fun writeLine(line: String) {
        out.write(line)
        out.newLine()
        out.flush()
    }
}

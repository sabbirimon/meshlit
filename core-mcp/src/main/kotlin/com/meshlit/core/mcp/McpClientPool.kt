package com.meshlit.core.mcp

import com.meshlit.core.common.logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages a pool of external MCP server processes — one process per
 * [UserMcpServer] entry. Calls to user-added tools route through
 * [invoke] which marshals the JSON-RPC request over stdin and waits
 * for the JSON-RPC response on stdout.
 *
 * The pool is a long-lived singleton in the app. Servers are
 * spawned lazily on first invocation and reaped after a configurable
 * idle window. Errors that suggest the child process died are
 * surfaced as [McpToolResult.Error] and the next call respawns.
 *
 * This implementation speaks a minimal subset of the MCP JSON-RPC
 * protocol — enough for tool calls and tool listing. The
 * Anthropic `kotlin-sdk` adapter (see [McpSdkAdapter]) is layered on
 * top when the SDK is on the classpath; this class is the always-on
 * fallback.
 */
class McpClientPool(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val idleTimeoutMs: Long = 30_000,
    private val registry: McpToolRegistry,
    private val store: UserMcpServerStore? = null,
) {
    private val log = logger("McpClientPool")

    private val state = MutableStateFlow<Map<String, UserMcpServer>>(emptyMap())
    val configured: StateFlow<Map<String, UserMcpServer>> = state.asStateFlow()

    private val running = ConcurrentHashMap<String, RunningServer>()
    private val mutex = Mutex()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        // If the pool is constructed with a UserMcpServerStore, seed
        // the configured catalog from the store's persisted set on
        // first launch. The store also handles rehydration; we just
        // mirror whatever is already loaded into the pool.
        val seeded = store?.all
        if (seeded != null && seeded.isNotEmpty()) {
            state.value = seeded.associateBy { it.id }
        }
    }

    /** Replace the persisted catalog. New entries spawn on next call;
     *  removed entries are stopped. */
    suspend fun replaceCatalog(servers: List<UserMcpServer>) {
        mutex.withLock {
            val byId = servers.associateBy { it.id }
            state.value = byId
            // Tear down running processes for removed entries.
            running.keys.filter { it !in byId }.forEach { id ->
                stopInternal(id)
            }
        }
    }

    /** Upsert one entry. */
    suspend fun upsert(server: UserMcpServer) {
        mutex.withLock {
            state.value = state.value + (server.id to server)
        }
    }

    /** Remove one entry. */
    suspend fun remove(serverId: String) {
        mutex.withLock {
            state.value = state.value - serverId
            stopInternal(serverId)
        }
    }

    /** Invoke a user-added tool by its fully-qualified name
     *  (`<serverName>.<toolName>`). */
    suspend fun invoke(
        toolName: String,
        arguments: JsonElement,
    ): McpToolResult {
        val server = state.value.values.firstOrNull { it.namespaced(toolName.removePrefix("${it.name}.")) == toolName }
            ?: return McpToolResult.Error(
                McpToolResult.ErrorCode.NOT_FOUND,
                "no user-added server claims tool '$toolName'",
            )
        return withContext(dispatcher) {
            val running = ensureRunning(server)
            val raw: McpToolResult? = withTimeoutOrNull(server.timeoutMs.toLong()) {
                try {
                    val resp = sendRpc(running, "tools/call", buildJsonObject {
                        put("name", toolName.removePrefix("${server.name}."))
                        put("arguments", arguments)
                    })
                    val obj = resp as? JsonObject
                    val err = obj?.get("error")
                    if (err != null) {
                        McpToolResult.Error(
                            McpToolResult.ErrorCode.EXEC_FAILED,
                            err.toString(),
                        )
                    } else {
                        McpToolResult.Json(resp)
                    }
                } catch (t: Throwable) {
                    McpToolResult.Error(
                        McpToolResult.ErrorCode.EXEC_FAILED,
                        t.message ?: t.javaClass.simpleName,
                    )
                }
            }
            raw ?: McpToolResult.Error(
                McpToolResult.ErrorCode.TIMEOUT,
                "user-MCP '${server.name}' exceeded ${server.timeoutMs}ms",
            )
        }
    }

    /** Discover which tools a server exposes. The discovery result is
     *  merged into [registry] under the namespaced names. Safe to
     *  call repeatedly — re-discovery re-registers the same tools. */
    suspend fun discover(server: UserMcpServer): List<McpToolSpec> {
        val running = ensureRunning(server)
        val payload = withTimeoutOrNull(server.timeoutMs.toLong()) {
            runCatching {
                sendRpc(running, "tools/list", buildJsonObject { })
            }.getOrNull()
        } ?: return emptyList()
        val tools = (payload as? JsonObject)?.get("tools") as? kotlinx.serialization.json.JsonArray
            ?: return emptyList()
        return tools.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val description = (obj["description"] as? JsonPrimitive)?.content ?: ""
            val schema = obj["inputSchema"] ?: JsonObject(emptyMap())
            McpToolSpec(
                name = server.namespaced(name),
                description = description,
                inputSchema = schema,
                origin = McpToolSpec.Origin.UserAdded,
            ) { args ->
                invoke(server.namespaced(name), args)
            }.also { registry.register(it) }
        }
    }

    /** Stop all running servers — call from the application
     *  shutdown hook so we don't leak processes. */
    suspend fun shutdown() {
        mutex.withLock {
            running.keys.toList().forEach { stopInternal(it) }
        }
    }

    private suspend fun ensureRunning(server: UserMcpServer): RunningServer {
        // Fast path — already up.
        running[server.id]?.let { return it }
        return mutex.withLock {
            running[server.id] ?: startInternal(server).also { running[server.id] = it }
        }
    }

    private fun startInternal(server: UserMcpServer): RunningServer {
        log.info("mcp.pool.start", "spawning user MCP", mapOf("id" to server.id, "name" to server.name))
        val pb = ProcessBuilder(listOf(server.command) + server.args)
            .redirectErrorStream(false)
        server.env.forEach { (k, v) -> pb.environment()[k] = v }
        val proc = pb.start()
        return RunningServer(
            server = server,
            process = proc,
            stdoutReader = BufferedReader(InputStreamReader(proc.inputStream)),
            stderrReader = BufferedReader(InputStreamReader(proc.errorStream)),
        )
    }

    private fun stopInternal(serverId: String) {
        val r = running.remove(serverId) ?: return
        log.info("mcp.pool.stop", "stopping user MCP", mapOf("id" to serverId))
        runCatching { r.process.destroyForcibly() }
        runCatching { r.process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS) }
    }

    private fun sendRpc(running: RunningServer, method: String, params: JsonObject): JsonElement {
        val id = UUID.randomUUID().toString()
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        running.process.outputStream.bufferedWriter().use { w ->
            w.write(json.encodeToString(JsonObject.serializer(), request))
            w.newLine()
            w.flush()
        }
        // Read until the line with our `id` shows up. Other lines
        // (notifications) are skipped — we don't expect any in this
        // subset but the loop is robust.
        while (true) {
            val line = running.stdoutReader.readLine()
                ?: return JsonObject(mapOf("error" to JsonPrimitive("EOF from server")))
            val parsed = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull()
                ?: continue
            if ((parsed["id"] as? JsonPrimitive)?.content == id) {
                return parsed
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private data class RunningServer(
        val server: UserMcpServer,
        val process: Process,
        val stdoutReader: BufferedReader,
        val stderrReader: BufferedReader,
    )
}
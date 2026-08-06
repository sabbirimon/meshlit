package com.meshlit.core.mcp.builtin

import com.meshlit.core.common.logger
import com.meshlit.core.mcp.McpToolResult
import com.meshlit.core.mcp.McpToolSpec
import com.meshlit.core.mcp.integerProp
import com.meshlit.core.mcp.objectSchema
import com.meshlit.core.mcp.stringProp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Built-in MCP tools. Each is constructed as an [McpToolSpec] the
 * registry can dispatch to. The handlers are intentionally
 * minimal — the point of shipping these in core-mcp is to give the
 * user something useful out of the box (file browse, shell run,
 * model introspection) so they can wire their own Tool-role prompts
 * without standing up an external server.
 *
 * All three take a [FileSystemPolicy] so they can be configured
 * per-deployment — the default policy allows only `filesDir` reads,
 * the test policy can pin a temp folder.
 */
class FilesListTool(
    private val policy: FileSystemPolicy,
) {
    fun spec(): McpToolSpec = McpToolSpec(
        name = "files_list",
        description = "List a directory under the app sandbox. " +
            "Returns one entry per line: `dir\tname` or `file\tname\tsize\tmime`.",
        inputSchema = objectSchema(
            properties = mapOf(
                "path" to stringProp(
                    description = "Absolute directory path. Must be inside an allowed root.",
                ),
            ),
            required = listOf("path"),
        ),
    ) { args -> handle(args) }

    private suspend fun handle(args: JsonElement): McpToolResult {
        val obj = args as? JsonObject ?: return McpToolResult.Error(
            McpToolResult.ErrorCode.INVALID_ARGS,
            "expected an object with a `path` field",
        )
        val path = obj["path"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.Error(
                McpToolResult.ErrorCode.INVALID_ARGS,
                "missing `path`",
            )
        val file = File(path)
        when (policy.checkRead(file)) {
            is FileSystemPolicy.Decision.Deny -> return McpToolResult.Error(
                McpToolResult.ErrorCode.PERMISSION_DENIED,
                "path '$path' is not inside an allowed root",
            )
            is FileSystemPolicy.Decision.Allow -> Unit
        }
        if (!file.isDirectory) return McpToolResult.Error(
            McpToolResult.ErrorCode.IO_ERROR,
            "not a directory: $path",
        )
        val children = withContext(Dispatchers.IO) {
            file.listFiles()?.sortedBy { it.name.lowercase() }.orEmpty()
        }
        val lines = buildJsonArray {
            children.forEach { entry ->
                if (entry.name.startsWith(".")) return@forEach
                add(
                    if (entry.isDirectory) {
                        buildJsonObject {
                            put("kind", "dir")
                            put("name", entry.name)
                            put("path", entry.absolutePath)
                        }
                    } else {
                        buildJsonObject {
                            put("kind", "file")
                            put("name", entry.name)
                            put("path", entry.absolutePath)
                            put("sizeBytes", entry.length())
                        }
                    },
                )
            }
        }
        return McpToolResult.Json(lines)
    }
}

class FilesReadTool(
    private val policy: FileSystemPolicy,
) {
    fun spec(): McpToolSpec = McpToolSpec(
        name = "files_read",
        description = "Read a UTF-8 text file under the app sandbox. " +
            "Returns the file's contents. Files larger than `maxBytes` are rejected.",
        inputSchema = objectSchema(
            properties = mapOf(
                "path" to stringProp(description = "Absolute file path."),
                "maxBytes" to integerProp(
                    description = "Hard cap on bytes to read. Default 1 MiB.",
                ),
            ),
            required = listOf("path"),
        ),
    ) { args -> handle(args) }

    private suspend fun handle(args: JsonElement): McpToolResult {
        val obj = args as? JsonObject ?: return McpToolResult.Error(
            McpToolResult.ErrorCode.INVALID_ARGS,
            "expected an object with a `path` field",
        )
        val path = obj["path"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.Error(
                McpToolResult.ErrorCode.INVALID_ARGS,
                "missing `path`",
            )
        val maxBytes = obj["maxBytes"]?.jsonPrimitive?.intOrNull ?: DEFAULT_MAX_BYTES
        val file = File(path)
        when (policy.checkRead(file)) {
            is FileSystemPolicy.Decision.Deny -> return McpToolResult.Error(
                McpToolResult.ErrorCode.PERMISSION_DENIED,
                "path '$path' is not inside an allowed root",
            )
            is FileSystemPolicy.Decision.Allow -> Unit
        }
        if (!file.isFile) return McpToolResult.Error(
            McpToolResult.ErrorCode.IO_ERROR,
            "not a regular file: $path",
        )
        if (file.length() > maxBytes) return McpToolResult.Error(
            McpToolResult.ErrorCode.IO_ERROR,
            "file too large (${file.length()} bytes > $maxBytes)",
        )
        val textResult: McpToolResult = withContext(Dispatchers.IO) {
            try {
                McpToolResult.Text(file.readText(Charsets.UTF_8))
            } catch (t: Throwable) {
                McpToolResult.Error(
                    McpToolResult.ErrorCode.IO_ERROR,
                    t.message ?: t.javaClass.simpleName,
                )
            }
        }
        return textResult
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Int = 1024 * 1024
    }
}

/**
 * Run a shell command and capture its stdout/stderr/exit code.
 *
 * The `command` and `args` are passed through [ShellPolicy] which
 * decides whether the invocation is allowed. The default policy is
 * a static allowlist of binaries (`echo`, `cat`, `ls`, `wc`,
 * `head`, `tail`, `grep`, `find`, `stat`, `pwd`, `whoami`,
 * `uname`, `date`) — anything else is rejected before
 * `ProcessBuilder` is constructed.
 *
 * The handler times out after `timeoutMs` (default 5 s, cap 60 s)
 * so a runaway tool call can't wedge the registry.
 */
class ShellExecTool(
    private val policy: ShellPolicy,
) {
    private val log = logger("ShellExecTool")

    fun spec(): McpToolSpec = McpToolSpec(
        name = "shell_exec",
        description = "Run a shell command from the static allowlist " +
            "and return its stdout, stderr, and exit code. " +
            "Times out after `timeoutMs` (default 5000, cap 60000).",
        inputSchema = objectSchema(
            properties = mapOf(
                "command" to stringProp(
                    description = "The binary to invoke. Must be on the static allowlist.",
                ),
                "args" to objectSchema(
                    properties = mapOf(
                        "values" to objectSchema(
                            properties = mapOf(
                                "0" to stringProp(),
                            ),
                        ),
                    ),
                ),
                "timeoutMs" to integerProp(
                    description = "Timeout in milliseconds. Default 5000, max 60000.",
                ),
            ),
            required = listOf("command"),
        ),
    ) { args -> handle(args) }

    private suspend fun handle(args: JsonElement): McpToolResult {
        val obj = args as? JsonObject ?: return McpToolResult.Error(
            McpToolResult.ErrorCode.INVALID_ARGS,
            "expected an object with a `command` field",
        )
        val command = obj["command"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.Error(
                McpToolResult.ErrorCode.INVALID_ARGS,
                "missing `command`",
            )
        val rawArgs = obj["args"]?.jsonArray
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()
        val timeoutMs = obj["timeoutMs"]?.jsonPrimitive?.intOrNull
            ?.coerceIn(100, MAX_TIMEOUT_MS)
            ?: DEFAULT_TIMEOUT_MS

        when (val verdict = policy.check(command, rawArgs)) {
            is ShellPolicy.Decision.Deny -> return McpToolResult.Error(
                McpToolResult.ErrorCode.PERMISSION_DENIED,
                verdict.reason,
            )
            is ShellPolicy.Decision.Allow -> Unit
        }

        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs.toLong()) {
                runCatching {
                    val pb = ProcessBuilder(listOf(command) + rawArgs)
                        .redirectErrorStream(false)
                    val proc = pb.start()
                    val finished = proc.waitFor(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                    if (!finished) {
                        proc.destroyForcibly()
                        McpToolResult.Error(
                            McpToolResult.ErrorCode.TIMEOUT,
                            "command exceeded ${timeoutMs}ms",
                        )
                    } else {
                        val out = proc.inputStream.bufferedReader().readText()
                        val err = proc.errorStream.bufferedReader().readText()
                        McpToolResult.Json(
                            buildJsonObject {
                                put("exitCode", proc.exitValue())
                                put("stdout", out)
                                put("stderr", err)
                            },
                        )
                    }
                }.getOrElse { t ->
                    McpToolResult.Error(
                        McpToolResult.ErrorCode.EXEC_FAILED,
                        t.message ?: t.javaClass.simpleName,
                    )
                }
            } ?: McpToolResult.Error(
                McpToolResult.ErrorCode.TIMEOUT,
                "command exceeded ${timeoutMs}ms",
            )
        }.also { log.info("shell.exec", "ran", mapOf("cmd" to command, "args" to rawArgs.size)) }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS: Int = 5_000
        const val MAX_TIMEOUT_MS: Int = 60_000
    }
}

/**
 * Returns metadata about the model currently loaded into the
 * inference coordinator. The provider is a simple `(modelInfo)
 * -> JsonObject` lambda so the tool doesn't depend on
 * `:core-inference` directly — `app` injects the closure at startup.
 *
 * When no model is loaded the tool returns `Error(IO_ERROR, "no
 * model loaded")` rather than a synthetic empty response, so the
 * LLM can react ("Please load a model before asking about it").
 */
class ModelInfoTool(
    private val provider: () -> JsonElement?,
) {
    fun spec(): McpToolSpec = McpToolSpec(
        name = "model_info",
        description = "Return metadata about the currently loaded model: " +
            "name, format, size, capability tier, free RAM. Returns an error " +
            "if no model is loaded.",
        inputSchema = objectSchema(properties = emptyMap()),
    ) { _ -> handle() }

    private fun handle(): McpToolResult {
        val payload = provider()
            ?: return McpToolResult.Error(
                McpToolResult.ErrorCode.IO_ERROR,
                "no model loaded",
            )
        return McpToolResult.Json(payload)
    }
}

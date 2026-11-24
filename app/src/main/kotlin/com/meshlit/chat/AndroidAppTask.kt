package com.meshlit.chat

import com.meshlit.core.mcp.McpToolRequest
import com.meshlit.core.mcp.McpToolResult
import kotlinx.serialization.json.*

internal data class AndroidAppTask(val name: String, val instruction: String)
private val taskCommand = Regex(
    """^(?:(?:please|plz|can you|could you)\s+)?(?:use|operate|open|launch|control)\s+(?:the\s+)?([\p{L}\p{N}][\p{L}\p{N} ._+'-]{0,79}?)\s+(?:to|and then|and)\s+(.+)$""",
    RegexOption.IGNORE_CASE
)

internal fun androidAppTask(text: String): AndroidAppTask? {
    val hit = taskCommand.matchEntire(text.trim()) ?: return null
    val app = hit.groupValues[1].replace(Regex("""(?i)\s+app$"""), "").trim()
    return AndroidAppTask(app, hit.groupValues[2].trim())
}

/** Every call after launch is bound to the package Android resolved and the user approved. */
internal class AndroidAppSession(private val invoke: suspend (McpToolRequest) -> McpToolResult) {
    private var target = ""
    private var needsSnapshot = true

    suspend fun open(name: String) {
        val reply = invoke(McpToolRequest("android_control", buildJsonObject { put("action", "open"); put("package", name) }))
        if (reply is McpToolResult.Error) error(reply.message)
        val body = (reply as? McpToolResult.Json)?.value as? JsonObject
        target = body?.get("package")?.jsonPrimitive?.contentOrNull ?: error("Android did not identify the opened app.")
    }

    suspend fun step(request: McpToolRequest): McpToolResult {
        val args = request.arguments.jsonObject
        val action = args["action"]?.jsonPrimitive?.contentOrNull
        require(request.name == "android_control" && action in setOf("snapshot","click","type")) {
            "This task is limited to inspecting, tapping and typing in the selected app."
        }
        check(!needsSnapshot || action == "snapshot") { "Inspect the selected app before taking another action." }
        val result = invoke(request.copy(arguments = JsonObject(args + ("package" to JsonPrimitive(target)))))
        if (result is McpToolResult.Error) error(result.message)
        needsSnapshot = action != "snapshot"
        return result
    }
}

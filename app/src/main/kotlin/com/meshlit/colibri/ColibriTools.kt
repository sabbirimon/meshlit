package com.meshlit.colibri

import com.meshlit.chat.ChatController
import com.meshlit.control.AgentBackend
import com.meshlit.core.mcp.*
import com.meshlit.workspace.ColibriMode
import kotlinx.serialization.json.*

class ColibriTools(private val chat: () -> ChatController, private val agent: AgentBackend) {
    fun specs() = listOf(McpToolSpec("colibri_mode", "Request Off, On or Auto for the active, user-delegated conversation for 30 minutes. Requires human host and conversation grants plus Models/cloud/automation delegation. Does not change saved policy, start a server or download models.", buildJsonObject {
        put("type", "object"); put("properties", buildJsonObject { put("mode", buildJsonObject {
            put("type", "string"); put("enum", buildJsonArray { ColibriMode.entries.forEach { add(it.name) } })
        }) }); put("required", buildJsonArray { add("mode") }); put("additionalProperties", false)
    }) { args ->
        val data = args as? JsonObject ?: error("Expected mode object")
        require(data.keys == setOf("mode")); val mode = ColibriMode.entries.firstOrNull { it.name == data["mode"]?.jsonPrimitive?.contentOrNull } ?: error("Invalid Colibri mode")
        require(agent.delegated(AgentBackend.Scope.MODELS)) { "Saved Models delegation is required" }
        chat().requestColibriModeByAgent(mode)
        McpToolResult.Json(buildJsonObject { put("mode", mode.name); put("expiresAfterMinutes", 30); put("persisted", false); put("applies", "Next eligible chat turn; explicit providers/routes and local tools retain priority") })
    })
}

package com.meshlit.core.cloudmcp.agent

import com.meshlit.core.cloudmcp.McpEvent
import com.meshlit.core.cloudmcp.McpTool
import kotlinx.serialization.json.JsonObject

/**
 * Pure routing table: maps an `agent_*` tool name to its
 * capability, so callers can find the [AgentCapability] behind a
 * tool without inspecting the descriptor text. The dispatcher
 * itself knows the actual handler; this file just resolves names.
 */
object AgentCapabilityRouter {

    /**
     * Look up which capability owns a given tool. Returns `null`
     * when the tool isn't an `agent_*` tool (e.g. a cloud
     * provider's tool).
     */
    fun capabilityFor(toolName: String): AgentCapability? =
        AGENT_TOOLS.firstNotNullOfOrNull { (cap, names) ->
            if (toolName in names) cap else null
        }

    /**
     * Snapshot of every `agent_*` tool name. The first item in
     * each list is the primary name used for routing; the rest are
     * aliases (currently unused — `Storage` is the only capability
     * with multiple tools).
     */
    private val AGENT_TOOLS: List<Pair<AgentCapability, List<String>>> = listOf(
        AgentCapability.Camera to listOf("agent_camera_capture"),
        AgentCapability.Microphone to listOf("agent_mic_listen"),
        AgentCapability.Location to listOf("agent_location_get"),
        AgentCapability.DataState to listOf("agent_data_state"),
        AgentCapability.Call to listOf("agent_call_dial"),
        AgentCapability.Sms to listOf("agent_sms_send"),
        AgentCapability.Storage to listOf(
            "agent_storage_list",
            "agent_storage_read",
            "agent_storage_write",
        ),
    )

    /**
     * Compute the tool descriptors for the capabilities the user
     * has enabled, given a predicate `isEnabled: (capability) ->
     * Boolean`. Returns the merged list in capability-declaration
     * order.
     */
    fun enabledTools(isEnabled: (AgentCapability) -> Boolean): List<McpTool> =
        AgentCapability.entries
            .filter(isEnabled)
            .flatMap { AgentCapabilityTools.toolsFor(it) }

    /**
     * Convenience wrapper: dispatch a parsed tool call to the
     * right dispatcher function on a [DispatcherFacade].
     */
    suspend fun route(
        toolName: String,
        args: JsonObject,
        facade: DispatcherFacade,
    ): McpEvent.ToolResult? = when (toolName) {
        "agent_camera_capture" -> facade.cameraCapture(args)
        "agent_mic_listen" -> facade.micListen(args)
        "agent_location_get" -> facade.locationGet(args)
        "agent_data_state" -> facade.dataState(args)
        "agent_call_dial" -> facade.callDial(args)
        "agent_sms_send" -> facade.smsSend(args)
        "agent_storage_list" -> facade.storageList(args)
        "agent_storage_read" -> facade.storageRead(args)
        "agent_storage_write" -> facade.storageWrite(args)
        else -> null
    }

    /**
     * The dispatcher facade — one suspending function per
     * `agent_*` tool. The app module implements this by delegating
     * to its `AgentCapabilityDispatchers` instance; the core-cloud-
     * mcp module can't import the app classes directly so it
     * speaks through this interface.
     */
    interface DispatcherFacade {
        suspend fun cameraCapture(args: JsonObject): McpEvent.ToolResult
        suspend fun micListen(args: JsonObject): McpEvent.ToolResult
        suspend fun locationGet(args: JsonObject): McpEvent.ToolResult
        suspend fun dataState(args: JsonObject): McpEvent.ToolResult
        suspend fun callDial(args: JsonObject): McpEvent.ToolResult
        suspend fun smsSend(args: JsonObject): McpEvent.ToolResult
        suspend fun storageList(args: JsonObject): McpEvent.ToolResult
        suspend fun storageRead(args: JsonObject): McpEvent.ToolResult
        suspend fun storageWrite(args: JsonObject): McpEvent.ToolResult
    }
}
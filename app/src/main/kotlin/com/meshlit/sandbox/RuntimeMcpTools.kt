package com.meshlit.sandbox

import com.meshlit.core.mcp.*
import com.meshlit.core.sandbox.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Agent interface uses operator-saved VM artifacts; never accepts host binaries. */
class RuntimeMcpTools(private val host: RuntimeHost) {
    fun specs(): List<McpToolSpec> = listOf(
        McpToolSpec(name = "vm_status", description = "Inspect the optional local Linux VM. " +
            "On-device RunAnywhere inference does not require it.") {
            McpToolResult.Json(buildJsonObject {
                put("state", host.vm.state.name); put("agent_activation_allowed", host.allowAgentVm())
                put("memory_mb", host.vmConfig().memoryMb); put("cpus", host.vmConfig().cpus)
                put("desktop_enabled", host.vmConfig().enableDesktop)
                put("session_limit_minutes", 30)
            })
        },
        McpToolSpec(name = "vm_start", description = "Start the user-configured Linux VM only " +
            "when a task needs guest tools. Requires the user's agent-VM opt-in. " +
            "Uses operator-saved RAM, network and disk policy.") {
            requireAgentPermission()?.let { return@McpToolSpec it }
            val state = host.startVm(agentRequested = true)
            McpToolResult.Json(buildJsonObject { put("state", state.name); put("ssh_ready", state == VmState.SSH_READY) })
        },
        McpToolSpec(name = "vm_wait", description = "Wait up to 45 seconds for guest SSH readiness.") {
            requireAgentPermission()?.let { return@McpToolSpec it }
            val ready = host.waitForGuest(agentRequested=true)
            McpToolResult.Json(buildJsonObject { put("ssh_ready", ready); put("state", host.vm.state.name) })
        },
        McpToolSpec(name = "vm_stop", description = "Stop the optional VM. Ephemeral changes are discarded; persistent disk writes remain.") {
            requireAgentPermission()?.let { return@McpToolSpec it }
            host.stopVm(agentRequested=true)
            McpToolResult.Json(buildJsonObject { put("state", "STOPPED") })
        },
        McpToolSpec(name = "vm_exec", description = "Run a bounded batch command in the configured " +
            "VM guest through verified loopback SSH. Requires guest SSH configuration and " +
            "user agent-VM permission. Does not grant Android root access.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("argv", buildJsonObject {
                        put("type", "array"); put("minItems", 1); put("maxItems", 64)
                        put("items", buildJsonObject { put("type", "string") })
                    })
                })
                put("required", buildJsonArray { add("argv") })
            }) { args ->
            requireAgentPermission()?.let { return@McpToolSpec it }
            val argv = ((args as? JsonObject)?.get("argv") as? JsonArray)?.map {
                (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.contentOrNull
            }
            if (argv == null || argv.isEmpty() || argv.size > 64 || argv.any { it == null }) {
                return@McpToolSpec McpToolResult.Error(McpToolResult.ErrorCode.INVALID_ARGS, "argv must contain 1..64 strings")
            }
            if (host.config().mode != RuntimeMode.VM_SSH || host.vm.state != VmState.SSH_READY) {
                return@McpToolSpec McpToolResult.Error(McpToolResult.ErrorCode.PERMISSION_DENIED, "Configure verified VM SSH and wait for guest readiness")
            }
            val result = host.executeGuest(argv.filterNotNull(),agentRequested=true)
            McpToolResult.Json(buildJsonObject {
                put("exit_code", result.exitCode?.let(::JsonPrimitive) ?: JsonNull)
                put("stdout", result.stdout); put("stderr", result.stderr)
                put("timed_out", result.timedOut); put("truncated", result.truncated)
                put("untrusted_content", true)
            })
        },
    )

    private fun requireAgentPermission(): McpToolResult.Error? = if (host.allowAgentVm()) null
        else McpToolResult.Error(McpToolResult.ErrorCode.PERMISSION_DENIED, "User has not enabled agent VM activation")
}

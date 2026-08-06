package com.meshlit.core.mcp

import kotlinx.serialization.Serializable

/**
 * A user-added MCP server. Persisted in the app's DataStore (see
 * `McpUserRepository`) and spawned on demand by the [McpClientPool].
 *
 * Each entry is a complete description of how to launch an external
 * MCP-compatible binary and trust it: the command + args + env,
 * the resource caps (timeout, memory), and the list of roots
 * inside the host filesystem that the server is allowed to read.
 *
 * The companion's `validate()` enforces the constraints that
 * [McpClientPool] assumes: name is unique, command is non-blank,
 * and the call-timeouts are sensibly bounded. Tests use the
 * validator to catch malformed entries before the pool tries to
 * spawn them.
 */
@Serializable
data class UserMcpServer(
    val id: String,
    val name: String,
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val allowedRoots: List<String> = emptyList(),
    val timeoutMs: Int = 10_000,
    val enabled: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(command.isNotBlank()) { "command must not be blank" }
        require(timeoutMs in 100..MAX_TIMEOUT_MS) {
            "timeoutMs must be in 100..$MAX_TIMEOUT_MS, got $timeoutMs"
        }
    }

    /** Stable identifier for the registry. User-added tools are
     *  namespaced `<serverName>.<toolName>` so they don't collide
     *  with built-ins. */
    fun namespaced(toolName: String): String = "$name.$toolName"

    companion object {
        const val MAX_TIMEOUT_MS: Int = 60_000
    }
}

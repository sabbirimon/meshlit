package com.meshlit.core.mcp

import com.meshlit.core.common.logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Phase 4.x — `Commit 34: InApp MCP server + permission gate`.
 *
 * Per-resource gate that protects every tool call from a bundled
 * MCP server before it executes. The gate sits in front of the
 * tool registry (or the [MeshlitServerController] adapter) and
 * refuses calls whose resource is not in the user-granted set.
 *
 * Why a separate gate (instead of checking each tool handler)?
 * ----------------------------------------------------------------
 * Without a gate, every tool handler would have to remember to
 * re-check permission — and that historically leaks: the Filesystem
 * MCP path checked `defaultFilesystemPolicy(...)`, but new tools
 * added in the future could forget. Centralising the check on the
 * registry makes "permission denied" a single source of truth.
 *
 * How it works:
 *  1. Each bundled server declares its **resources** (e.g.
 *     `notes`, `calendar`, `contacts`).
 *  2. The settings repository exposes a per-resource granted flag.
 *     This gate holds a `Set<String>` of granted resources.
 *  3. Each tool spec carries an optional `requiredResource: String`
 *     field. When the registry invokes it, the gate is consulted.
 *     A missing grant returns
 *     [McpToolResult.Error] with `PERMISSION_DENIED`.
 *
 * Wiring:
 *  - [MeshlitApplication] holds one process-wide gate.
 *  - The settings screen calls `grant(resource)` / `revoke(resource)`
 *    when the user flips a chip.
 *  - The bundle bootstrap calls `setGranted(resources)` on cold-
 *    start so the in-memory set matches the persisted flag set.
 *
 * This class is intentionally backend-agnostic — it stores the
 * granted set in a `MutableStateFlow<Set<String>>` and exposes
 * synchronous `isGranted(...)` reads. Persistence is the caller's
 * job (typically `SettingsRepository`).
 */
class McpPermissionGate(
    initialGranted: Set<String> = emptySet(),
) {

    private val log = logger("McpPermissionGate")

    private val mutex = Mutex()

    private val _granted = MutableStateFlow(initialGranted.toSet())
    /** Snapshot of every currently-granted resource id (e.g.
     *  `"notes"`, `"calendar"`, `"contacts"`). UI surfaces observe
     *  this to render the chip row + "permission required" hints. */
    val granted: StateFlow<Set<String>> = _granted.asStateFlow()

    /** Synchronous predicate — `true` when [resource] is in the
     *  granted set. Read-only, no mutex needed because we only
     *  swap the whole set (immutable `Set`). */
    fun isGranted(resource: String): Boolean = resource in _granted.value

    /** Synchronous snapshot for callers that want to enumerate. */
    fun snapshot(): Set<String> = _granted.value

    /** Replace the entire granted set. Called on cold-start from
     *  the persisted CSV and from "Reset permissions" actions. */
    suspend fun setGranted(resources: Set<String>) {
        mutex.withLock {
            val sanitized = resources
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
            if (sanitized == _granted.value) return@withLock
            _granted.value = sanitized
            log.info(
                "mcp.gate.replace",
                "permission set replaced",
                mapOf("count" to sanitized.size.toString()),
            )
        }
    }

    /** Add one resource. Idempotent — calling twice with the
     *  same value is a no-op. */
    suspend fun grant(resource: String) {
        val trimmed = resource.trim()
        if (trimmed.isBlank()) return
        mutex.withLock {
            if (trimmed in _granted.value) return@withLock
            _granted.value = _granted.value + trimmed
            log.info("mcp.gate.grant", "resource granted", mapOf("resource" to trimmed))
        }
    }

    /** Remove one resource. Idempotent. */
    suspend fun revoke(resource: String) {
        val trimmed = resource.trim()
        if (trimmed.isBlank()) return
        mutex.withLock {
            if (trimmed !in _granted.value) return@withLock
            _granted.value = _granted.value - trimmed
            log.info("mcp.gate.revoke", "resource revoked", mapOf("resource" to trimmed))
        }
    }

    /**
     * Guard helper. Returns `null` when [resource] is granted;
     * returns a [McpToolResult.Error] with `PERMISSION_DENIED`
     * otherwise. Tool handlers / registry wrappers use this to
     * short-circuit ungranted calls.
     *
     * The error message is intentionally short and human-readable
     * so an LLM that reads the tool result can act on it ("please
     * ask the user to grant access to X").
     */
    fun denyIfNotGranted(resource: String): McpToolResult.Error? {
        if (isGranted(resource)) return null
        return McpToolResult.Error(
            McpToolResult.ErrorCode.PERMISSION_DENIED,
            "user has not granted access to resource '$resource'",
        )
    }
}

/**
 * The set of resources the bundled **InApp** MCP server exposes.
 * Hard-coded here because it is the single source of truth for
 * both the gate, the registry, and the settings UI chip row.
 *
 * Adding a new resource is a two-line change:
 *  1. Add an entry below.
 *  2. Add a tool spec in `InAppMcpTools` that carries this resource
 *     tag — the gate picks it up automatically.
 */
enum class InAppResource(
    val id: String,
    val displayName: String,
    val description: String,
) {
    Notes(
        id = "notes",
        displayName = "Notes",
        description = "Read titles + bodies from the on-device notes provider.",
    ),
    Calendar(
        id = "calendar",
        displayName = "Calendar",
        description = "Read upcoming events from the on-device calendar provider.",
    ),
    Contacts(
        id = "contacts",
        displayName = "Contacts",
        description = "Read names + phones from the on-device contacts provider.",
    ),
    AppFiles(
        id = "app_files",
        displayName = "App files",
        description = "Read files under the app's allowed roots (sandbox-enforced).",
    );

    companion object {
        /** All resource ids — `SettingsRepository` reads this to
         *  build the chip row. */
        val allIds: List<String> = values().map { it.id }

        /** Lookup by id. */
        fun fromId(id: String): InAppResource? = values().firstOrNull { it.id == id }
    }
}
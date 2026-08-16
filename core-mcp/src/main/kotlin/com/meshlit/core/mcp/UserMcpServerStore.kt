@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.meshlit.core.mcp

import com.meshlit.core.common.logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Persists user-added MCP server entries ([UserMcpServer]) in a
 * JSON string inside a single key so the entire catalog can be
 * rehydrated in one read.
 *
 * The store is the source of truth: `McpClientPool.replaceCatalog`
 * is called once on app start with [all], and from the Settings →
 * MCP screen on every CRUD edit. The store does not couple to a
 * specific persistence backend — the [Persistence] interface lets
 * us swap DataStore Preferences (production) for an in-memory map
 * (tests) without changing callers.
 *
 * Wire JSON shape is the [UserMcpServer] data class list — same
 * shape the pool accepts. Validation is delegated to
 * [UserMcpServer.init] requirements.
 */
class UserMcpServerStore(
    private val persistence: Persistence,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    /** Persistence backend. Tests inject [InMemoryPersistence]. */
    interface Persistence {
        suspend fun read(): String?
        suspend fun write(value: String)
    }

    private val log = logger("UserMcpServerStore")
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }
    private val serializer = ListSerializer(UserMcpServer.serializer())

    private val _servers = MutableStateFlow<List<UserMcpServer>>(emptyList())

    /** Live view of every persisted user-added server. UI subscribes
     *  to this StateFlow; the pool subscribes via [rehydrate]. */
    val servers: StateFlow<List<UserMcpServer>> = _servers.asStateFlow()

    /** Snapshot of every persisted server. */
    val all: List<UserMcpServer> get() = _servers.value

    /** Look up by id. */
    fun findById(id: String): UserMcpServer? = _servers.value.firstOrNull { it.id == id }

    /**
     * Read every persisted entry from the backend. Idempotent.
     * Failures are logged and the in-memory state stays empty.
     */
    suspend fun rehydrate() {
        mutex.withLock {
            val raw = runCatching { persistence.read() }.getOrNull()
            val parsed = raw
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
                ?: emptyList()
            _servers.value = parsed
            log.info(
                "mcp.user_store.rehydrate",
                "loaded persisted user MCP servers",
                mapOf("count" to parsed.size),
            )
        }
    }

    /** Add or replace one entry. Persists immediately. */
    suspend fun upsert(server: UserMcpServer) {
        require(server.id.isNotBlank()) { "server.id must not be blank" }
        mutex.withLock {
            val current = _servers.value
            val without = current.filterNot { it.id == server.id }
            val updated = (without + server).sortedBy { it.name }
            persistLocked(updated)
        }
        log.info(
            "mcp.user_store.upsert",
            "user MCP server saved",
            mapOf("id" to server.id, "name" to server.name),
        )
    }

    /** Remove by id. No-op if absent. */
    suspend fun remove(id: String) {
        val prior = findById(id)
        mutex.withLock {
            val current = _servers.value
            if (current.none { it.id == id }) return@withLock
            persistLocked(current.filterNot { it.id == id })
        }
        if (prior != null) {
            log.info("mcp.user_store.remove", "user MCP server removed", mapOf("id" to id))
        }
    }

    /** Toggle the `enabled` flag in place. */
    suspend fun setEnabled(id: String, enabled: Boolean) {
        mutex.withLock {
            val current = _servers.value
            val existing = current.firstOrNull { it.id == id } ?: return@withLock
            if (existing.enabled == enabled) return@withLock
            val updated = current.map { if (it.id == id) it.copy(enabled = enabled) else it }
            persistLocked(updated)
        }
    }

    /** Replace every entry at once. Persists. */
    suspend fun replaceAll(servers: List<UserMcpServer>) {
        mutex.withLock {
            persistLocked(servers.sortedBy { it.name })
        }
    }

    /**
     * Apply this store's contents to the given pool — replacing the
     * pool's configured catalog. Idempotent. Call from app startup
     * and after every CRUD edit.
     */
    suspend fun applyTo(pool: McpClientPool) {
        pool.replaceCatalog(_servers.value)
    }

    /** Drop everything (used by the "reset MCP settings" debug action). */
    suspend fun clear() {
        mutex.withLock {
            persistLocked(emptyList())
        }
        log.info("mcp.user_store.clear", "user MCP store cleared")
    }

    // ---- internal helpers (caller must hold [mutex]) -----------------------

    private suspend fun persistLocked(servers: List<UserMcpServer>) {
        _servers.value = servers
        val raw = json.encodeToString(serializer, servers)
        runCatching { persistence.write(raw) }.onFailure { t ->
            log.error("mcp.user_store.persist_fail", "failed to persist user MCP servers", t)
        }
    }
}

/** In-memory persistence backend. Useful for tests and the preview
 *  surface. Not safe to share between processes. */
class InMemoryUserMcpServerPersistence : UserMcpServerStore.Persistence {
    @Volatile private var current: String? = null
    override suspend fun read(): String? = current
    override suspend fun write(value: String) {
        current = value
    }
}

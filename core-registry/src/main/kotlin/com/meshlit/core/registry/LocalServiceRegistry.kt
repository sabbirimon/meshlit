package com.meshlit.core.registry

import com.meshlit.core.common.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide [ServiceRegistry] backed by a single
 * [MutableStateFlow]. Designed for one node per process — multi-node
 * registries live in the gossip layer (Phase 0.4).
 *
 * Concurrency: a single [Mutex] guards writes. Reads go through
 * `value` without the lock so calling `get` / `byKind` from many
 * coroutines is cheap.
 */
class LocalServiceRegistry(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ServiceRegistry {

    private val log = logger("LocalServiceRegistry")
    private val mutex = Mutex()
    private val state = MutableStateFlow<Map<String, ServiceDescriptor>>(emptyMap())

    /** Derived sorted view of [state]; updates on every write. */
    private val sortedState: StateFlow<List<ServiceDescriptor>> = state
        .map { it.values.sortedBy { d -> d.id } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun register(descriptor: ServiceDescriptor) = mutex.withLock {
        state.value = state.value + (descriptor.id to descriptor)
        log.info(
            "registry.register",
            "service registered",
            mapOf(
                "id" to descriptor.id,
                "kind" to descriptor.kind.name,
                "health" to descriptor.health::class.java.simpleName,
            ),
        )
    }

    override suspend fun unregister(id: String) = mutex.withLock {
        val before = state.value
        val after = before - id
        if (before == after) return@withLock
        state.value = after
        log.info("registry.unregister", "service unregistered", mapOf("id" to id))
    }

    override fun list(): StateFlow<List<ServiceDescriptor>> = sortedState

    override fun get(id: String): ServiceDescriptor? = state.value[id]

    override fun byKind(kind: ServiceKind): List<ServiceDescriptor> =
        state.value.values.filter { it.kind == kind }

    override suspend fun updateHealth(
        id: String,
        health: HealthState,
        timestampMs: Long,
    ) = mutex.withLock {
        val current = state.value[id] ?: return@withLock
        state.value = state.value + (id to current.copy(
            health = health,
            lastHeartbeatMs = timestampMs,
        ))
    }
}

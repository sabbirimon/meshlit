package com.meshlit.core.registry

import kotlinx.coroutines.flow.StateFlow

/**
 * The local service registry — every [ManagedService] this node owns
 * is registered here, with its current health. The router reads
 * [list] to decide where to dispatch a job.
 *
 * Implementations are thread-safe; callers should be able to
 * `register` / `unregister` from any coroutine context.
 */
interface ServiceRegistry {
    /** Add or update a descriptor. */
    suspend fun register(descriptor: ServiceDescriptor)

    /** Remove by id. No-op if absent. */
    suspend fun unregister(id: String)

    /** All current descriptors. Sorted by [ServiceDescriptor.id]. */
    fun list(): StateFlow<List<ServiceDescriptor>>

    /** Look up by id. */
    fun get(id: String): ServiceDescriptor?

    /** Filter by [ServiceKind]. */
    fun byKind(kind: ServiceKind): List<ServiceDescriptor>

    /**
     * Update the health of an existing descriptor. The descriptor
     * is identified by [id]; every other field is preserved.
     */
    suspend fun updateHealth(id: String, health: HealthState, timestampMs: Long = System.currentTimeMillis())
}

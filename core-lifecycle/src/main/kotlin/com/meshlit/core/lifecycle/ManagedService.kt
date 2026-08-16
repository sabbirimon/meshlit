package com.meshlit.core.lifecycle

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.registry.HealthState
import com.meshlit.core.registry.ServiceDescriptor
import com.meshlit.core.registry.ServiceKind

/**
 * A service the bootstrap coordinator can register, start, and stop
 * as part of the Phase 0.2 lifecycle. The lifecycle controller
 * ([ServiceLifecycleController]) treats this as a contract — every
 * implementation must be safe to start twice in a row (idempotent),
 * safe to stop while stopped (no-op), and must keep [healthCheck]
 * cheap (it runs on the lifecycle loop).
 *
 * @property id Stable across the process lifetime — the same value
 *  becomes the [ServiceDescriptor.id] in the registry.
 * @property kind Categorises the service for the router.
 * @property dependencies Other [ManagedService] ids that must be
 *  [LifecycleState.Running] before this one starts. Empty for the
 *  stub services.
 * @property requiredFeatureFlag Feature flag the lifecycle checks
 *  via [ServiceLifecycleController.isEligible]. `null` for services
 *  that should always run (e.g. the agent runtime when the user
 *  explicitly enabled it).
 */
interface ManagedService {
    val id: String
    val kind: ServiceKind
    val dependencies: List<String>
    val requiredFeatureFlag: String?
    val descriptorFactory: (ownerNodeId: String) -> ServiceDescriptor

    suspend fun start(): MeshlitResult<Unit>
    suspend fun stop(): MeshlitResult<Unit>
    suspend fun healthCheck(): HealthState
}

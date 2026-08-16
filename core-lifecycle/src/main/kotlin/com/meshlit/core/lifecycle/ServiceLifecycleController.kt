package com.meshlit.core.lifecycle

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.registry.HealthState
import com.meshlit.core.registry.LocalServiceRegistry
import com.meshlit.core.registry.ServiceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates start / stop of [ManagedService] instances and keeps
 * the [ServiceRegistry] in sync.
 *
 * Modeled after `MeshlitServerController`
 * (`core-mcp/.../MeshlitServerController.kt`): a single `Mutex`
 * guards every state transition, each call is idempotent, and the
 * per-service state lives in a `MutableStateFlow<LifecycleState>`.
 *
 * The controller also exposes [isEligible] for callers that want to
 * filter a list of services before starting them — Phase 0.5 will
 * wire the power policy into this predicate.
 */
class ServiceLifecycleController(
    private val registry: ServiceRegistry,
    private val ownerNodeId: () -> String,
    private val flagEnabled: (String) -> Boolean = { true },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private val log = logger("ServiceLifecycleController")
    private val mutex = Mutex()

    private val services = mutableMapOf<String, ManagedService>()
    private val state = MutableStateFlow<Map<String, LifecycleState>>(emptyMap())

    /** Snapshot of every service's current lifecycle state. */
    fun states(): StateFlow<Map<String, LifecycleState>> = state.asStateFlow()

    /** Current state of a single service. */
    fun stateOf(id: String): LifecycleState? = state.value[id]

    /**
     * True iff [service] should run on this node right now. Considers:
     *  - the [ManagedService.requiredFeatureFlag] (if set)
     *  - the [ManagedService.dependencies] are all Running
     */
    fun isEligible(service: ManagedService): Boolean {
        val flag = service.requiredFeatureFlag
        if (flag != null && !flagEnabled(flag)) return false
        val deps = service.dependencies
        if (deps.isEmpty()) return true
        val snapshot = state.value
        return deps.all { snapshot[it] == LifecycleState.Running }
    }

    /** Register a service descriptor without starting it. Idempotent. */
    suspend fun register(service: ManagedService): MeshlitResult<Unit> = mutex.withLock {
        services[service.id] = service
        state.value = state.value + (service.id to LifecycleState.Idle)
        val desc = service.descriptorFactory(ownerNodeId())
        registry.register(desc)
        log.info("lifecycle.register", "service registered", mapOf("id" to service.id))
        MeshlitResult.Success(Unit)
    }

    /** Start every eligible service in [services]. Already-running
     *  services are skipped (idempotent). */
    suspend fun startAll(): MeshlitResult<Unit> {
        val toStart = mutex.withLock {
            services.values.filter { isEligible(it) && state.value[it.id] != LifecycleState.Running }
        }
        var anyFailure: MeshlitError? = null
        for (service in toStart) {
            when (val res = startInternal(service)) {
                is MeshlitResult.Success -> { /* ok */ }
                is MeshlitResult.Failure -> {
                    log.error(
                        "lifecycle.start.fail",
                        "service start failed",
                        res.error,
                        mapOf("id" to service.id),
                    )
                    anyFailure = anyFailure ?: res.error
                }
            }
        }
        return if (anyFailure != null) MeshlitResult.Failure(anyFailure)
        else MeshlitResult.Success(Unit)
    }

    /** Stop every service. Idempotent. */
    suspend fun stopAll(): MeshlitResult<Unit> {
        val toStop = mutex.withLock {
            services.values.toList()
        }
        var anyFailure: MeshlitError? = null
        for (service in toStop) {
            when (val res = stopInternal(service)) {
                is MeshlitResult.Success -> { /* ok */ }
                is MeshlitResult.Failure -> {
                    log.error(
                        "lifecycle.stop.fail",
                        "service stop failed",
                        res.error,
                        mapOf("id" to service.id),
                    )
                    anyFailure = anyFailure ?: res.error
                }
            }
        }
        return if (anyFailure != null) MeshlitResult.Failure(anyFailure)
        else MeshlitResult.Success(Unit)
    }

    /** Start a single service by id. No-op if already running. */
    suspend fun start(id: String): MeshlitResult<Unit> {
        val service = mutex.withLock { services[id] }
            ?: return MeshlitResult.Failure(MeshlitError.Invalid("lifecycle.unknown_id:$id"))
        return startInternal(service)
    }

    /** Stop a single service by id. No-op if already stopped. */
    suspend fun stop(id: String): MeshlitResult<Unit> {
        val service = mutex.withLock { services[id] }
            ?: return MeshlitResult.Failure(MeshlitError.Invalid("lifecycle.unknown_id:$id"))
        return stopInternal(service)
    }

    /** Run a single health-check round on every service. Updates the
     *  registry's [HealthState]. */
    suspend fun healthCheckAll(): MeshlitResult<Unit> {
        val servicesSnapshot = mutex.withLock { services.values.toList() }
        for (service in servicesSnapshot) {
            val health = try {
                service.healthCheck()
            } catch (t: Throwable) {
                log.error(
                    "lifecycle.health.fail",
                    "healthCheck threw",
                    t,
                    mapOf("id" to service.id),
                )
                HealthState.Unreachable(t.message ?: t::class.java.simpleName)
            }
            registry.updateHealth(service.id, health, clock())
        }
        return MeshlitResult.Success(Unit)
    }

    // ---- internal helpers (caller may or may not hold [mutex]) -----

    private suspend fun startInternal(service: ManagedService): MeshlitResult<Unit> {
        if (!isEligible(service)) {
            return MeshlitResult.Failure(
                MeshlitError.Invalid("lifecycle.not_eligible:${service.id}"),
            )
        }
        // Idempotent: skip if already running.
        if (state.value[service.id] == LifecycleState.Running) return MeshlitResult.Success(Unit)
        state.value = state.value + (service.id to LifecycleState.Starting)
        return try {
            val res = service.start()
            if (res is MeshlitResult.Failure) {
                state.value = state.value + (service.id to LifecycleState.Error(res.error.tag))
                res
            } else {
                state.value = state.value + (service.id to LifecycleState.Running)
                log.info("lifecycle.start", "service running", mapOf("id" to service.id))
                res
            }
        } catch (t: Throwable) {
            state.value = state.value + (service.id to LifecycleState.Error(t.message ?: "unknown"))
            log.error("lifecycle.start.crash", "service start crashed", t, mapOf("id" to service.id))
            MeshlitResult.Failure(MeshlitError.Unknown(t))
        }
    }

    private suspend fun stopInternal(service: ManagedService): MeshlitResult<Unit> {
        // Idempotent: skip if already idle/stopped.
        if (state.value[service.id] == LifecycleState.Idle) return MeshlitResult.Success(Unit)
        state.value = state.value + (service.id to LifecycleState.Stopping)
        return try {
            val res = service.stop()
            state.value = state.value + (service.id to LifecycleState.Idle)
            log.info("lifecycle.stop", "service stopped", mapOf("id" to service.id))
            res
        } catch (t: Throwable) {
            state.value = state.value + (service.id to LifecycleState.Error(t.message ?: "unknown"))
            log.error("lifecycle.stop.crash", "service stop crashed", t, mapOf("id" to service.id))
            MeshlitResult.Failure(MeshlitError.Unknown(t))
        }
    }
}

/** Convenience factory for [LocalServiceRegistry] — used in tests
 *  and the production Koin binding. */
fun newLocalRegistry(): ServiceRegistry = LocalServiceRegistry()

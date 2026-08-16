package com.meshlit.core.bootstrap

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.config.BuiltInConfigKeys
import com.meshlit.core.config.ConfigKey
import com.meshlit.core.config.ConfigRepository
import com.meshlit.core.flags.FeatureFlagRegistry
import com.meshlit.core.lifecycle.ServiceLifecycleController
import com.meshlit.core.probe.HardwareProfilerRegistry
import com.meshlit.core.registry.ServiceRegistry
import com.meshlit.core.role.RoleManager
import java.util.UUID

/**
 * Coordinates the bootstrap sequence on app start.
 *
 * Phase 0.1 — Config + Flags.
 * Phase 0.2 — Registry + Services.
 * Phase 0.3 — Probe + Role.
 *
 * Each phase is wired only when its dependencies were supplied —
 * keeping the coordinator testable without standing up every
 * subsystem.
 *
 * **Fix 4 (review):** the previous design generated a fresh node id
 * on every call but never wrote it back, so every restart minted a
 * new identity and broke gossip membership + role history. The
 * generation in [resolveNodeId] now persists the id *before*
 * returning it, so the new value is durably stored before any
 * caller observes it.
 */
class BootstrapCoordinator(
    private val config: ConfigRepository,
    private val flags: FeatureFlagRegistry,
    private val registry: ServiceRegistry? = null,
    private val lifecycle: ServiceLifecycleController? = null,
    private val services: List<com.meshlit.core.lifecycle.ManagedService> = emptyList(),
    private val profiler: HardwareProfilerRegistry? = null,
    private val roleManager: RoleManager? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    private val log = logger("BootstrapCoordinator")

    /**
     * Run the bootstrap sequence.
     *
     * Always runs the Config and Flags phases. The Registry,
     * Services phases run only when the corresponding dependencies
     * were supplied to the constructor.
     *
     * Returns [MeshlitResult.Success] when every phase ran (or was
     * skipped because its dependency wasn't supplied). The first
     * hard failure short-circuits with [MeshlitResult.Failure].
     */
    suspend fun boot(): MeshlitResult<BootstrapSnapshot> {
        val entries = mutableListOf<BootstrapReport.Entry>()

        // ---- Config phase: node id -----------------------------------
        val configStart = clock()
        val nodeIdResult = resolveNodeId()
        entries += BootstrapReport.Entry(
            phase = BootstrapPhase.Config,
            outcome = if (nodeIdResult is MeshlitResult.Success) BootstrapReport.Outcome.Ok
                else BootstrapReport.Outcome.Failed,
            durationMs = clock() - configStart,
        )
        val nodeId = when (val r = nodeIdResult) {
            is MeshlitResult.Success -> r.value
            is MeshlitResult.Failure -> return MeshlitResult.Failure(r.error)
        }

        // ---- Flags phase: hot-load -----------------------------------
        val flagsStart = clock()
        val flagsOutcome = try {
            flags.load()
            BootstrapReport.Outcome.Ok
        } catch (t: Throwable) {
            log.error("bootstrap.flags.fail", "flags load threw", t)
            BootstrapReport.Outcome.Failed
        }
        entries += BootstrapReport.Entry(
            phase = BootstrapPhase.Flags,
            outcome = flagsOutcome,
            durationMs = clock() - flagsStart,
        )

        // ---- Phase 0.2: Registry + Services (optional) ---------------
        val reg = registry
        val lif = lifecycle
        if (reg != null && lif != null && services.isNotEmpty()) {
            val regStart = clock()
            val regOutcome = runCatching {
                for (svc in services) lif.register(svc)
            }.fold(
                onSuccess = { BootstrapReport.Outcome.Ok },
                onFailure = {
                    log.error("bootstrap.registry.fail", "register services threw", it)
                    BootstrapReport.Outcome.Failed
                },
            )
            entries += BootstrapReport.Entry(
                phase = BootstrapPhase.Registry,
                outcome = regOutcome,
                durationMs = clock() - regStart,
            )

            val svcStart = clock()
            val svcOutcome = when (val res = lif.startAll()) {
                is MeshlitResult.Success -> BootstrapReport.Outcome.Ok
                is MeshlitResult.Failure -> {
                    log.warn("bootstrap.services.partial", "one or more services failed to start: ${res.error.tag}")
                    BootstrapReport.Outcome.Failed
                }
            }
            entries += BootstrapReport.Entry(
                phase = BootstrapPhase.Services,
                outcome = svcOutcome,
                durationMs = clock() - svcStart,
            )
        }

        // ---- Phase 0.3: Probe + Role (optional) ---------------------
        if (profiler != null && roleManager != null) {
            val probeStart = clock()
            val probeOutcome: BootstrapReport.Outcome = when (val res = profiler.profileAll()) {
                is MeshlitResult.Success -> {
                    roleManager.updateWith(res.value)
                    BootstrapReport.Outcome.Ok
                }
                is MeshlitResult.Failure -> {
                    log.warn("bootstrap.probe.fail", "profile failed: ${res.error.tag}")
                    BootstrapReport.Outcome.Failed
                }
            }
            entries += BootstrapReport.Entry(
                phase = BootstrapPhase.Probe,
                outcome = probeOutcome,
                durationMs = clock() - probeStart,
            )

            val roleStart = clock()
            val decision = roleManager.decision.value
            entries += BootstrapReport.Entry(
                phase = BootstrapPhase.Role,
                outcome = BootstrapReport.Outcome.Ok,
                durationMs = clock() - roleStart,
            )
        }

        val snap = flags.snapshot()
        val role = roleManager?.decision?.value
        log.info(
            "bootstrap.boot.ok",
            "bootstrap complete",
            mapOf(
                "nodeId" to nodeId,
                "flagCount" to snap.size,
                "phases" to entries.joinToString { e -> "${e.phase}=${e.outcome}" },
                "role" to (role?.role?.name ?: "n/a"),
                "roleConfidence" to "%.2f".format(role?.confidence ?: 0f),
            ),
        )
        return MeshlitResult.Success(
            BootstrapSnapshot(
                nodeId = nodeId,
                flags = snap,
                role = role,
                report = BootstrapReport(entries),
            ),
        )
    }

    /**
     * Resolve the stable node id.
     *
     * If a value was persisted on a previous boot, hand it back
     * verbatim. If nothing is persisted, generate a UUIDv4 and
     * **persist it immediately** before returning — the write must
     * complete before this method returns so callers (gossip
     * membership, role history) see a stable identity across
     * restarts.
     */
    private suspend fun resolveNodeId(): MeshlitResult<String> {
        val key: ConfigKey<String> = BuiltInConfigKeys.nodeId()
        val existing = config.get(key)
        if (!existing.isNullOrBlank()) {
            log.info(
                "bootstrap.node_id.cached",
                "node id restored from config",
                mapOf("nodeId" to existing),
            )
            return MeshlitResult.Success(existing)
        }
        val fresh = idGenerator()
        // Fix 4: persist the freshly generated id before exposing it
        // anywhere. A failure here is fatal — the next restart will
        // mint yet another id.
        return when (val res = config.set(key, fresh)) {
            is MeshlitResult.Success -> {
                log.info(
                    "bootstrap.node_id.generated",
                    "node id generated and persisted",
                    mapOf("nodeId" to fresh),
                )
                MeshlitResult.Success(fresh)
            }
            is MeshlitResult.Failure -> {
                log.error(
                    "bootstrap.node_id.persist_fail",
                    "failed to persist generated node id",
                    res.error,
                )
                MeshlitResult.Failure(
                    MeshlitError.Resource("bootstrap.node_id.persist_fail"),
                )
            }
        }
    }
}

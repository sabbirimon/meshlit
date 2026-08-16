package com.meshlit.training

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.inference.cluster.ClusterRoutes
import com.meshlit.core.training.config.DistributedConfig
import com.meshlit.core.training.registry.ClusterTrainerRegistry
import com.meshlit.MeshlitApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 11.2 — wires `ClusterTrainerRegistry` to the `/v1/cluster/...`
 * HTTP surface. The bridge interface lives in `:core-inference` (to
 * avoid an upward dep on `:core-training`); this class is the `:app`
 * side implementation.
 *
 * State model:
 *  - [peers] is a cached list — the registry keeps the source of truth
 *    (member-of-record via `ClusterCoordinator.state`).
 *  - [activeRunId] and the most-recent [recentEvents] are kept in a
 *    bounded ring buffer (256 entries) so `/v1/cluster/logs/{runId}`
 *    doesn't have to wait on a network or a disk read. When the
 *    registry hasn't seen the runId, the endpoint returns 404.
 *
 * All operations are idempotent so the bridge survives a peer
 * retrying after a network blip.
 */
class ClusterRoutesBridge(
    private val registryProvider: () -> ClusterTrainerRegistry?,
    private val nodeIdProvider: () -> String,
    private val peerSnapshotProvider: () -> List<com.meshlit.core.inference.cluster.PeerCapabilities>,
) : ClusterRoutes.Bridge {

    private val log = logger("ClusterRoutesBridge")

    private val _activeRunId = MutableStateFlow<String?>(null)
    val activeRunId: StateFlow<String?> = _activeRunId.asStateFlow()

    private val _recentEvents = MutableStateFlow<List<ClusterRoutes.TrainingEventDto>>(emptyList())
    val recentEvents: StateFlow<List<ClusterRoutes.TrainingEventDto>> = _recentEvents.asStateFlow()

    override fun peers(): ClusterRoutes.PeersResponse {
        // ClusterRoutesBridge.peers() — synthesises a `ClusterRoutes.PeersResponse`
        // from the live snapshot. We re-use the existing
        // `MeshlitApplication.selfCapabilities()` form so the wire
        // shape stays identical to `/v1/capabilities`.
        // The peers list comes from the cluster coordinator (via
        // `clusterCoordinator.state.value.members`) once the cluster
        // is wired in `MeshlitApplication.onCreate`. In v0 we fall
        // back to a single-element list with the local peer.
        val members = runCatching { peerSnapshotProvider() }.getOrDefault(emptyList())
        return ClusterRoutes.PeersResponse(
            clusterWireVersion = 1,
            members = members,
        )
    }

    override fun plan(runId: String): ClusterRoutes.PlanResponse? {
        // Plans are computed by the registry on demand. v0 stores
        // the active plan in a transient cache; future plans persist
        // to `filesDir/training/<runId>/plan.json`.
        val reg = registryProvider() ?: return null
        // The registry doesn't currently expose a plan() method —
        // the WireRoutesBridge only returns something non-null when
        // the active runId matches. Otherwise the route 404s.
        if (runId != _activeRunId.value) return null
        val state = reg.state.value as? ClusterTrainerRegistry.RegistryState.StrategySelected
            ?: return null
        return ClusterRoutes.PlanResponse(
            clusterWireVersion = 1,
            runId = runId,
            // assignments are populated by a follow-up wiring that
            // captures the ShardingPlanner output before launch().
            assignments = emptyList(),
            strategy = state.strategy.name,
            totalReservedMb = 0L,
        )
    }

    override fun join(runId: String): ClusterRoutes.OperationResult {
        val reg = registryProvider() ?: return errorResult("registry_not_initialized")
        val cfg = defaultConfig()
        val nodeId = nodeIdProvider().ifBlank { "self" }
        return when (val res = reg.selectStrategy(cfg = cfg, jobId = runId, localPeerId = nodeId)) {
            is MeshlitResult.Success -> {
                _activeRunId.value = runId
                ClusterRoutes.OperationResult(
                    clusterWireVersion = 1,
                    accepted = true,
                    message = "joined run=$runId as $nodeId",
                )
            }
            is MeshlitResult.Failure -> errorResult(formatError(res.error))
        }
    }

    override fun leave(runId: String): ClusterRoutes.OperationResult {
        if (_activeRunId.value == runId) _activeRunId.value = null
        // Idempotent — calling leave twice is a no-op the second time.
        return ClusterRoutes.OperationResult(
            clusterWireVersion = 1,
            accepted = true,
            message = "left run=$runId",
        )
    }

    override fun run(runId: String, strategy: String): ClusterRoutes.OperationResult {
        val reg = registryProvider() ?: return errorResult("registry_not_initialized")
        val strat = parseStrategy(strategy) ?: return errorResult("unknown_strategy:$strategy")
        val cfg = defaultConfig().copy(strategy = strat)
        val nodeId = nodeIdProvider().ifBlank { "self" }
        val state = reg.state.value as? ClusterTrainerRegistry.RegistryState.StrategySelected
        if (state == null) {
            // No strategy selected yet — prime it so /v1/cluster/run
            // doesn't need a separate /v1/cluster/join call.
            when (val res = reg.selectStrategy(cfg = cfg, jobId = runId, localPeerId = nodeId)) {
                is MeshlitResult.Failure -> return errorResult(formatError(res.error))
                is MeshlitResult.Success -> Unit
            }
        }
        _activeRunId.value = runId
        // The actual trainer.launch(...) is fired by the FGS
        // (`InferenceForegroundService.ACTION_TRAIN_LAUNCH`) — the
        // route only signals that the run is admitted. The wire
        // contract mirrors SSH `training run <jobId>` semantics.
        return ClusterRoutes.OperationResult(
            clusterWireVersion = 1,
            accepted = true,
            message = "admitted run=$runId strategy=$strategy",
        )
    }

    override fun logs(runId: String, limit: Int): List<ClusterRoutes.TrainingEventDto> {
        if (runId != _activeRunId.value) return emptyList()
        val bounded = limit.coerceIn(1, 256)
        val events = _recentEvents.value
        return if (events.size <= bounded) events
        else events.takeLast(bounded)
    }

    // ── registry-facing helpers ─────────────────────────────────────

    private fun defaultConfig(): DistributedConfig =
        com.meshlit.core.training.config.DistributedConfigLoader.defaultConfig()

    private fun parseStrategy(raw: String): DistributedConfig.Strategy? = when (raw.uppercase()) {
        "P2P" -> DistributedConfig.Strategy.P2P
        "DILOCO" -> DistributedConfig.Strategy.DILOCO
        "ACCELERATE" -> DistributedConfig.Strategy.ACCELERATE
        else -> null
    }

    private fun formatError(err: MeshlitError): String = when (err) {
        is MeshlitError.Invalid -> err.tag
        is MeshlitError.Resource -> err.tag
        is MeshlitError.Network -> "network:${err.tag}"
        is MeshlitError.Native -> "native:${err.tag}"
        is MeshlitError.NodeGone -> "node_gone:${err.tag}"
        is MeshlitError.Auth -> "auth:${err.tag}"
        is MeshlitError.Unknown -> "unknown:${err.tag}"
    }

    private fun errorResult(message: String): ClusterRoutes.OperationResult =
        ClusterRoutes.OperationResult(
            clusterWireVersion = 1,
            accepted = false,
            message = message,
        )

    companion object {
        /**
         * Factory the FGS calls when it boots. We deliberately hold
         * the registry + nodeId as lambdas so the bridge survives
         * the FGS being recreated on system-initiated teardown.
         */
        fun fromApp(app: MeshlitApplication): ClusterRoutesBridge {
            return ClusterRoutesBridge(
                registryProvider = { app.clusterTrainerRegistry },
                nodeIdProvider = { app.nodeIdHex.ifBlank { "self" } },
                peerSnapshotProvider = {
                    // v0 — single-element peer list from the
                    // app's selfCapabilities(). Follow-up: bridge
                    // the ClusterCoordinator.state.value.members.
                    listOf(app.selfCapabilities())
                },
            )
        }
    }
}

package com.meshlit.inference

import com.meshlit.core.inference.CoordinatorState
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.core.inference.net.InferRequest
import com.meshlit.core.inference.net.RequestHints
import com.meshlit.core.inference.net.RouterDecision
import com.meshlit.core.inference.net.RouterRef

/**
 * Local-first router for the legacy peer endpoint. When local execution is busy,
 * rotate across approved trusted peers with fresh loaded-model health. This is
 * fair distribution, not capacity reservation or guaranteed GPU placement.
 * Forwarding consumes a bounded wire budget; an exhausted request stays local
 * and may return the engine's normal unavailable/busy failure.
 *
 * This implements [RouterRef] (the interface the embedded server
 * calls). Single instance lives in the FGS.
 */
class MiniRouter(
    private val coordinator: InferenceCoordinator,
    private val peers: PeerRegistry,
    private val health: PeerHealthCache,
) : RouterRef {

    private val selector = HealthyPeerSelector()

    override suspend fun decideFor(
        request: InferRequest,
        hints: RequestHints?,
    ): RouterDecision {
        if (hints?.remainingForwards == 0) {
            return RouterDecision.local(reason = "forwarding-budget-exhausted")
        }
        val state = coordinator.state.value
        val localOk = isLocalCapable(state, hints)
        val peerList = peers.snapshot()
        val peerHealth = health.snapshotAll()

        if (localOk && peerList.isEmpty()) {
            return RouterDecision.local(reason = "local-only")
        }
        if (localOk && hints?.needsGpu != true) {
            return RouterDecision.local(reason = "local-capable-and-no-gpu-required")
        }

        // Health is not authority: only explicitly trusted brain peers qualify.
        val trusted = runCatching { peers.trustedSnapshot() }.getOrDefault(emptyList())
        val trustedIps = trusted.filter { it.tier == com.meshlit.core.trust.TrustTier.LOCAL_TRUSTED }
            .map { it.ip }
            .toSet()
        val selected = selector.pick(peerList.filter { it in trustedIps }, peerHealth)
        if (selected != null) {
            return RouterDecision.forward(
                peerBaseUrl = "http://$selected:${com.meshlit.core.inference.net.InferenceHttpServer.DEFAULT_PORT}",
                reason = "peer-healthy:${describeLocalIssue(state, hints)}",
            )
        }

        if (localOk) {
            // No healthy peer; serve locally anyway so the request
            // doesn't drop. Phase 1 is forgiving.
            return RouterDecision.local(reason = "no-healthy-peer-fallback")
        }
        // Local is unhealthy AND no healthy peer. We still try
        // local — the failure is the engine's problem, not the
        // router's.
        return RouterDecision.local(reason = "degraded-no-healthy-peer")
    }

    /** Is the local coordinator in a state where it can serve this request? */
    private fun isLocalCapable(
        state: CoordinatorState,
        hints: RequestHints?,
    ): Boolean = when (state) {
        is CoordinatorState.Idle -> false
        is CoordinatorState.Loading -> false
        is CoordinatorState.Ready -> {
            // Phase 1 has no GPU offload; if hints require GPU and
            // we have nothing to offload to, treat local as
            // incapable. Phase 3 will consult the loaded model's
            // gpuLayers count.
            if (hints?.needsGpu == true) false else true
        }
        is CoordinatorState.Starting -> false
        is CoordinatorState.Generating -> false
        is CoordinatorState.Error -> false
    }

    private fun describeLocalIssue(
        state: CoordinatorState,
        hints: RequestHints?,
    ): String = when (state) {
        is CoordinatorState.Idle -> "no-model-loaded"
        is CoordinatorState.Loading -> "loading-model"
        is CoordinatorState.Starting -> "engine-starting"
        is CoordinatorState.Ready -> if (hints?.needsGpu == true) "needs-gpu-not-local" else "should-not-happen"
        is CoordinatorState.Generating -> "busy"
        is CoordinatorState.Error -> "error:${state.message}"
    }
}

/** Selection is atomic across concurrent requests; removed/unhealthy peers cannot retain a turn. */
internal class HealthyPeerSelector {
    private var lastSelected: String? = null

    @Synchronized fun pick(
        approvedIps: List<String>,
        health: Map<String, PeerHealthCache.PeerHealth>,
        nowMs: Long = System.currentTimeMillis(),
    ): String? {
        val eligible = approvedIps.distinct().filter { ip ->
            health[ip]?.let { it.ok && it.modelLoaded && it.asOfMs > 0 && nowMs >= it.asOfMs &&
                nowMs - it.asOfMs <= PeerHealthCache.STALE_AFTER_MS } == true
        }
        if (eligible.isEmpty()) return null
        val previous = lastSelected?.let { eligible.indexOf(it) } ?: -1
        val next = (previous + 1) % eligible.size
        return eligible[next].also { lastSelected = it }
    }
}

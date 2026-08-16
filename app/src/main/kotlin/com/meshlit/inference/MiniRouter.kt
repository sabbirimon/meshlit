package com.meshlit.inference

import com.meshlit.core.common.logger
import com.meshlit.core.inference.CoordinatorState
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.core.inference.net.InferRequest
import com.meshlit.core.inference.net.RequestHints
import com.meshlit.core.inference.net.RouterDecision
import com.meshlit.core.inference.net.RouterRef

/**
 * Capability-aware mini-router for v1.
 *
 * Decision flow per `decideFor`:
 *  1. Check the local coordinator's state.
 *     - `Idle`        → local is fine.
 *     - `Loading`     → local is busy loading; if a peer is healthy,
 *                       forward.
 *     - `Generating`  → local is busy inferring; forward.
 *     - `Ready`       → local has a model loaded → can serve.
 *     - `Error`       → local is broken; forward (peers may still work).
 *  2. If the request's [RequestHints.needsGpu] is true but the
 *     local model is CPU-only (Phase 1 always CPU; Phase 3 will read
 *     the loaded model's GPU layer count), prefer a peer.
 *  3. If local serves the hint AND the local state is healthy, return
 *     `RouterDecision.local(...)`.
 *  4. Else, scan [PeerHealthCache.snapshotAll] for the first peer
 *     that's `ok && modelLoaded`, in registry order. If found, return
 *     `RouterDecision.forward(peer, ...)`.
 *  5. Else, return `RouterDecision.local("degraded")` so the request
 *     still gets served — better than failing in Phase 1. Phase 2
 *     will add proper dead-job retry.
 *
 * This implements [RouterRef] (the interface the embedded server
 * calls). Single instance lives in the FGS.
 */
class MiniRouter(
    private val coordinator: InferenceCoordinator,
    private val peers: PeerRegistry,
    private val health: PeerHealthCache,
) : RouterRef {

    private val log = logger("MiniRouter")

    override suspend fun decideFor(
        request: InferRequest,
        hints: RequestHints?,
    ): RouterDecision {
        val state = coordinator.state.value
        val localOk = isLocalCapable(state, hints)
        val peerList = peers.snapshot()
        val peerHealth = health.snapshotAll()

        if (localOk && peerList.isEmpty()) {
            return RouterDecision.local(reason = "local-only")
        }
        if (localOk && hints?.needsGpu == false) {
            return RouterDecision.local(reason = "local-capable-and-no-gpu-required")
        }

        // Either local is busy / unhealthy, or the hints want GPU.
        // Try to find a peer.
        //
        // Phase 3 — prefer LOCAL_TRUSTED peers before falling back to
        // LOCAL_SANDBOXED (which we still route to as long as the
        // peer is healthy — the firewall does the final gate per
        // /v1/{infer,capabilities,…}).
        val trusted = runCatching { peers.trustedSnapshot() }.getOrDefault(emptyList())
        val trustedIps = trusted.filter { it.tier == com.meshlit.core.trust.TrustTier.LOCAL_TRUSTED }
            .map { it.ip }
            .toSet()
        val orderedIps = peerList.sortedByDescending { if (it in trustedIps) 1 else 0 }
        for (ip in orderedIps) {
            val h = peerHealth[ip]
            if (h != null && h.ok && h.modelLoaded) {
                return RouterDecision.forward(
                    peerBaseUrl = "http://$ip:${com.meshlit.core.inference.net.InferenceHttpServer.DEFAULT_PORT}",
                    reason = "peer-healthy:${describeLocalIssue(state, hints)}",
                )
            }
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
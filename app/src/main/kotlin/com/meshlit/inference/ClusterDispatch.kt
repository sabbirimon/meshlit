package com.meshlit.inference

import com.meshlit.core.common.logger
import com.meshlit.core.trust.TrustTier

/**
 * Cluster-side inference dispatch.
 *
 * Phase 2.x adds a third dispatch option to the Jobs screen —
 * "Cluster" — which picks the first reachable peer from the
 * [PeerRegistry] and routes prompts through the same
 * `RemoteInferenceClient` SSE wire as [InferenceDispatchMode.REMOTE].
 *
 * The actual peer resolution is intentionally minimal in this
 * revision:
 *
 *  - [firstPeer] reads the trusted-peer snapshot from the registry
 *    once (no async retry, no health probe) and returns the first
 *    entry whose `TrustTier` is `LOCAL_TRUSTED`. Untrusted peers
 *    (`LOCAL_SANDBOXED` / `WAN`) are skipped — the user explicitly
 *    added them but they haven't completed a pairing handshake yet,
 *    or they're on a different trust surface.
 *  - When no peer matches the policy the Jobs screen surfaces a
 *    synthetic "[no cluster peers reachable]" bubble.
 *
 * The wire protocol stays identical to REMOTE, so any future
 * discovery / health probing / failover code plugs into
 * [firstPeer] without touching the Jobs screen.
 */
class ClusterDispatch(
    private val peerRegistry: PeerRegistry,
) {
    private val log = logger("ClusterDispatch")

    /**
     * Resolve the first [TrustTier.LOCAL_TRUSTED] peer and return
     * its IP (`a.b.c.d:port` form). Returns `null` when no
     * trusted peer is currently registered.
     *
     * Reads the registry's snapshot synchronously via
     * `trustedSnapshot()` so the caller (a Compose `when` branch)
     * can decide the next step inline.
     */
    suspend fun firstPeer(): String? = selectFromSnapshot(peerRegistry.trustedSnapshot())

    /**
     * Non-suspend variant — selects from a caller-supplied
     * snapshot. Used by Compose `when` branches that can't
     * enter a coroutine without an explicit `LaunchedEffect` /
     * `scope.launch` (which would change the dispatch timing).
     */
    fun selectFromSnapshot(trusted: List<com.meshlit.inference.TrustedPeer>): String? {
        if (trusted.isEmpty()) {
            log.info("cluster.firstPeer.empty", "no peers registered")
            return null
        }
        val candidate = trusted.firstOrNull { it.tier == TrustTier.LOCAL_TRUSTED }
            ?: run {
                log.info(
                    "cluster.firstPeer.untrusted",
                    "no local_trusted peer",
                    mapOf("size" to trusted.size),
                )
                return null
            }
        // Same default port the Jobs screen's REMOTE field uses.
        // The port becomes a peer attribute in a follow-up.
        val peer = "${candidate.ip}:8080"
        log.info(
            "cluster.firstPeer.hit",
            "selected peer",
            mapOf("nodeId" to candidate.nodeId, "ip" to peer),
        )
        return peer
    }
}
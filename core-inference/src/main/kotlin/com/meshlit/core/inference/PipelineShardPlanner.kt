package com.meshlit.core.inference

import com.meshlit.core.common.CapabilityTier
import com.meshlit.core.inference.cluster.PeerCapabilities
import com.meshlit.core.inference.net.ShardManifest
import com.meshlit.core.inference.net.ShardSpec
import com.meshlit.core.inference.net.StageRole

/**
 * Phase 2 — pipeline-parallel inference topology. The result of
 * running [PipelineShardPlanner.plan] against a [ShardManifest] +
 * a peer roster.
 *
 * Each stage is bound to a single peer (a `ShardSpec` carries one
 * layer range, and a peer hosts one stage). The roster may not be
 * long enough for the manifest — when that happens the planner
 * returns [PipelineTopology.Invalid] with a `reason` and the caller
 * falls back to whole-model loading.
 *
 * Topology invariants:
 *  - `assignments.size == manifest.shards.size` (one stage per
 *    shard, exactly).
 *  - `assignments` are ordered by `StageRole` from
 *    `FirstStage → MiddleStage(0) → … → LastStage`. The ordering
 *    matches the manifest's own `shards` order which is also
 *    layer-monotonic.
 *  - `assignments.first().peerId == StageRole.FirstStage`. The
 *    reverse for `last`.
 *  - No two assignments share a `peerId` (otherwise we'd be
 *    hosting two stages on one phone, which is allowed only when
 *    explicitly opted into — see [PipelineShardPlanner.allowLocalCollisions]).
 */
sealed class PipelineTopology {
    abstract val manifest: ShardManifest

    /** A valid pipeline. `assignments` is layer-monotonic. */
    data class Valid(
        override val manifest: ShardManifest,
        val assignments: List<StageAssignment>,
        /** Stable id derived from the assignment table. Used as a
         *  cache key by [PipelineCoordinator] so a second request
         *  with the same topology reuses the open channels. */
        val topologyId: String,
    ) : PipelineTopology() {
        val firstStageAssignment: StageAssignment get() = assignments.first()
        val lastStageAssignment: StageAssignment get() = assignments.last()
    }

    /**
     * The planner couldn't build a valid topology. The caller should
     * fall back to whole-model loading. `reason` is a short string
     * suitable for logcat / UI status.
     */
    data class Invalid(
        override val manifest: ShardManifest,
        val reason: String,
    ) : PipelineTopology()
}

/**
 * One stage in a [PipelineTopology.Valid]. Maps a [ShardSpec] to
 * the peer that will host it. The `activation: ActivationEndpoint`
 * is the host:port downstream peers dial to reach this stage.
 */
data class StageAssignment(
    val shard: ShardSpec,
    val peer: PeerCapabilities,
    val activationHost: String,
    val activationPort: Int,
)

/**
 * Phase 2 — pure planner that maps a [ShardManifest] to a
 * [PipelineTopology]. No I/O — the caller passes a peer roster
 * (already filtered to "online + has /v1/health"). The planner is
 * deterministic so any peer can re-derive the same topology from
 * the manifest + roster without a distributed consensus step.
 *
 * Policy (in order):
 *  1. **Tier gate** — every peer must be at
 *     `capabilityTier >= shard.preferredCapabilityTier`. A LITE
 *     phone never hosts a FULL shard.
 *  2. **RAM gate** — every peer must have
 *     `freeRamMb >= shard.estimatedRamMb`. The free-RAM number
 *     comes from the latest `PeerCapabilities` snapshot.
 *  3. **Per-stage scoring** — among the eligible peers, the
 *     planner picks the highest-scoring one using Phase 1's
 *     [PeerLoadScorer]. Phase 2 adds two bonuses:
 *      - FirstStage gets +0.3 (the entry point should be the phone
 *        already serving the user's SSE connection; avoids one
 *        extra dial).
 *      - LastStage gets +0.1 (sampling on the same phone as the
 *        UI produces a tiny latency win).
 *  4. **Sticky assignment** — when a peer already hosts the same
 *    shard id (match on `hostedShardIds`), the planner prefers it
 *    with a +0.5 sticky bonus. Avoids reassigning a shard every
 *    time the planner re-runs.
 *  5. **Collision check** — by default no two stages share a peer.
 *     When the roster is shorter than the shard count the planner
 *     returns `Invalid("insufficient_peers")` so the caller falls
 *     back to whole-model. Set
 *     [PipelineShardPlanner.allowLocalCollisions] to opt into
 *     "host multiple stages on the same phone" mode (used for
 *     debug builds + single-phone simulator runs).
 *
 * Inputs are immutable; the planner is a pure function. NoStateFlow,
 * no coroutines, no logging beyond the structured `logger` calls.
 */
class PipelineShardPlanner(
    private val firstStageBonus: Double = 0.3,
    private val lastStageBonus: Double = 0.1,
    private val stickyBonus: Double = 0.5,
    val allowLocalCollisions: Boolean = false,
) {

    /**
     * The scoring function used per stage. Phase 2 keeps this in
     * `:core-inference` (pure) — the `:app`-side `PeerLoadScorer`
     * is a separate, IO-aware component. The planner only needs
     * the arithmetic, so we duplicate the formula here rather than
     * pulling the whole `:app` scorer into `:core-inference`.
     *
     * Mirrors the table in [PeerLoadScorer.scoreOf] but stripped
     * of the IO-derived signals (RTT, p50 latency, consecutive
     * failures). The planner is invoked at plan-time, before any
     * forward has happened, so those signals are zero anyway.
     */
    private fun scoreCandidate(peer: PeerCapabilities, shard: ShardSpec): Double {
        var score = 0.0
        // Tier — FULL/MID/LITE → 0.4/0.2/0.0
        score += when (peer.capabilityTier) {
            CapabilityTier.FULL -> 0.4
            CapabilityTier.MID -> 0.2
            CapabilityTier.LITE -> 0.0
        }
        // GPU — +0.2 when the peer has a working GPU backend.
        if (peer.hasGpu) score += 0.2
        // Free RAM — penalise when RAM is below 512 MiB.
        if (peer.freeRamMb in 1 until 512) score -= 0.3
        // Shard-tier bonus — reward peers that match the shard's
        // preferred tier. A FULL peer hosting a FULL shard is the
        // ideal pairing; a MID peer hosting a MID shard is fine.
        if (peer.capabilityTier == shard.preferredCapabilityTier) {
            score += 0.2
        }
        return score
    }

    /**
     * Build a topology. Pure function; same input → same output.
     *
     * `self` is the local device's [PeerCapabilities]. It is
     * always included in the candidate pool (with `peerId = "self"`)
     * because the user may want to run the pipeline on a single
     * phone in development.
     */
    fun plan(
        manifest: ShardManifest,
        self: PeerCapabilities,
        roster: List<PeerCapabilities>,
        activationPort: Int = 9090,
    ): PipelineTopology {
        // 1. Insufficient peers — bail out before scoring.
        val totalRoster = (listOf(self) + roster).distinctBy { it.peerId }
        if (!allowLocalCollisions && totalRoster.size < manifest.shards.size) {
            return PipelineTopology.Invalid(
                manifest = manifest,
                reason = "insufficient_peers:" +
                    "need=${manifest.shards.size} available=${totalRoster.size}",
            )
        }

        // 2. Per-stage assignment. We keep an immutable copy of the
        // roster between calls so each assignment removes its
        // chosen host from the pool when collisions are forbidden.
        val mutableRoster = totalRoster.toMutableList()
        val assignments = mutableListOf<StageAssignment>()
        for (shard in manifest.shards) {
            val eligible = mutableRoster.filter { peer ->
                // Tier gate (peer tier >= shard preferred tier).
                peerHasMinimumTier(peer.capabilityTier, shard.preferredCapabilityTier) &&
                    // RAM gate.
                    peer.freeRamMb >= shard.estimatedRamMb
            }
            if (eligible.isEmpty()) {
                return PipelineTopology.Invalid(
                    manifest = manifest,
                    reason = "no_eligible_peer_for_shard:" +
                        "shard=${shard.shardId} " +
                        "tier>=${shard.preferredCapabilityTier.name} " +
                        "ramMb>=${shard.estimatedRamMb}",
                )
            }
            // Score + tiebreak. Sticky + first/last bonuses stack on top.
            val scored = eligible.map { peer ->
                var s = scoreCandidate(peer, shard)
                // Sticky: the peer already hosts this shard.
                if (peer.hostedShardIds.contains(shard.shardId)) {
                    s += stickyBonus
                }
                // Entry-point bonus.
                if (shard.stageRole is StageRole.FirstStage) {
                    s += firstStageBonus
                }
                // Exit-point bonus.
                if (shard.stageRole is StageRole.LastStage) {
                    s += lastStageBonus
                }
                peer to s
            }.sortedWith(
                compareByDescending<Pair<PeerCapabilities, Double>> { it.second }
                    .thenByDescending { it.first.freeRamMb }
                    .thenBy { it.first.peerId }
            )
            val (chosenPeer, _) = scored.first()
            val assignment = StageAssignment(
                shard = shard,
                peer = chosenPeer,
                activationHost = peerHostFor(chosenPeer),
                activationPort = activationPort,
            )
            assignments += assignment
            // Remove the chosen peer from the pool so we don't
            // double-assign unless the caller opted in.
            if (!allowLocalCollisions) {
                mutableRoster.removeAll { it.peerId == chosenPeer.peerId }
            }
        }

        // 3. Validate result — assignments must be layer-monotonic.
        val sorted = assignments.sortedBy { it.shard.layerStart }
        if (sorted != assignments) {
            return PipelineTopology.Invalid(
                manifest = manifest,
                reason = "non_monotonic_assignment",
            )
        }

        // 4. Topology id — stable hash of (shardId, peerId) pairs.
        //    SHA-1 truncated to 16 chars is plenty for a cache key;
        //    collisions are astronomically unlikely on a 6-phone
        //    cluster and the worst case is a cache miss.
        val topologyId = computeTopologyId(sorted)

        return PipelineTopology.Valid(
            manifest = manifest,
            assignments = sorted,
            topologyId = topologyId,
        )
    }

    /**
     * Tier comparison — a peer can host a shard whose
     * `preferredCapabilityTier` is at or below the peer's tier.
     */
    private fun peerHasMinimumTier(peer: CapabilityTier, required: CapabilityTier): Boolean {
        val peerRank = when (peer) {
            CapabilityTier.FULL -> 3
            CapabilityTier.MID -> 2
            CapabilityTier.LITE -> 1
        }
        val requiredRank = when (required) {
            CapabilityTier.FULL -> 3
            CapabilityTier.MID -> 2
            CapabilityTier.LITE -> 1
        }
        return peerRank >= requiredRank
    }

    /**
     * Resolve the activation host for a peer. The convention is:
     *  - `peer.peerId == "self"` → `127.0.0.1` (loopback).
     *  - Otherwise the peerId is the IPv4 of the remote device
     *    (set by `PeerRegistry` from the discovery UDP socket).
     *  - When the peer is the host of record, use the
     *    `clusterHostOfRecord` node id (cleared through the
     *    discovery layer).
     *
     * Resolution is intentionally narrow — IPv4 only. IPv6 is a
     * Phase 2.5 add-on because the existing
     * `RawTcpActivationChannel` uses `InetSocketAddress` which
     * handles both, but the peer roster is IPv4 for the LAN
     * Meshlit cluster.
     */
    private fun peerHostFor(peer: PeerCapabilities): String {
        if (peer.peerId == "self") return "127.0.0.1"
        return peer.peerId
    }

    /**
     * Compute a stable id for the topology. The id is the
     * concatenated `(shardId, peerId)` pairs, hashed with
     * SHA-1 and truncated to 16 hex chars. SHA-1 is fine here:
     * the id is a cache key, not a security token.
     */
    private fun computeTopologyId(assignments: List<StageAssignment>): String {
        val bag = assignments.joinToString("|") { "${it.shard.shardId}@${it.peer.peerId}" }
        val md = java.security.MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(bag.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }
}

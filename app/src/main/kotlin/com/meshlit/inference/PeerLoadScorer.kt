package com.meshlit.inference

import com.meshlit.core.common.CapabilityTier
import com.meshlit.core.inference.cluster.PeerCapabilities
import com.meshlit.core.trust.TrustTier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Weighted scorer for cluster peers.
 *
 * Replaces the Phase-1 "first-peer-wins" pick in [MiniRouter] with
 * a points-based decision over the same signals the
 * `WeightedRoundRobinSelector` in `:core-net` uses, plus the new
 * load signal (`activeInferences` + `queueDepth`).
 *
 * Score components (additive; clamped as noted):
 *
 * | Component             | Value        | Notes                              |
 * |-----------------------|--------------|------------------------------------|
 * | model loaded          | +0.5         | when `health.ok && modelLoaded`    |
 * | tier = FULL           | +0.4         |                                    |
 * | tier = MID            | +0.2         |                                    |
 * | GPU offload available | +0.2         | when `capabilities.hasGpu`         |
 * | active inferences     | −0.2 / load  | clamped at −1.0                    |
 * | queue > 0             | −0.2         |                                    |
 * | p50 latency > 500ms   | −0.05 / 100ms over | clamped at −0.5           |
 * | free RAM < 512 MiB    | −0.3         |                                    |
 * | consecutive failures  | −0.1 / fail  | clamped at −1.0                    |
 * | trust = SANDBOXED     | −0.1         |                                    |
 *
 * Tiebreak: highest score wins; on equal score, the most-recent
 * `health.asOfMs` wins (fresher probe data is more trustworthy).
 *
 * Sticky pin: when `sticky` is non-null and the pinned peer is
 * still in the candidate list with fewer than
 * [pinDemotionThreshold] consecutive failures, the pin holds
 * regardless of score. This avoids flapping when the local
 * coordinator momentarily observes a higher score on a different
 * peer.
 *
 * Diagnostics: every [pick] emits a [WeightSnapshot] for every
 * candidate via the [snapshots] SharedFlow so the team can grep
 * logcat for `peer.load.scored` and see what the scorer was
 * thinking.
 */
class PeerLoadScorer {

    /**
     * Per-peer input to the scorer. Built by [MiniRouter] from the
     * PeerRegistry + PeerHealthCache + HealthEnricher snapshot. The
     * scorer is pure (no I/O), so the router is the right place to
     * assemble the candidate.
     */
    data class CandidateLoad(
        val ip: String,
        val capabilities: PeerCapabilities,
        val health: PeerHealthCache.PeerHealth,
        val activeInferences: Int,
        val queueDepth: Int,
        val p50LatencyMs: Long,
        /** Round-trip latency in ms from the last forward attempt.
         *  0 when we haven't talked to this peer yet. */
        val rttMs: Long = 0L,
        val consecutiveFailures: Int = 0,
        val trustTier: TrustTier = TrustTier.LOCAL_TRUSTED,
    )

    /**
     * One line in the [snapshots] emission. Carries the score and
     * the contributing components so logcat can show why a peer
     * ranked where it did.
     */
    data class WeightSnapshot(
        val ip: String,
        val score: Double,
        val components: Map<String, Double>,
        val tsMs: Long,
    )

    private val _snapshots = MutableSharedFlow<List<WeightSnapshot>>(extraBufferCapacity = 32)
    val snapshots: SharedFlow<List<WeightSnapshot>> = _snapshots.asSharedFlow()

    /**
     * Score one candidate. Pure function — no I/O, no flow emission.
     * Visible to tests so the per-component contribution can be
     * asserted directly.
     */
    fun scoreOf(c: CandidateLoad): Double {
        val components = mutableMapOf<String, Double>()
        var score = 0.0
        if (c.health.ok && c.health.modelLoaded) {
            components["modelLoaded"] = 0.5
            score += 0.5
        }
        val tierKey = "tier_${c.capabilities.capabilityTier.name}"
        val tierBonus = when (c.capabilities.capabilityTier) {
            CapabilityTier.FULL -> 0.4
            CapabilityTier.MID -> 0.2
            CapabilityTier.LITE -> 0.0
        }
        if (tierBonus > 0.0) {
            components[tierKey] = tierBonus
            score += tierBonus
        }
        if (c.capabilities.hasGpu) {
            components["gpu"] = 0.2
            score += 0.2
        }
        val loadPenalty = (c.activeInferences * 0.2).coerceAtMost(1.0)
        if (loadPenalty > 0.0) {
            components["load"] = -loadPenalty
            score -= loadPenalty
        }
        if (c.queueDepth > 0) {
            components["queue"] = -0.2
            score -= 0.2
        }
        if (c.p50LatencyMs > 500) {
            val over = (c.p50LatencyMs - 500) / 100
            val pen = (over * 0.05).coerceAtMost(0.5)
            components["latency"] = -pen
            score -= pen
        }
        if (c.capabilities.freeRamMb in 1 until 512) {
            components["lowRam"] = -0.3
            score -= 0.3
        }
        if (c.consecutiveFailures > 0) {
            val pen = (c.consecutiveFailures * 0.1).coerceAtMost(1.0)
            components["fail"] = -pen
            score -= pen
        }
        if (c.trustTier == TrustTier.LOCAL_SANDBOXED) {
            components["trust"] = -0.1
            score -= 0.1
        }
        return score
    }

    /**
     * Pick the best candidate from [candidates]. Honours a sticky
     * pin until the pinned peer has [pinDemotionThreshold]
     * consecutive failures. Returns null when the input is empty.
     *
     * Emits a [WeightSnapshot] for every candidate on [snapshots]
     * — even when the pin holds — so logcat shows the score the
     * pin over-rode.
     */
    fun pick(
        candidates: List<CandidateLoad>,
        sticky: String?,
        pinDemotionThreshold: Int = 3,
    ): CandidateLoad? {
        if (candidates.isEmpty()) return null

        val scored = candidates.map { it to scoreOf(it) }
        val sorted = scored.sortedWith(
            compareByDescending<Pair<CandidateLoad, Double>> { it.second }
                .thenByDescending { it.first.health.asOfMs },
        )

        // Always emit a snapshot so the team can audit the
        // picker's reasoning, including when the pin over-rode.
        val snapshots = sorted.map { (c, s) ->
            WeightSnapshot(
                ip = c.ip,
                score = s,
                components = componentsOf(c),
                tsMs = System.currentTimeMillis(),
            )
        }
        _snapshots.tryEmit(snapshots)

        // Sticky pin: return the pinned peer if it's still in the
        // candidate list and hasn't accumulated enough failures
        // to demote.
        if (sticky != null) {
            val pinned = candidates.firstOrNull { it.ip == sticky }
            if (pinned != null && pinned.consecutiveFailures < pinDemotionThreshold) {
                return pinned
            }
        }
        return sorted.firstOrNull()?.first
    }

    /**
     * Re-derive the components map for a single candidate. Mirrors
     * [scoreOf] but returns the map directly. Used to build a
     * [WeightSnapshot] without re-running the math a second time
     * inline.
     */
    private fun componentsOf(c: CandidateLoad): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        if (c.health.ok && c.health.modelLoaded) out["modelLoaded"] = 0.5
        val tier = when (c.capabilities.capabilityTier) {
            CapabilityTier.FULL -> 0.4
            CapabilityTier.MID -> 0.2
            CapabilityTier.LITE -> 0.0
        }
        if (tier > 0.0) out["tier_${c.capabilities.capabilityTier.name}"] = tier
        if (c.capabilities.hasGpu) out["gpu"] = 0.2
        val load = (c.activeInferences * 0.2).coerceAtMost(1.0)
        if (load > 0.0) out["load"] = -load
        if (c.queueDepth > 0) out["queue"] = -0.2
        if (c.p50LatencyMs > 500) {
            val over = (c.p50LatencyMs - 500) / 100
            out["latency"] = -(over * 0.05).coerceAtMost(0.5)
        }
        if (c.capabilities.freeRamMb in 1 until 512) out["lowRam"] = -0.3
        if (c.consecutiveFailures > 0) {
            out["fail"] = -(c.consecutiveFailures * 0.1).coerceAtMost(1.0)
        }
        if (c.trustTier == TrustTier.LOCAL_SANDBOXED) out["trust"] = -0.1
        return out
    }
}

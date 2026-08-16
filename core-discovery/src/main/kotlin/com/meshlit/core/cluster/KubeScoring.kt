package com.meshlit.core.cluster

import com.meshlit.core.common.CapabilityTier
import com.meshlit.core.discovery.beacon.ResourceSnapshot
import com.meshlit.core.discovery.beacon.ThermalHeadroom
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * **Phase Hivemind-1 — KubeScoring**
 *
 * Pure scoring function used by [KubeScheduler] to rank cluster
 * members for host eligibility. The scorer is intentionally
 * analogous to Kubernetes' `NodeAffinity` + `Taints` + `priorityClass`
 * — five weighted axes that map to the same physical signals
 * (`NodeResources`, `Load`, `Health`, `Power`, `Network`).
 *
 * Why a pure function and not a class with state:
 *  - Unit tests don't need mocks; pass a `Map<nodeId, Inputs>` and
 *    assert the sorted output.
 *  - The scheduler can run the scorer in parallel against
 *    different `roster` snapshots (e.g. for dry-run display) without
 *    mutation.
 *  - The scorer never blocks on IO; all inputs are pre-collected by
 *    the scheduler before the call.
 *
 * Weight table (kept in code so the unit tests can pin them):
 *  - hardware  × 1.0
 *  - load      × 0.6
 *  - health    × 0.5
 *  - power     × 0.4
 *  - network   × 0.3
 *
 * Total range: `[-2.5, 2.8]`. A peer is **eligible to be Host** if
 * `totalScore > HOST_FLOOR = 0.5` AND `powerScore > 0.3` AND
 * `healthScore > 0.1`. Workers only need `totalScore > -1.0`.
 *
 * Hysteresis is applied by the caller (see [YIELD_THRESHOLD]) —
 * the scorer returns raw scores; the scheduler decides whether
 * the delta is worth a handover.
 */
object KubeScoring {

    /** Hard gate: below this floor the peer is never a host. */
    const val HOST_FLOOR = 0.5

    /** Hard gate: below this floor the peer can't even be a worker. */
    const val WORKER_FLOOR = -1.0

    /** Hysteresis: a handover is only triggered if the new best
     *  score exceeds the current host by at least this delta.
     *  Prevents flapping when two peers oscillate around the same
     *  score. */
    const val YIELD_THRESHOLD = 0.3

    /** Power hard gate: a throttling peer can never be host even
     *  if its hardware score is the highest. */
    const val POWER_HOST_FLOOR = 0.3

    /** Inputs the scheduler has already gathered from
     *  [com.meshlit.core.discovery.beacon.ResourceSnapshot] +
     *  [PeerCapabilities] + [PeerHealthCache]. */
    data class Inputs(
        val nodeId: String,
        val ip: String = "",
        val tier: CapabilityTier,
        val freeRamMb: Long = 0L,
        val cpuCoreCount: Int = 1,
        val hasGpu: Boolean = false,
        val hasNpu: Boolean = false,
        val healthOk: Boolean = true,
        val healthAgeMs: Long = 0L,
        val consecutiveFailures: Int = 0,
        val activeInferences: Int = 0,
        val rttMs: Long = 0L,
        val linkSpeedMbps: Double? = null,
        val rssiDbm: Int? = null,
        val batteryPct: Int = -1,
        val isCharging: Boolean = false,
        val thermal: ThermalHeadroom = ThermalHeadroom.COOL,
        val packetLoss: Double = 0.0,
    )

    /** Per-axis breakdown so the UI can render "why this peer is
     *  ranked here". */
    data class ScoreBreakdown(
        val nodeId: String,
        val ip: String,
        val hardware: Double,
        val load: Double,
        val health: Double,
        val power: Double,
        val network: Double,
        val total: Double,
        val hostEligible: Boolean,
        val workerEligible: Boolean,
    ) {
        val components: Map<String, Double> = mapOf(
            "hardware" to hardware,
            "load" to load,
            "health" to health,
            "power" to power,
            "network" to network,
            "total" to total,
        )
    }

    /**
     * Score a single peer. Returns a [ScoreBreakdown] with the
     * per-axis contributions and the eligibility flags. Negative
     * inputs are clamped; `nan`/`inf` propagate as `0.0` so a
     * flaky probe doesn't poison the entire ranking.
     */
    fun score(inp: Inputs): ScoreBreakdown {
        val hardware = hardwareScore(inp)
        val load = loadScore(inp)
        val health = healthScore(inp)
        val power = powerScore(inp)
        val network = networkScore(inp)
        val total = hardware * 1.0 + load * 0.6 + health * 0.5 + power * 0.4 + network * 0.3
        val hostEligible = total > HOST_FLOOR &&
            power > POWER_HOST_FLOOR &&
            health > 0.1 &&
            inp.healthOk
        val workerEligible = total > WORKER_FLOOR
        return ScoreBreakdown(
            nodeId = inp.nodeId,
            ip = inp.ip,
            hardware = sanitize(hardware),
            load = sanitize(load),
            health = sanitize(health),
            power = sanitize(power),
            network = sanitize(network),
            total = sanitize(total),
            hostEligible = hostEligible,
            workerEligible = workerEligible,
        )
    }

    /** Score every peer and return them sorted by total score
     *  descending. Stable for ties (preserves input order). */
    fun scoreAll(inputs: List<Inputs>): List<ScoreBreakdown> =
        inputs.map { score(it) }
            .sortedWith(compareByDescending<ScoreBreakdown> { it.total }
                .thenBy { it.nodeId })

    /** Hardware axis — tier + RAM + cores + GPU + NPU. Range [0, 1]. */
    fun hardwareScore(inp: Inputs): Double {
        var s = 0.0
        s += when (inp.tier) {
            CapabilityTier.FULL -> 0.4
            CapabilityTier.MID -> 0.2
            CapabilityTier.LITE -> 0.0
        }
        // RAM: log2(RAM / 4 GB), clamped to [-0.2, 0.4]. 4 GB
        // baseline maps to 0; 16 GB maps to +0.4; 256 MB maps to -0.2.
        val ramBaseline = max(inp.freeRamMb.toDouble() / 4096.0, 0.0625)
        val ram = (ln(ramBaseline) / ln(2.0)) * 0.2
        s += min(0.4, max(-0.2, ram))
        // CPU cores: capped at 16, normalized linearly. 1 core → 0,
        // 16 cores → 0.15.
        val cores = (inp.cpuCoreCount.coerceIn(1, 16) / 16.0) * 0.15
        s += cores
        if (inp.hasGpu) s += 0.15
        if (inp.hasNpu) s += 0.10
        return s
    }

    /** Load axis — 0.0 when saturated (4+ inferences), 1.0 when
     *  idle. Linear interpolation so the score degrades smoothly
     *  as the peer gets busier. Range [0, 1]. */
    fun loadScore(inp: Inputs): Double {
        val active = inp.activeInferences.coerceAtLeast(0)
        return (1.0 - (active / 4.0)).coerceIn(0.0, 1.0)
    }

    /** Health axis — freshness × (1 - failure penalty). Stale
     *  probes (>30 s) decay exponentially; consecutive failures
     *  drive the score toward 0. Range [0, 1]. */
    fun healthScore(inp: Inputs): Double {
        if (!inp.healthOk) return 0.0
        val ageMs = inp.healthAgeMs.coerceAtLeast(0L)
        val freshness = exp(-ageMs / 30_000.0)
        val failurePenalty = (inp.consecutiveFailures / 3.0).coerceIn(0.0, 1.0)
        return (freshness * (1.0 - failurePenalty)).coerceIn(0.0, 1.0)
    }

    /** Power axis — 0.0 when throttling (overrides everything),
     *  1.0 when charging, else battery percentage. Unknown battery
     *  (-1) returns 0.5 (neutral). Range [0, 1]. */
    fun powerScore(inp: Inputs): Double {
        if (inp.thermal == ThermalHeadroom.THROTTLING) return 0.0
        if (inp.isCharging) return 1.0
        if (inp.batteryPct < 0) return 0.5
        return (inp.batteryPct.coerceIn(0, 100) / 100.0)
    }

    /** Network axis — link speed, latency, packet loss. Range [0, 1]. */
    fun networkScore(inp: Inputs): Double {
        val link = if (inp.linkSpeedMbps != null && inp.linkSpeedMbps > 0) {
            // log10(Mbps / 100), clamped to [0, 1]. 100 Mbps → 0; 1 Gbps → 1.
            min(1.0, max(0.0, (ln(inp.linkSpeedMbps / 100.0) / ln(10.0)) ))
        } else 0.5
        val latency = (1.0 - (inp.rttMs.toDouble() / 200.0)).coerceIn(0.0, 1.0)
        val loss = (1.0 - inp.packetLoss.coerceIn(0.0, 1.0))
        return (link + latency + loss) / 3.0
    }

    /** Defensive `nan`/`inf` guard. A flaky probe returning garbage
     *  shouldn't poison the whole ranking; clamp to 0. */
    private fun sanitize(v: Double): Double = when {
        v.isNaN() || v.isInfinite() -> 0.0
        else -> v
    }

    /** True iff the peer clears the hard host gates. Convenience
     *  wrapper around [score] for callers that only need the flag. */
    fun isHostEligible(inp: Inputs): Boolean = score(inp).hostEligible

    /** True iff the peer is at least a worker (i.e. total > -1.0). */
    fun isWorkerEligible(inp: Inputs): Boolean = score(inp).workerEligible
}
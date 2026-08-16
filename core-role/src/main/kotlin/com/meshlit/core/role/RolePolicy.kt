package com.meshlit.core.role

import com.meshlit.core.probe.HardwareCapability

/**
 * Scores a [HardwareCapability] against a single [Role]. Returns a
 * value in 0..1 — the highest score wins.
 *
 * **Fix 1 (review):** the original design pinned `Role.Idle` to
 * `1.0f` as a "fallback that always wins", but every other role
 * only hit `1.0` when its thresholds were all met. The result: any
 * device that wasn't a perfect match for Brain/Tool/Monitor/Relay
 * lost to Idle and the cluster went quiet. Idle is now a *low
 * baseline* (`0.05f`) — always eligible, but never the winner
 * unless nothing else scored above it. The
 * [com.meshlit.core.role.RolePolicyTest] suite has an explicit
 * regression test for this case.
 */
object RolePolicy {

    /** Always-eligible baseline. Returns the same value regardless
     *  of capability so Idle is the deterministic loser. */
    fun scoreIdle(): Float = 0.05f

    /** Score for [Role.Brain]. Requires:
     *   - NPU available
     *   - RAM ≥ 6 GB
     *   - Battery ≥ 30 %
     *   - Not currently throttling
     *
     * Each satisfied threshold contributes a fraction; the final
     * score is the average. Zero if any hard requirement fails.
     */
    fun scoreBrain(cap: HardwareCapability): Float {
        if (!cap.hasNpu) return 0f
        val ram = cap.totalRamMb ?: return 0f
        if (ram < 6L * 1024L) return 0f
        val battery = cap.batteryPct ?: 0
        if (battery < 30) return 0f
        if (cap.isThrottling) return 0f
        var score = 0f
        score += 0.40f // npu + ram gate passed
        score += 0.30f * batteryScore(battery)
        score += 0.30f * (1f - (cap.thermal.score ?: 1f)) // lower thermal stress = higher score
        return score.coerceIn(0f, 1f)
    }

    /** Score for [Role.Tool]. Requires RAM ≥ 4 GB and not throttling. */
    fun scoreTool(cap: HardwareCapability): Float {
        val ram = cap.totalRamMb ?: return 0f
        if (ram < 4L * 1024L) return 0f
        if (cap.isThrottling) return 0f
        return 0.5f + 0.5f * ramScore(ram, baseline = 4L * 1024L, ceiling = 12L * 1024L)
    }

    /** Score for [Role.Monitor]. Requires battery ≥ 50 % and
     *  network reachable. RAM / NPU don't matter. */
    fun scoreMonitor(cap: HardwareCapability): Float {
        val battery = cap.batteryPct ?: 0
        if (battery < 50) return 0f
        if (!cap.networkReachable) return 0f
        return 0.6f + 0.4f * batteryScore(battery)
    }

    /** Score for [Role.Relay]. Requires network reachable. Bypass-
     *  charge detection (the real "is this wall-powered" signal)
     *  lands in Phase 0.5 — for now any device with a network
     *  connection is eligible. */
    fun scoreRelay(cap: HardwareCapability): Float {
        if (!cap.networkReachable) return 0f
        return 0.4f
    }

    /** Dispatcher used by the suggestion engine. */
    fun score(cap: HardwareCapability, role: Role): Float = when (role) {
        Role.Idle -> scoreIdle()
        Role.Brain -> scoreBrain(cap)
        Role.Tool -> scoreTool(cap)
        Role.Monitor -> scoreMonitor(cap)
        Role.Relay -> scoreRelay(cap)
    }

    /** Reasons a role passed (or didn't). Surfaced in the suggestion
     *  UI so the user can see *why* their device got a particular
     *  suggestion. */
    fun explain(cap: HardwareCapability, role: Role): List<String> = when (role) {
        Role.Idle -> listOf("baseline — no other role scored above 0.05")
        Role.Brain -> listOfNotNull(
            "NPU available".takeIf { cap.hasNpu },
            "RAM ≥ 6 GB".takeIf { (cap.totalRamMb ?: 0L) >= 6L * 1024L },
            "battery ≥ 30%".takeIf { (cap.batteryPct ?: 0) >= 30 },
            "not throttling".takeIf { !cap.isThrottling },
        )
        Role.Tool -> listOfNotNull(
            "RAM ≥ 4 GB".takeIf { (cap.totalRamMb ?: 0L) >= 4L * 1024L },
            "not throttling".takeIf { !cap.isThrottling },
        )
        Role.Monitor -> listOfNotNull(
            "battery ≥ 50%".takeIf { (cap.batteryPct ?: 0) >= 50 },
            "network reachable".takeIf { cap.networkReachable },
        )
        Role.Relay -> listOfNotNull(
            "network reachable".takeIf { cap.networkReachable },
            "wall power assumed (Phase 0.5 will detect bypass-charge)".takeIf { cap.networkReachable },
        )
    }

    // ---- private scoring helpers -------------------------------------

    /** 0..1 score for a battery percent. 0 → 0, 50 → 0.5, 100 → 1.0. */
    private fun batteryScore(pct: Int): Float =
        (pct.coerceIn(0, 100) / 100f).coerceIn(0f, 1f)

    /** 0..1 score for a RAM amount, linearly interpolated between
     *  `baseline` (0) and `ceiling` (1). */
    private fun ramScore(ramMb: Long, baseline: Long, ceiling: Long): Float {
        val span = (ceiling - baseline).coerceAtLeast(1L)
        val above = (ramMb - baseline).coerceAtLeast(0L)
        return (above.toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }
}

package com.meshlit.core.common

/**
 * Capability probe — runs once on join and periodically afterwards.
 * Android-side implementation lives in core-orchestration; this is the
 * platform-agnostic interface that the role suggester and benchmark-on-
 * join feed off.
 *
 * Per BUILD_GUIDE §0 there are no hardcoded thresholds like "75% of RAM"
 * — the probe reports actual headroom at the moment of measurement,
 * and downstream code (router, scheduler) decides what's safe.
 */
interface CapabilityProbe {
    suspend fun probe(): MeshlitResult<CapabilitySnapshot>
}

/**
 * Suggest a role from a snapshot. This is the "advisory" code path
 * from §0 principle 2 — never hard-locks. The UI shows it as a
 * suggestion and lets the user override.
 */
fun suggestRole(snap: CapabilitySnapshot): ClusterRole {
    // Heuristic (Phase 0 placeholder, replaced by real signals in Phase 2):
    //   - Lots of free RAM and not too hot           -> BRAIN candidate
    //   - Lots of free RAM but on charger + low-SoC  -> MONITOR candidate
    //   - Mid-RAM, multi-core, abi is arm64          -> TOOL candidate
    return when {
        snap.availRamMb >= 4_000 && snap.thermal <= 2 && snap.isCharging.not() -> ClusterRole.BRAIN
        snap.isCharging && snap.batteryPct <= 30 -> ClusterRole.MONITOR
        else -> ClusterRole.TOOL
    }
}

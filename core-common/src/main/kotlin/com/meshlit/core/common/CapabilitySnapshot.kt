package com.meshlit.core.common

/**
 * A snapshot of a node's capabilities at a point in time. Populated by
 * the capability probe (Phase 2) and refreshed periodically. Used by
 * the role suggester and the benchmark-on-join.
 *
 * RAM values are in MB. thermal is the Android `PowerManager.THERMAL_STATUS_*`
 * constant (0 = NONE, 6 = SHUTDOWN). `supportsNpu` / `supportsGpu` come
 * from feature probing — the actual accelerator isn't required; it's
 * "is there a delegate we can try?"
 */
data class CapabilitySnapshot(
    val totalRamMb: Long,
    val availRamMb: Long,
    val cpuCores: Int,
    val abi: String,
    val thermal: Int,
    val isCharging: Boolean,
    val batteryPct: Int,
    val supportsNpu: Boolean,
    val supportsGpu: Boolean,
    val suggestedRole: ClusterRole,
) {
    /** Memory pressure as a fraction of total RAM (0..1). Used by the router. */
    val ramPressure: Float
        get() = if (totalRamMb == 0L) 1f else (totalRamMb - availRamMb).toFloat() / totalRamMb

    /** Should this node accept new jobs right now? Combines thermal + RAM + battery. */
    val canAcceptWork: Boolean
        get() = thermal <= 3 && batteryPct >= 15 && ramPressure < 0.92f
}

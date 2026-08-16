package com.meshlit.core.probe

/**
 * A single-axis profile result. Each value is normalised to a
 * 0..1 score or a millisecond / percent / etc reading — never a
 * raw enum — so the role policy can compare across axes without a
 * unit-conversion table.
 *
 * @property score 0..1 capability for the role policy. Higher is
 *  better. `null` means the profiler couldn't read the axis.
 */
data class ProfileSample(
    val score: Float?,
    val rawValue: String,
)

/**
 * Aggregated hardware capability snapshot for a single node at a
 * single point in time. Each field maps to one
 * [com.meshlit.core.probe.HardwareProfiler] axis.
 *
 * The role policy and the router both read this snapshot — it's
 * the "what does this device do well" record that drives dynamic
 * decisions. The values are deliberately coarse so the type
 * doesn't churn every time Android exposes a new API.
 */
data class HardwareCapability(
    val cpu: ProfileSample,
    val memory: ProfileSample,
    val thermal: ProfileSample,
    val battery: ProfileSample,
    val network: ProfileSample,
    val npu: ProfileSample,
    val timestampMs: Long,
) {
    /**
     * Convenience: a quick boolean for the role policy. `true` when
     * the CPU is not in a thermal-throttle state (`thermal.score <
     * 0.5`).
     */
    val isThrottling: Boolean
        get() = (thermal.score ?: 0f) < 0.5f

    /**
     * Convenience: total RAM in MB extracted from `memory.rawValue`,
     * if it parses. The Android impl writes `<ramMb>` so this is a
     * cheap way to feed `RolePolicy.score` without forcing every
     * call site to parse.
     */
    val totalRamMb: Long?
        get() = memory.rawValue.toLongOrNull()

    /** Battery percent, parsed from `battery.rawValue`. */
    val batteryPct: Int?
        get() = battery.rawValue.toIntOrNull()

    /** Network reachable? Reads from `network.score`. */
    val networkReachable: Boolean
        get() = (network.score ?: 0f) > 0f

    /** NPU available? Reads from `npu.score`. */
    val hasNpu: Boolean
        get() = (npu.score ?: 0f) > 0f
}

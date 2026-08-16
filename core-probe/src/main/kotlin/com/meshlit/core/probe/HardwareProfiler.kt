package com.meshlit.core.probe

import com.meshlit.core.common.MeshlitResult

/**
 * One-axis hardware profiler. Implementations are independent — they
 * can be wired into the [HardwareProfilerRegistry] in any order and
 * each runs in parallel via `coroutineScope { async { profile() } }`.
 */
interface HardwareProfiler {
    /** Stable axis name — used as the key on `HardwareCapability`. */
    val axis: String

    suspend fun profile(): MeshlitResult<ProfileSample>
}

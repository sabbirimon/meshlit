package com.meshlit.core.probe

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Runs every registered [HardwareProfiler] in parallel and folds the
 * results into a single [HardwareCapability].
 *
 * A single failing profiler does not abort the whole snapshot — its
 * axis is filled with a zero-score [ProfileSample] so the role
 * policy can degrade gracefully (the role defaults to "Tool" or
 * "Monitor" when an axis is missing). The error is logged at warn
 * level so a flapping profiler is visible in logs.
 */
open class HardwareProfilerRegistry(
    private val profilers: List<HardwareProfiler>,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val log = logger("HardwareProfilerRegistry")

    // Cached most-recent snapshot. UiState-bearing view models
    // read this synchronously so the first frame renders real
    // values instead of a stale "Loading" state. Updated on every
    // profileAll() call.
    private val _latest = MutableStateFlow<HardwareCapability?>(null)
    val latest: StateFlow<HardwareCapability?> = _latest.asStateFlow()

    suspend fun profileAll(): MeshlitResult<HardwareCapability> = coroutineScope {
        val deferred = profilers.map { p ->
            async {
                val key = p.axis
                val sample = when (val res = p.profile()) {
                    is MeshlitResult.Success -> res.value
                    is MeshlitResult.Failure -> {
                        log.warn(
                            "probe.fail",
                            "profiler failed",
                            mapOf("axis" to key, "err" to res.error.tag),
                        )
                        ProfileSample(score = 0f, rawValue = "")
                    }
                }
                key to sample
            }
        }
        val results: Map<String, ProfileSample> =
            deferred.awaitAll().toMap()

        val cpu = results["cpu"] ?: ProfileSample(null, "")
        val memory = results["memory"] ?: ProfileSample(null, "")
        val thermal = results["thermal"] ?: ProfileSample(null, "")
        val battery = results["battery"] ?: ProfileSample(null, "")
        val network = results["network"] ?: ProfileSample(null, "")
        val npu = results["npu"] ?: ProfileSample(null, "")

        val cap = HardwareCapability(
            cpu = cpu,
            memory = memory,
            thermal = thermal,
            battery = battery,
            network = network,
            npu = npu,
            timestampMs = clock(),
        )
        _latest.value = cap
        MeshlitResult.Success(cap)
    }

    /**
     * Re-run the profiler suite and return the new snapshot. A
     * convenience wrapper around [profileAll] that propagates the
     * failure mode for the caller. Used by the v2 Device Info
     * screen's "Re-probe hardware" button — the result is ignored
     * if the underlying `MeshlitApplication.hostOS` can't read
     * certain axes (the OS-level call fails; the registry still
     * returns a partial snapshot).
     */
    suspend fun reprobe(): MeshlitResult<HardwareCapability> = profileAll()
}

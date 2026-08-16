package com.meshlit.core.probe

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
class HardwareProfilerRegistry(
    private val profilers: List<HardwareProfiler>,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val log = logger("HardwareProfilerRegistry")

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

        MeshlitResult.Success(
            HardwareCapability(
                cpu = cpu,
                memory = memory,
                thermal = thermal,
                battery = battery,
                network = network,
                npu = npu,
                timestampMs = clock(),
            ),
        )
    }
}

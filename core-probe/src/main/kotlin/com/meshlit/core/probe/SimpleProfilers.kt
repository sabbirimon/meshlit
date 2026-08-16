package com.meshlit.core.probe

import com.meshlit.core.common.MeshlitResult

/**
 * Pre-built profiler implementations backed by a supplier function.
 * Tests use these to inject synthetic data; production code wraps
 * the Android `SystemService` calls in suppliers of the same shape.
 */
class CpuProfiler(
    private val supplier: suspend () -> MeshlitResult<ProfileSample>,
) : HardwareProfiler {
    override val axis: String = "cpu"
    override suspend fun profile(): MeshlitResult<ProfileSample> = supplier()
}

class MemoryProfiler(
    private val supplier: suspend () -> MeshlitResult<ProfileSample>,
) : HardwareProfiler {
    override val axis: String = "memory"
    override suspend fun profile(): MeshlitResult<ProfileSample> = supplier()
}

class ThermalProfiler(
    private val supplier: suspend () -> MeshlitResult<ProfileSample>,
) : HardwareProfiler {
    override val axis: String = "thermal"
    override suspend fun profile(): MeshlitResult<ProfileSample> = supplier()
}

class BatteryProfiler(
    private val supplier: suspend () -> MeshlitResult<ProfileSample>,
) : HardwareProfiler {
    override val axis: String = "battery"
    override suspend fun profile(): MeshlitResult<ProfileSample> = supplier()
}

class NetworkProfiler(
    private val supplier: suspend () -> MeshlitResult<ProfileSample>,
) : HardwareProfiler {
    override val axis: String = "network"
    override suspend fun profile(): MeshlitResult<ProfileSample> = supplier()
}

class NpuProfiler(
    private val supplier: suspend () -> MeshlitResult<ProfileSample>,
) : HardwareProfiler {
    override val axis: String = "npu"
    override suspend fun profile(): MeshlitResult<ProfileSample> = supplier()
}

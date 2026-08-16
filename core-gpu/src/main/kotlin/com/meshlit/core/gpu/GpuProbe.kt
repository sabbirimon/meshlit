package com.meshlit.core.gpu

/** Snapshot returned by [GpuDetector.probe]. */
data class GpuProbe(
    val backend: GpuBackend,
    val devices: List<GpuDevice>,
) {
    val hasExternalGpu: Boolean get() = devices.any { it.isExternal }

    companion object {
        val None = GpuProbe(backend = GpuBackend.NONE, devices = emptyList())
    }
}
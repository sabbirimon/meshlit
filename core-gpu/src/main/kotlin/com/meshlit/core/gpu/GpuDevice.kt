package com.meshlit.core.gpu

/** One physical GPU on the host. */
data class GpuDevice(
    val vendor: String,
    val vramMb: Long?,
    val apiVersion: String,
    val backend: GpuBackend,
    /** True when this GPU is on an external bus (USB4/TB3/4) instead
     *  of integrated on the SoC. */
    val isExternal: Boolean,
    val bus: String,
    val pcieGeneration: Int?,
)
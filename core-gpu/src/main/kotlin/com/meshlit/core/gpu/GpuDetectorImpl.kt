package com.meshlit.core.gpu

/**
 * Pure-Kotlin GPU probe. Given a [VulkanFeatureProbe] + an optional
 * external-Gpu source it returns a [GpuProbe] suitable for the UI
 * and the inference backend picker.
 *
 * It deliberately does **not** enumerate `VkPhysicalDevice` because
 * that requires a native `libmeshlit_gpu.so` that has not yet been
 * built. The Java-side probe is enough to:
 *  - Show "Vulkan 1.x supported / unsupported" in the Advanced →
 *    Devices → GPU panel.
 *  - Decide whether to route a model through Vulkan or fall back to
 *    CPU in [com.meshlit.core.gpu.BackendHintsPicker].
 */
class GpuDetector(
    private val vulkanFeatureProbe: VulkanFeatureProbe = NoVulkan,
    private val externalGpuSource: ExternalGpuSource = NoExternalGpu,
) {
    fun probe(): GpuProbe {
        val apiVersion = vulkanFeatureProbe.probeVulkanVersion()
        if (apiVersion == null) {
            return GpuProbe.None
        }
        val backend = GpuBackend.VULKAN
        val integrated = GpuDevice(
            vendor = "Integrated GPU",
            vramMb = null,
            apiVersion = apiVersion,
            backend = backend,
            isExternal = false,
            bus = "on-SoC",
            pcieGeneration = null,
        )
        val external = externalGpuSource.read()?.let { egpu ->
            GpuDevice(
                vendor = egpu.vendorTag,
                vramMb = egpu.vramMb,
                apiVersion = apiVersion,
                backend = backend,
                isExternal = true,
                bus = egpu.bus,
                pcieGeneration = egpu.pcieGeneration,
            )
        }
        return GpuProbe(
            backend = backend,
            devices = listOfNotNull(integrated, external),
        )
    }
}

/** A single source of truth for the connected eGPU. */
fun interface ExternalGpuSource {
    fun read(): Snapshot?

    /** Static snapshot of the currently-attached eGPU. */
    data class Snapshot(
        val vendorTag: String,
        val vramMb: Long?,
        val bus: String,
        val pcieGeneration: Int?,
    )
}

val NoExternalGpu: ExternalGpuSource = ExternalGpuSource { null }
package com.meshlit.core.gpu

/** Compute backends supported by the Meshlit Android GPU layer. */
enum class GpuBackend(val tag: String, val displayName: String) {
    NONE("none", "CPU only"),
    VULKAN("vulkan", "Vulkan"),
}

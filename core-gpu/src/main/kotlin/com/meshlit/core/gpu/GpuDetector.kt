package com.meshlit.core.gpu

/**
 * Abstraction over Android's Vulkan feature flags so the detector can
 * run on emulators, JVM unit tests, and real devices.
 *
 * The production implementation reads
 *  - [PackageManager.hasSystemFeature] with `android.hardware.vulkan.level`
 *  - [PackageManager.hasSystemFeature] with `android.hardware.vulkan.version`
 * and returns the Vulkan API level (e.g. "1.3") that the system
 * reports. Real JNI enumeration of physical GPUs and eGPU PCI
 * detection will plug in at a later step; today the probe is the
 * coarse system-feature signal that every Android API ≥ 26 supports.
 */
fun interface VulkanFeatureProbe {
    /** Returns the Vulkan API version string, or null if not supported. */
    fun probeVulkanVersion(): String?
}

/** Used when we cannot read system features at all (tests, JVM). */
val NoVulkan: VulkanFeatureProbe = VulkanFeatureProbe { null }
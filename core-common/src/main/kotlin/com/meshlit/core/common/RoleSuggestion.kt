package com.meshlit.core.common

/**
 * Chipset-aware role-suggestion rules. Per BUILD_GUIDE §0 principle 2,
 * roles are *advisory* — the user can override from Settings → Device.
 *
 * The rule of thumb is "what does this device do well?":
 *  - High-RAM + flagship SoC + GPU/NPU  -> BRAIN
 *  - Mid-tier SoC + decent RAM + storage -> TOOL
 *  - Anything running low battery or on charge-only -> MONITOR
 *  - Older / weaker phones -> MONITOR or refuse
 *
 * The full rule table lives here so it can be unit-tested without
 * Android dependencies. The probe and UI consume it.
 */
object RoleSuggestion {

    /**
     * Suggest a role from the resolved device + capability snapshot.
     * Honors the user's override if present.
     */
    fun suggest(
        device: EffectiveDeviceInfo,
        capability: CapabilitySnapshot,
        override: ClusterRole? = null,
    ): ClusterRole {
        override?.let { return it }
        return suggestFromSignals(device, capability, device.externalGpu)
    }

    /**
     * Overload that lets the caller pass an explicit eGPU. Useful
     * for the "I just plugged in an eGPU — should I bump from TOOL
     * to BRAIN?" recompute flow.
     */
    fun suggest(
        device: EffectiveDeviceInfo,
        capability: CapabilitySnapshot,
        externalGpu: EGpuConnection?,
    ): ClusterRole = suggestFromSignals(device, capability, externalGpu)

    /**
     * Overload that adds the [HostOS] signal. On Linux x86_64 hosts
     * (Waydroid, Android-x86, AVD, ChromeOS ARC) the device is
     * effectively desktop-class: AVX2 SIMD, no thermal throttle,
     * plenty of RAM. The role-suggestion rules reflect this by
     * allowing BRAIN even on chipsets that would otherwise be TOOL.
     *
     * If [hostOS] is null we assume stock Android (the safe default).
     */
    fun suggest(
        device: EffectiveDeviceInfo,
        capability: CapabilitySnapshot,
        externalGpu: EGpuConnection?,
        hostOS: HostOS?,
    ): ClusterRole {
        val suggested = suggestFromSignals(device, capability, externalGpu)
        // x86 hosts: even a LIGHT-fit x86 chip outperforms a
        // FRONTIER-fit phone (no thermal envelope, AVX2 SIMD). Bump
        // MONITOR → TOOL on Linux x86_64.
        if (hostOS != null && hostOS.isX86Host && suggested == ClusterRole.MONITOR) {
            return ClusterRole.TOOL
        }
        return suggested
    }

    private fun suggestFromSignals(
        device: EffectiveDeviceInfo,
        cap: CapabilitySnapshot,
        egpu: EGpuConnection?,
    ): ClusterRole {
        val ramGb = device.totalRamMb / 1024
        val thermalStress = cap.thermal >= 4      // THERMAL_STATUS_4 = overheating
        val veryLowBattery = cap.batteryPct < 15 && !cap.isCharging

        // Hard "no" first: device is in distress.
        if (veryLowBattery) return ClusterRole.MONITOR

        // eGPU is a power multiplier. If it's attached and has a
        // working driver, the SoC ceiling doesn't constrain us —
        // we can host whatever the eGPU can handle. An 8 GB VRAM
        // eGPU bumps a TOOL phone to BRAIN+ (it can run a 13B Q4
        // locally with the matmul offloaded).
        if (egpu != null && egpu.driverStatus != EGpuDriverAvailability.LINUX_ONLY) {
            val vramGb = (egpu.vramMb ?: 0) / 1024
            val driverWorks = egpu.driverStatus == EGpuDriverAvailability.OPEN_SOURCE ||
                              egpu.driverStatus == EGpuDriverAvailability.THROUGH_ZLUDA_OR_REMOTE
            if (driverWorks) {
                return when {
                    vramGb >= 12 -> ClusterRole.BRAIN    // 70B at Q2 via sharding
                    vramGb >= 6 -> ClusterRole.BRAIN     // 13B Q4 comfortably
                    else -> ClusterRole.TOOL             // small eGPU, useful for MCP/embed
                }
            }
        }

        // Then fit-based on SoC alone.
        return when (device.socFamily.inferenceFit) {
            InferenceFit.FRONTIER -> {
                if (ramGb >= 8 && !thermalStress) ClusterRole.BRAIN else ClusterRole.TOOL
            }
            InferenceFit.MID_HIGH -> {
                if (ramGb >= 6 && !thermalStress) ClusterRole.BRAIN else ClusterRole.TOOL
            }
            InferenceFit.MID -> ClusterRole.TOOL
            InferenceFit.LIGHT -> ClusterRole.MONITOR
            InferenceFit.UNKNOWN -> {
                // Fall back to RAM-only heuristic.
                when {
                    ramGb >= 8 && !thermalStress -> ClusterRole.BRAIN
                    ramGb >= 4 -> ClusterRole.TOOL
                    else -> ClusterRole.MONITOR
                }
            }
        }
    }

    /**
     * RAM budget available for model weights, accounting for OS overhead.
     * Used by the model-fit picker: a 4 GB phone has roughly 2.5 GB
     * available for llama.cpp's KV cache + weights; Q4_K_M fits roughly
     * model_size_mb ≈ ram_mb / 1.5 for inference, / 2.5 for training.
     */
    fun availableRamForModel(totalRamMb: Long): Long {
        // Reserve 1.5 GB for the OS + app, 0.5 GB for KV cache headroom,
        // and assume the model can take the rest.
        val reserved = 1500 + 500
        return (totalRamMb - reserved).coerceAtLeast(0)
    }

    /**
     * Approximate max quant size that fits in available RAM, in MB.
     */
    fun maxModelSizeMb(totalRamMb: Long): Long {
        val budget = availableRamForModel(totalRamMb)
        // Q4_K_M is roughly 0.5 bytes/param; we want to fit ~85% of budget
        // to leave headroom for OS swapping.
        val params = (budget * 1024 * 1024 * 0.85f / 0.5f).toLong()
        // Convert back to MB: 4 bytes per param.
        return params * 4 / 1024 / 1024
    }
}
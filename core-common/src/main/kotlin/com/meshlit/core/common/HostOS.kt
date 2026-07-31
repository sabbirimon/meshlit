package com.meshlit.core.common

import kotlinx.serialization.Serializable

/**
 * The OS hosting the Android runtime. Most installs run on stock
 * Android (AOSP / Samsung / Pixel / Chinese forks), but a growing
 * number of developers run Meshlit inside an x86 host:
 *
 *  - Android Emulator (AVD) on x86_64 Linux/macOS/Windows — qemu/kvm
 *  - Waydroid (Linux-only, containerized Android on Ubuntu/Fedora/Arch)
 *  - Android-x86 (Bliss OS, Prime OS, Phoenix OS — full desktop install)
 *  - ChromeOS ARC / ARCVM (Crostini)
 *  - Genymotion, Bluestacks, NoxPlayer (x86 emulators)
 *  - Anbox (Ubuntu Touch containerized Android)
 *
 * Meshlit treats each host differently:
 *
 *  - On Linux x86_64 the CPU is desktop-class: AVX2/AVX-512, no thermal
 *    throttle, lots of RAM. Inference runs at 5–20x phone speed even
 *    without an eGPU. llama.cpp's native x86 SIMD path is mature.
 *  - On Linux x86 with CUDA/ROCm/oneAPI available, the eGPU backend
 *    landscape is much wider than on Android (which only has Vulkan).
 *  - On ChromeOS ARC the user can hot-plug USB-eGPU through Crostini,
 *    giving us a desktop-class compute path on a thin laptop.
 *  - On Anbox we still detect the host but mark inference fit as
 *    MID (the kernel is shared with the host OS).
 *
 * Detection sources (ranked by reliability):
 *  1. `ro.kernel.qemu = 1`            — qemu-based emulator (AVD, Waydroid, Anbox)
 *  2. `ro.boot.hardware.platform`     — ChromeOS sets this
 *  3. `ro.product.cpu.abi`            — `x86` or `x86_64`
 *  4. `/proc/cpuinfo` "model name"     — host CPU model
 *  5. `/sys/class/dmi/id/product_name`— DMI product (Bliss OS, Prime OS)
 *  6. Package fingerprint of system apps — "com.android.emu" → emulator
 */
@Serializable
enum class HostOS(
    val tag: String,
    val displayName: String,
    /** True when the runtime is Android-on-something-other-than-stock. */
    val isContainerized: Boolean,
    /** Hint: does the host expose a desktop-class GPU we can target? */
    val hasDesktopGpu: Boolean,
    /** Whether we should mark this node as BRAIN-eligible by default. */
    val defaultBrainEligible: Boolean,
) {
    /** Stock Android (AOSP / Pixel / Samsung / Chinese OEM forks). */
    ANDROID(
        tag = "android",
        displayName = "Android",
        isContainerized = false,
        hasDesktopGpu = false,
        defaultBrainEligible = true,
    ),

    /** Android Studio AVD — x86_64 image, kvm-accelerated. */
    ANDROID_EMULATOR(
        tag = "android_emulator",
        displayName = "Android Emulator (AVD)",
        isContainerized = true,
        hasDesktopGpu = true,
        defaultBrainEligible = true,
    ),

    /** Waydroid — Android container on Linux x86_64. */
    WAYDROID(
        tag = "waydroid",
        displayName = "Waydroid (Linux container)",
        isContainerized = true,
        hasDesktopGpu = true,
        defaultBrainEligible = true,
    ),

    /** ChromeOS ARC / ARCVM — Android VM running on ChromeOS. */
    CHROMEOS_ARC(
        tag = "chromeos_arc",
        displayName = "ChromeOS ARC / ARCVM",
        isContainerized = true,
        hasDesktopGpu = true,
        defaultBrainEligible = true,
    ),

    /** Bliss OS / Prime OS / Phoenix OS — Android-x86 installed natively. */
    ANDROID_X86(
        tag = "android_x86",
        displayName = "Android-x86 (Bliss / Prime / Phoenix)",
        isContainerized = false,
        hasDesktopGpu = true,
        defaultBrainEligible = true,
    ),

    /** Genymotion / Bluestacks / NoxPlayer / LDPlayer — third-party emulators. */
    THIRD_PARTY_EMULATOR(
        tag = "third_party_emulator",
        displayName = "Third-party emulator (Genymotion / Bluestacks)",
        isContainerized = true,
        hasDesktopGpu = true,
        defaultBrainEligible = true,
    ),

    /** Anbox — Android in Ubuntu Touch. */
    ANBOX(
        tag = "anbox",
        displayName = "Anbox (Ubuntu Touch)",
        isContainerized = true,
        hasDesktopGpu = false,
        defaultBrainEligible = false,
    ),

    /** HarmonyOS NEXT — AOSP-compatibility shim. */
    HARMONYOS(
        tag = "harmonyos",
        displayName = "HarmonyOS NEXT",
        isContainerized = false,
        hasDesktopGpu = false,
        defaultBrainEligible = true,
    ),

    /** Unrecognized host (catch-all). */
    UNKNOWN(
        tag = "unknown",
        displayName = "Unknown host",
        isContainerized = false,
        hasDesktopGpu = false,
        defaultBrainEligible = false,
    );

    /** True when the runtime is x86-architecture (Linux or ChromeOS or
     *  third-party emulator). Used by the role-suggester to bump the
     *  BRAIN-eligibility threshold. */
    val isX86Host: Boolean get() = this != ANDROID && this != HARMONYOS && this != UNKNOWN

    companion object {
        fun fromTag(tag: String): HostOS = entries.firstOrNull { it.tag == tag } ?: UNKNOWN
    }
}

/**
 * Detailed result of a host-OS probe. The probe runs on app start
 * (after the existing chipset probe) and writes a [HostOS] plus the
 * supporting evidence so the user can verify the detection.
 */
@Serializable
data class HostOSDetection(
    val hostOS: HostOS,
    /** Detected ABI family: "arm64-v8a", "x86_64", "x86", "riscv64". */
    val abi: String,
    /** True when running inside qemu (qemu.kvm / Android Emulator). */
    val isQemu: Boolean,
    /** True when running inside Crostini / ARCVM. */
    val isChromeOSArc: Boolean,
    /** Host kernel version (e.g. "5.15.0-91-generic" or "6.1.0-android13"). */
    val kernelVersion: String,
    /** Host product name from /sys/class/dmi/id/product_name, or null. */
    val hostProduct: String?,
    /** Host CPU model from /proc/cpuinfo, or null. */
    val hostCpuModel: String?,
    /** True when /dev/dri/renderD128 exists (host GPU passthrough). */
    val hostGpuRenderNode: Boolean,
    /** True when nvidia-smi is on PATH (CUDA host). */
    val hostHasNvidiaSmi: Boolean,
    /** True when rocm-smi is on PATH (AMD ROCm host). */
    val hostHasRocmSmi: Boolean,
    /** True when `/dev/kfd` is accessible (AMD KFD). */
    val hostHasKfd: Boolean,
    /** True when oneAPI level-zero / sycl-ls works (Intel GPU host). */
    val hostHasOneApi: Boolean,
) {
    /** Convenience: the recommended eGPU backend for this host.
     *  Returns `null` when the host is plain Android with no eGPU. */
    val preferredDesktopBackend: DesktopBackend?
        get() = when {
            hostHasNvidiaSmi -> DesktopBackend.CUDA
            hostHasRocmSmi || hostHasKfd -> DesktopBackend.ROCM
            hostHasOneApi -> DesktopBackend.ONEAPI
            hostGpuRenderNode -> DesktopBackend.VULKAN
            else -> null
        }
}

/**
 * Desktop-class GPU backends available when running on Linux/x86.
 * Distinct from the mobile [com.meshlit.core.common.EGpuBackend] —
 * desktop backends assume a full driver stack and lots of VRAM.
 */
@Serializable
enum class DesktopBackend(val tag: String, val displayName: String) {
    CUDA("cuda", "NVIDIA CUDA"),
    ROCM("rocm", "AMD ROCm"),
    ONEAPI("oneapi", "Intel oneAPI / Level Zero"),
    VULKAN("vulkan", "Vulkan (generic)"),
    OPENCL("opencl", "OpenCL"),
    METAL("metal", "Apple Metal (macOS only)"),
    CPU("cpu", "CPU only (no GPU)"),
    UNKNOWN("unknown", "Unknown backend");

    companion object {
        fun fromTag(tag: String): DesktopBackend = entries.firstOrNull { it.tag == tag } ?: UNKNOWN
    }
}
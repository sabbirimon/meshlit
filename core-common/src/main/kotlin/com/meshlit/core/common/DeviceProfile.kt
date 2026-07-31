package com.meshlit.core.common

import kotlinx.serialization.Serializable

/**
 * The kind of node a peer represents. Independent of [ClusterRole] —
 * a form factor ("phone", "laptop") is orthogonal to what the node
 * actually does in the cluster (Brain / Tool / Monitor / gateway).
 *
 * Why both? Because:
 *  - Form-factor drives thermal/RAM/storage expectations. A laptop
 *    is a different cooling envelope than a phone.
 *  - Role drives dispatch. A node can play multiple roles over time
 *    (Phase 4.5 bidirectional), but its form factor doesn't change.
 *  - Permission models differ. A `LAPTOP` peer can expose its full
 *    filesystem; a `PHONE` peer can't.
 */
@Serializable
enum class NodeKind(val tag: String, val displayName: String) {
    PHONE("phone", "Phone"),
    TABLET("tablet", "Tablet"),
    LAPTOP("laptop", "Laptop"),
    DESKTOP("desktop", "Desktop / Mini-PC"),
    SERVER("server", "Server / VPS"),
    ROUTER("router", "Router / NAS"),
    SINGLE_BOARD("sbc", "Single-board computer"),
    IOT("iot", "IoT device"),
    STORAGE("storage", "External storage"),
    UNKNOWN("unknown", "Unknown device");

    companion object {
        fun fromTag(tag: String): NodeKind = entries.firstOrNull { it.tag == tag } ?: UNKNOWN
    }
}

/**
 * Complete identity of a device. The app is installed on.
 *
 * Captured at first launch (auto-detected) and editable from
 * Settings → Device. The user can override anything the auto-detect got
 * wrong (custom ROMs, brand-masked devices, remote setup via ADB), and
 * the override is the source of truth.
 *
 * `detection` is what the probe found. `override_` is what the user
 * confirmed or changed. `effective` is the resolved set the rest of
 * the app uses (override wins if present, else detection).
 */
@Serializable
data class DeviceProfile(
    val detection: DetectedDeviceInfo,
    val override: UserOverride? = null,
    val nodeKind: NodeKind = NodeKind.PHONE,
    val connectedPeripherals: List<PeripheralDevice> = emptyList(),
    val knownPeerNodes: List<PeerNodeSummary> = emptyList(),
) {
    val effective: EffectiveDeviceInfo
        get() = override?.resolve(detection) ?: detection.toEffective()

    val hasOverride: Boolean get() = override != null
}

/**
 * A device attached to this phone via USB host mode, Bluetooth, or
 * another wired connection. Not a cluster peer — a *peripheral*.
 *
 * Examples: USB-C hub, external SSD, Bluetooth keyboard, USB-C to
 * Ethernet adapter, Bluetooth audio device, USB mouse, USB MIDI.
 */
@Serializable
data class PeripheralDevice(
    val kind: PeripheralKind,
    val name: String?,
    val transport: PeripheralTransport,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val storageCapacityMb: Long? = null,
    val isMounted: Boolean = false,
    val mountPath: String? = null,
)

@Serializable
enum class PeripheralKind(val tag: String, val displayName: String) {
    USB_HUB("usb_hub", "USB hub"),
    USB_STORAGE("usb_storage", "USB storage"),
    USB_NETWORK("usb_network", "USB network adapter"),
    USB_DISPLAY("usb_display", "USB display"),
    USB_EGPU("usb_egpu", "External GPU (eGPU)"),
    USB_AUDIO("usb_audio", "USB audio"),
    USB_KEYBOARD("usb_keyboard", "USB keyboard"),
    USB_MOUSE("usb_mouse", "USB mouse"),
    USB_MIDI("usb_midi", "USB MIDI"),
    BLUETOOTH_HID("bt_hid", "Bluetooth HID"),
    BLUETOOTH_AUDIO("bt_audio", "Bluetooth audio"),
    BLUETOOTH_STORAGE("bt_storage", "Bluetooth storage"),
    EXTERNAL_DISPLAY("ext_display", "External display"),
    OTHER("other", "Other peripheral");

    companion object {
        fun fromTag(tag: String): PeripheralKind = entries.firstOrNull { it.tag == tag } ?: OTHER
    }
}

@Serializable
enum class PeripheralTransport(val tag: String) {
    USB("usb"),
    BLUETOOTH("bluetooth"),
    WIFI_DISPLAY("wifi_display"),
    NETWORK("network"),
    OTHER("other");

    companion object {
        fun fromTag(tag: String): PeripheralTransport = entries.firstOrNull { it.tag == tag } ?: OTHER
    }
}

/**
 * A peer in the cluster, as known by this node. Kept lightweight —
 * the full profile is fetched on demand. This is what the orchestrator
 * caches for routing decisions.
 */
@Serializable
data class PeerNodeSummary(
    val nodeId: String,
    val displayName: String,
    val nodeKind: NodeKind,
    val preferredRole: ClusterRole,
    val trustTier: String,            // TrustTier.tag
    val lastSeenMs: Long,
    val reachable: Boolean,
    val lanAddress: String? = null,   // "192.168.1.42:8888"
    val tailscaleAddress: String? = null,
    val wgAddress: String? = null,
)

/**
 * What the system probe found. These are *signals* — not commitments.
 * For example, "socFamily = SNAPDRAGON" is inferred from
 * `Build.HARDWARE` and `Build.SOC_MANUFACTURER`, both of which can be
 * spoofed on custom ROMs. The user can override any of it.
 */
@Serializable
data class DetectedDeviceInfo(
    val manufacturer: String,           // Build.MANUFACTURER (e.g. "samsung")
    val brand: String,                  // Build.BRAND (e.g. "google")
    val model: String,                  // Build.MODEL (e.g. "Pixel 7 Pro")
    val device: String,                 // Build.DEVICE (e.g. "cheetah")
    val product: String,                // Build.PRODUCT
    val hardware: String,               // Build.HARDWARE (chipset key)
    val board: String,                  // Build.BOARD
    val abis: List<String>,             // Build.SUPPORTED_ABIS, preferred first
    val primaryAbi: String,             // abis[0]
    val socFamily: SocFamily,           // inferred chipset family
    val socModel: String?,              // best-effort model name (e.g. "SM8550")
    val gpuFamily: GpuFamily,           // inferred GPU vendor
    val hasNpu: Boolean,                // best-effort NPU presence
    val npuName: String?,               // "Hexagon", "APU 790", "NPU 1.0", etc.
    val totalRamMb: Long,               // ActivityManager.MemoryInfo.totalMem
    val availableRamMb: Long,           // at probe time
    val totalStorageMb: Long,           // StatFs
    val availableStorageMb: Long,
    val cpuCoreCount: Int,              // Runtime.availableProcessors
    val cpuMaxFreqKHz: Long,            // /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq
    val androidVersion: String,         // Build.VERSION.RELEASE ("14")
    val androidSdkInt: Int,             // Build.VERSION.SDK_INT
    val securityPatch: String?,         // Build.VERSION.SECURITY_PATCH
    val buildFingerprint: String,       // Build.FINGERPRINT (full identifier)
    val buildType: String,              // Build.TYPE ("user", "userdebug", "eng")
    /** eGPU detected at probe time, if any. Filled by AndroidEGpuProbe. */
    val detectedExternalGpu: EGpuConnection? = null,
) {
    fun toEffective(): EffectiveDeviceInfo = EffectiveDeviceInfo(
        manufacturer = manufacturer,
        brand = brand,
        model = model,
        device = device,
        primaryAbi = primaryAbi,
        abis = abis,
        socFamily = socFamily,
        socModel = socModel,
        gpuFamily = gpuFamily,
        hasNpu = hasNpu,
        npuName = npuName,
        totalRamMb = totalRamMb,
        cpuCoreCount = cpuCoreCount,
        androidVersion = androidVersion,
        androidSdkInt = androidSdkInt,
        externalGpu = detectedExternalGpu,
    )
}

/**
 * The user's manual override. Every field is optional so the override can
 * patch *some* of the auto-detected info and let the rest stand. Built
 * this way so a "I have 12 GB but the system reports 8 GB" override
 * doesn't require the user to re-enter every other field.
 */
@Serializable
data class UserOverride(
    val manufacturer: String? = null,
    val model: String? = null,
    val socFamily: SocFamily? = null,
    val socModel: String? = null,
    val gpuFamily: GpuFamily? = null,
    val hasNpu: Boolean? = null,
    val npuName: String? = null,
    val totalRamMb: Long? = null,
    val cpuCoreCount: Int? = null,
    val primaryAbi: String? = null,
    val nodeKind: NodeKind? = null,
    val note: String? = null,             // free-text "why I overrode"
    val externalGpuOverride: EGpuConnection? = null,
) {
    fun resolve(detection: DetectedDeviceInfo): EffectiveDeviceInfo = EffectiveDeviceInfo(
        manufacturer = manufacturer ?: detection.manufacturer,
        brand = detection.brand,
        model = model ?: detection.model,
        device = detection.device,
        primaryAbi = primaryAbi ?: detection.primaryAbi,
        abis = detection.abis,
        socFamily = socFamily ?: detection.socFamily,
        socModel = socModel ?: detection.socModel,
        gpuFamily = gpuFamily ?: detection.gpuFamily,
        hasNpu = hasNpu ?: detection.hasNpu,
        npuName = npuName ?: detection.npuName,
        totalRamMb = totalRamMb ?: detection.totalRamMb,
        cpuCoreCount = cpuCoreCount ?: detection.cpuCoreCount,
        androidVersion = detection.androidVersion,
        androidSdkInt = detection.androidSdkInt,
        externalGpu = externalGpuOverride ?: detection.detectedExternalGpu,
    )
}

/**
 * The resolved device identity plus the user-confirmed node kind.
 */
@Serializable
data class ResolvedDeviceProfile(
    val device: EffectiveDeviceInfo,
    val nodeKind: NodeKind,
)

/**
 * The resolved device identity the rest of the app uses.
 */
@Serializable
data class EffectiveDeviceInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val primaryAbi: String,
    val abis: List<String>,
    val socFamily: SocFamily,
    val socModel: String?,
    val gpuFamily: GpuFamily,
    val hasNpu: Boolean,
    val npuName: String?,
    val totalRamMb: Long,
    val cpuCoreCount: Int,
    val androidVersion: String,
    val androidSdkInt: Int,
    /** eGPU currently attached (locally or via remote peer). */
    val externalGpu: EGpuConnection? = null,
) {
    /** Human-readable label: "Pixel 7 Pro (Tensor G2, Mali-G710, 12 GB)". */
    val displayLabel: String
        get() = buildString {
            append(model)
            append(" (")
            append(socFamily.displayName)
            socModel?.let { append(" ").append(it) }
            append(", ")
            append(gpuFamily.displayName)
            append(", ")
            append(totalRamMb / 1024).append(" GB")
            externalGpu?.let { egpu ->
                append(" + ")
                append(egpu.kind.displayName)
            }
            append(")")
        }

    /** Has any eGPU attached (local enclosure OR remote peer forwarding one). */
    val hasExternalGpu: Boolean get() = externalGpu != null
}

/**
 * The chipset family. Comprehensive enough to cover the realistic
 * Android device pool in 2024–2026. Unknown/OTHER is the catch-all for
 * devices that don't match a known pattern.
 *
 * `inferenceFit` is the rough ceiling for what this family can run.
 * It's a hint, not a hard rule (BUILD_GUIDE §0 principle 2: roles
 * are advisory).
 */
@Serializable
enum class SocFamily(
    val tag: String,
    val displayName: String,
    val inferenceFit: InferenceFit,
) {
    // Qualcomm — most common high-end Android
    SNAPDRAGON_8("snapdragon_8", "Snapdragon 8-series", InferenceFit.FRONTIER),
    SNAPDRAGON_7("snapdragon_7", "Snapdragon 7-series", InferenceFit.MID_HIGH),
    SNAPDRAGON_6("snapdragon_6", "Snapdragon 6-series", InferenceFit.MID),
    SNAPDRAGON_4("snapdragon_4", "Snapdragon 4-series", InferenceFit.LIGHT),
    SNAPDRAGON_OLDER("snapdragon_older", "Snapdragon (older)", InferenceFit.MID),

    // MediaTek
    DIMENSITY_9000("dimensity_9000", "MediaTek Dimensity 9000+", InferenceFit.FRONTIER),
    DIMENSITY_8000("dimensity_8000", "MediaTek Dimensity 8000-series", InferenceFit.MID_HIGH),
    DIMENSITY_7000("dimensity_7000", "MediaTek Dimensity 7000-series", InferenceFit.MID_HIGH),
    DIMENSITY_6000("dimensity_6000", "MediaTek Dimensity 6000-series", InferenceFit.MID),
    DIMENSITY_1000("dimensity_1000", "MediaTek Dimensity 1000-series", InferenceFit.MID),
    HELIO_G99("helio_g99", "MediaTek Helio G99", InferenceFit.MID),
    HELIO_OLDER("helio_older", "MediaTek Helio (older)", InferenceFit.LIGHT),

    // Samsung Exynos (regional variants of Galaxy S/Note/A)
    EXYNYS_2400("exynos_2400", "Samsung Exynos 2400", InferenceFit.FRONTIER),
    EXYNOS_2200("exynos_2200", "Samsung Exynos 2200", InferenceFit.MID_HIGH),
    EXYNOS_2100("exynos_2100", "Samsung Exynos 2100", InferenceFit.MID_HIGH),
    EXYNOS_1400("exynos_1400", "Samsung Exynos 1400-series", InferenceFit.MID),
    EXYNOS_OLDER("exynos_older", "Samsung Exynos (older)", InferenceFit.MID),

    // Google Tensor
    TENSOR_G5("tensor_g5", "Google Tensor G5", InferenceFit.FRONTIER),
    TENSOR_G4("tensor_g4", "Google Tensor G4", InferenceFit.MID_HIGH),
    TENSOR_G3("tensor_g3", "Google Tensor G3", InferenceFit.MID_HIGH),
    TENSOR_G2("tensor_g2", "Google Tensor G2", InferenceFit.MID),
    TENSOR_G1("tensor_g1", "Google Tensor G1", InferenceFit.MID),

    // HiSilicon Kirin (Huawei)
    KIRIN_9000("kirin_9000", "HiSilicon Kirin 9000-series", InferenceFit.MID_HIGH),
    KIRIN_8000("kirin_8000", "HiSilicon Kirin 8000-series", InferenceFit.MID),
    KIRIN_OLDER("kirin_older", "HiSilicon Kirin (older)", InferenceFit.LIGHT),

    // Unisoc / Spreadtrum (entry-level)
    UNISOC_TIGER("unisoc_tiger", "Unisoc Tiger", InferenceFit.LIGHT),
    UNISOC_OLDER("unisoc_older", "Unisoc (older)", InferenceFit.LIGHT),

    // x86 (emulators, some Chromebooks, very rare Intel-based Android tablets)
    X86_INTEL("x86_intel", "Intel x86", InferenceFit.LIGHT),
    X86_AMD("x86_amd", "AMD x86", InferenceFit.LIGHT),

    // RISC-V (emerging, mostly Chinese OEMs)
    RISCV("riscv", "RISC-V", InferenceFit.UNKNOWN),

    // Catch-all
    OTHER("other", "Unknown chipset", InferenceFit.UNKNOWN);

    companion object {
        fun fromTag(tag: String): SocFamily = entries.firstOrNull { it.tag == tag } ?: OTHER
    }
}

/**
 * How heavy an inference job this device can realistically run.
 * Used by the role-suggestion rules and the model-fit picker.
 */
@Serializable
enum class InferenceFit {
    /** Frontier-class 7B+ at Q4, 13B+ at Q2. */
    FRONTIER,
    /** Mid-tier 3B–7B at Q4, 13B at Q2. */
    MID_HIGH,
    /** 1B–3B at Q4, 7B at Q2. */
    MID,
    /** Small models only (≤ 1B). */
    LIGHT,
    /** We don't know yet — probe didn't return enough signal. */
    UNKNOWN,
}

/**
 * GPU family. Drives which llama.cpp GPU backend to attempt.
 * Per skills/llama-cpp-android/SKILL.md: "check at runtime, not assumed
 * from SoC name alone" — this enum is the *probe result*, not a guess.
 */
@Serializable
enum class GpuFamily(val tag: String, val displayName: String) {
    ADRENO("adreno", "Adreno"),
    MALI("mali", "Mali"),
    IMMORTALIS("immortalis", "Immortalis"),
    POWERVR("powervr", "PowerVR"),
    XCLIPSE("xclipse", "Xclipse (AMD RDNA)"),
    IMG("img", "IMG"),
    NVIDIA("nvidia", "NVIDIA"),
    NONE("none", "No GPU / software"),
    UNKNOWN("unknown", "Unknown");

    companion object {
        fun fromTag(tag: String): GpuFamily = entries.firstOrNull { it.tag == tag } ?: UNKNOWN
    }
}
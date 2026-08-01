package com.meshlit.core.common

import kotlinx.serialization.Serializable

/**
 * External GPU detected on this node. The phone acts as the controller
 * + thermal head + display; the eGPU does the heavy matmul work.
 *
 * Covers the realistic 2024–2026 eGPU landscape, including Western
 * discrete GPUs (AMD, NVIDIA, Intel Arc) AND Chinese-vendor AI
 * accelerators (Moore Threads, Biren, Huawei Ascend, Cambricon,
 * Iluvatar, Tenstorrent) because Meshlit ships in markets where
 * those vendors are the default.
 *
 * Per BUILD_GUIDE §0 principle 1, we don't assume any vendor is
 * "best" — we record what we see and let the role-suggestion rules
 * (and the user override) decide how to use it.
 */
@Serializable
enum class EGpuKind(
    val tag: String,
    val displayName: String,
    /** Driver availability on Android — limits which llama.cpp backend can use this. */
    val androidDriver: EGpuDriverAvailability,
    /** Best backend for llama.cpp given this vendor. NONE means CPU-only for now. */
    val preferredBackend: EGpuBackend,
    /** Rough inference-fit ceiling when this eGPU is the primary accelerator. */
    val inferenceFit: InferenceFit,
) {
    // --- Western discrete GPUs (most common in 2024-2026 eGPU enclosures) ---
    DISCRETE_AMD_RADEON(
        "amd_radeon",
        "AMD Radeon (eGPU)",
        EGpuDriverAvailability.OPEN_SOURCE,
        EGpuBackend.VULKAN,
        InferenceFit.FRONTIER,
    ),
    DISCRETE_NVIDIA_GEFORCE(
        "nvidia_geforce",
        "NVIDIA GeForce (eGPU)",
        EGpuDriverAvailability.THROUGH_ZLUDA_OR_REMOTE,
        EGpuBackend.CUDA_OR_VULKAN,
        InferenceFit.FRONTIER,
    ),
    DISCRETE_INTEL_ARC(
        "intel_arc",
        "Intel Arc (eGPU)",
        EGpuDriverAvailability.OPEN_SOURCE,
        EGpuBackend.VULKAN,
        InferenceFit.MID_HIGH,
    ),
    DISCRETE_INTEL_DATA_CENTER(
        "intel_data_center_gpu",
        "Intel Data Center GPU (Max / Flex)",
        EGpuDriverAvailability.LINUX_ONLY,
        EGpuBackend.OPENCL_OR_SYCL,
        InferenceFit.FRONTIER,
    ),

    // --- Workstation / server-class ---
    NVIDIA_QUADRO_RTX(
        "nvidia_quadro",
        "NVIDIA RTX A-series / Quadro",
        EGpuDriverAvailability.THROUGH_ZLUDA_OR_REMOTE,
        EGpuBackend.CUDA_OR_VULKAN,
        InferenceFit.FRONTIER,
    ),
    NVIDIA_TESLA_DATA_CENTER(
        "nvidia_tesla",
        "NVIDIA Tesla / A100 / H100 (server)",
        EGpuDriverAvailability.LINUX_ONLY,
        EGpuBackend.CUDA,
        InferenceFit.FRONTIER,
    ),

    // --- Chinese-vendor AI accelerators (domestic in CN; rare but real) ---
    MOORE_THREADS_MTT(
        "moore_threads_mtt",
        "Moore Threads MTT (S80 / S100 / S2000 / Xmid)",
        EGpuDriverAvailability.LINUX_ONLY,
        EGpuBackend.MUSA_OR_OPENCL,
        InferenceFit.MID_HIGH,
    ),
    BIREN_BR(
        "biren_br",
        "Biren BR104",
        EGpuDriverAvailability.LINUX_ONLY,
        EGpuBackend.BIREN_SDK,
        InferenceFit.MID_HIGH,
    ),
    HUAWEI_ASCEND(
        "huawei_ascend",
        "Huawei Ascend (310 / 910)",
        EGpuDriverAvailability.LINUX_ONLY,
        EGpuBackend.CANN,
        InferenceFit.FRONTIER,
    ),
    CAMBRICON_MLU(
        "cambricon_mlu",
        "Cambricon MLU (270 / 290 / 370)",
        EGpuDriverAvailability.LINUX_ONLY,
        EGpuBackend.NEURON_RUNTIME,
        InferenceFit.MID_HIGH,
    ),
    ILUVATAR_COREX(
        "iluvatar_corex",
        "Iluvatar CoreX",
        EGpuDriverAvailability.LINUX_ONLY,
        EGpuBackend.TACC,
        InferenceFit.MID_HIGH,
    ),
    ENFLAME_TECHS(
        "enflame",
        "Enflame Cloudblazer",
        EGpuDriverAvailability.LINUX_ONLY,
        EGpuBackend.TOPS_RUNTIME,
        InferenceFit.MID_HIGH,
    ),
    METAX_C600(
        "metax",
        "Metax C500 / C600",
        EGpuDriverAvailability.LINUX_ONLY,
        EGpuBackend.METAX_SDK,
        InferenceFit.MID_HIGH,
    ),

    // --- Novel-architecture accelerators ---
    TENSTORRENT_WORMHOLE(
        "tenstorrent_wormhole",
        "Tenstorrent Wormhole (n150 / n300)",
        EGpuDriverAvailability.OPEN_SOURCE,
        EGpuBackend.TT_METAL,
        InferenceFit.FRONTIER,
    ),
    TENSTORRENT_GRAYSKULL(
        "tenstorrent_grayskull",
        "Tenstorrent Grayskull (e150)",
        EGpuDriverAvailability.OPEN_SOURCE,
        EGpuBackend.TT_METAL,
        InferenceFit.MID_HIGH,
    ),
    GROQ_LPU(
        "groq_lpu",
        "Groq LPU (LPUCard)",
        EGpuDriverAvailability.LINUX_ONLY,
        EGpuBackend.GROQ_API,
        InferenceFit.FRONTIER,
    ),

    // --- Catch-all ---
    UNKNOWN(
        "unknown",
        "Unknown external GPU",
        EGpuDriverAvailability.UNKNOWN,
        EGpuBackend.NONE,
        InferenceFit.UNKNOWN,
    );

    companion object {
        fun fromTag(tag: String): EGpuKind = entries.firstOrNull { it.tag == tag } ?: UNKNOWN
    }
}

@Serializable
enum class EGpuDriverAvailability(val tag: String, val displayName: String) {
    /** Working open driver on Android; llama.cpp can use it directly. */
    OPEN_SOURCE("open_source", "Open-source driver (works on Android)"),
    /** CUDA translation layer (ZLUDA) or remote GPU forwarding. */
    THROUGH_ZLUDA_OR_REMOTE("translated", "Translated / remote forwarding"),
    /** Driver exists for Linux only — record the hardware, inference falls back to CPU. */
    LINUX_ONLY("linux_only", "Linux-only driver (no Android support)"),
    /** We don't know yet — probe will return a clearer answer. */
    UNKNOWN("unknown", "Unknown driver status");

    companion object {
        fun fromTag(tag: String): EGpuDriverAvailability =
            entries.firstOrNull { it.tag == tag } ?: UNKNOWN
    }
}

@Serializable
enum class EGpuBackend(val tag: String, val displayName: String) {
    VULKAN("vulkan", "Vulkan"),
    CUDA("cuda", "CUDA"),
    CUDA_OR_VULKAN("cuda_or_vulkan", "CUDA or Vulkan (auto)"),
    OPENCL("opencl", "OpenCL"),
    OPENCL_OR_SYCL("opencl_or_sycl", "OpenCL / oneAPI SYCL"),
    HIP("hip", "AMD HIP / ROCm"),
    METAL("metal", "Apple Metal (laptop peer)"),
    CANN("cann", "Huawei CANN"),
    MUSA_OR_OPENCL("musa_or_opencl", "Moore Threads MUSA / OpenCL"),
    NEURON_RUNTIME("neuron", "Cambricon NeuWare"),
    TACC("tacc", "Iluvatar TACC"),
    TOPS_RUNTIME("tops", "Enflame TOPS"),
    METAX_SDK("metax", "Metax SDK"),
    BIREN_SDK("biren", "Biren DTK"),
    TT_METAL("tt_metal", "Tenstorrent TT-Metal"),
    GROQ_API("groq", "Groq Cloud API"),
    NONE("none", "No working backend (CPU fallback)");

    companion object {
        fun fromTag(tag: String): EGpuBackend = entries.firstOrNull { it.tag == tag } ?: NONE
    }
}

/**
 * Connection descriptor for a detected eGPU. The probe fills these
 * fields; downstream code (role-suggestion, inference backend
 * picker) consumes them.
 */
@Serializable
data class EGpuConnection(
    val kind: EGpuKind,
    val displayName: String,                       // e.g. "ASUS ROG XG Mobile (Radeon RX 6850M XT)"
    val transport: EGpuTransport,
    val vendorId: Int? = null,                     // USB VID, when applicable
    val productId: Int? = null,                    // USB PID
    val vramMb: Long? = null,                      // 8_192 for an RX 7600M XT
    val estimatedTflopsF16: Float? = null,         // for role-suggestion weighting
    val powerBudgetWatts: Int? = null,             // 150 W typical for an eGPU enclosure
    val pcieGeneration: Int? = null,               // 3 (TB3) or 4 (USB4 / TB4)
    val driverStatus: EGpuDriverAvailability = EGpuDriverAvailability.UNKNOWN,
    val preferredBackend: EGpuBackend = EGpuBackend.NONE,
    /** True if this eGPU is on a remote peer (desktop forwarding inference). */
    val isRemote: Boolean = false,
    /** When remote, the peer address to dispatch inference to. */
    val remoteAddress: String? = null,
    /** User-set override for which backend to use. Wins over [preferredBackend]. */
    val userBackendOverride: EGpuBackend? = null,
) {
    /** Resolved backend — user override if present, else probe preference. */
    val activeBackend: EGpuBackend get() = userBackendOverride ?: preferredBackend
}

/**
 * Transport between the phone and the eGPU. Most enclosures use
 * USB-C with TB3/TB4/USB4 tunnels, but a phone can also borrow a
 * desktop's GPU over the LAN/WAN tunnel (Tailscale/WG) and run
 * inference on the remote box while staying portable.
 */
@Serializable
enum class EGpuTransport(val tag: String, val displayName: String) {
    USB4("usb4", "USB4"),
    THUNDERBOLT_3("tb3", "Thunderbolt 3"),
    THUNDERBOLT_4("tb4", "Thunderbolt 4"),
    USB_3X("usb3", "USB 3.x (bandwidth-limited)"),
    OCULINK("oculink", "OCuLink"),
    M2_DOCK("m2_dock", "M.2 dock (laptop peer)"),
    WIFI_DISPLAY("wifi_display", "Wi-Fi Display (Miracast-class)"),
    TAILSCALE_TUNNEL("tailscale", "Tailscale / WG tunnel"),
    LAN("lan", "LAN"),
    OTHER("other", "Other");

    companion object {
        fun fromTag(tag: String): EGpuTransport =
            entries.firstOrNull { it.tag == tag } ?: OTHER
    }
}

/**
 * Online-curated eGPU database snapshot. Same shape as
 * [DeviceDatabaseSnapshot] but for enclosures — augmented by the
 * community on the chipset-DB repo. Lets new enclosures ship
 * without an app update.
 */
@Serializable
data class EGpuDatabaseSnapshot(
    val version: String,                           // semver
    val generatedAt: String,                       // ISO-8601
    val sourceUrl: String,
    val signatureSha256: String,
    val enclosures: List<EGpuDefinition>,
)

@Serializable
data class EGpuDefinition(
    /** Pattern matched against USB VID/PID + EGL renderer string + device tree. */
    val matchOn: EGpuMatchRules,
    val kind: EGpuKind,                            // EGpuKind.tag — resolved via fromTag()
    val displayName: String,
    val transport: EGpuTransport,
    val vramMb: Long,
    val estimatedTflopsF16: Float,
    val powerBudgetWatts: Int,
    val pcieGeneration: Int? = null,
    val driverStatus: EGpuDriverAvailability = EGpuDriverAvailability.UNKNOWN,
    val preferredBackend: EGpuBackend = EGpuBackend.NONE,
    val notes: String? = null,
)

@Serializable
data class EGpuMatchRules(
    /** Match by USB VID/PID — covers most TB3/USB4 enclosures. */
    val vendorId: Int? = null,
    val productId: Int? = null,
    /** Match by USB VID and any of these product names. */
    val vendorIdAndProductNameContains: Pair<Int, List<String>>? = null,
    /** Match by GL renderer string fragment (post-EGL probe). */
    val glRendererContains: List<String>? = null,
    /** Match by `/dev/dri/renderD128` vendor name (Linux only — server peer). */
    val driVendorContains: List<String>? = null,
)

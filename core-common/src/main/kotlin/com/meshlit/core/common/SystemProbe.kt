package com.meshlit.core.common

/**
 * Platform-agnostic system probe. The actual Android implementation
 * lives in `:app` (SystemProbeAndroid.kt) because it needs
 * `android.os.Build`, `ActivityManager`, `StatFs`, `/sys/...` reads.
 *
 * The probe is the *first* thing the app does at launch — its output
 * feeds the first-run setup flow, the role-suggestion rules, and the
 * model-fit picker.
 */
interface SystemProbe {
    suspend fun detect(): MeshlitResult<DetectedDeviceInfo>
}

/**
 * Infer the chipset family from `Build.HARDWARE` and `Build.SOC_MANUFACTURER`.
 * Real detection has to handle the OEMs that mask the SoC (Samsung's
 * "exynos" vs "exynos5" vs "universal" naming, Huawei's "hi" prefix,
 * etc.). This is the platform-agnostic part — pattern matching.
 *
 * Per the user's request: "list all mobile cpu" — the SocFamily enum
 * covers the realistic 2024-2026 Android device pool. Detection is
 * best-effort; the user can override from Settings → Device.
 */
fun inferSocFamily(
    hardware: String,
    socManufacturer: String?,
    model: String,
): SocFamily {
    val hw = hardware.lowercase()
    val sm = socManufacturer?.lowercase()
    val combined = "$hw|$sm|${model.lowercase()}"

    return when {
        // Qualcomm — model names start with SM (SM8550 = 8 Gen 2, etc.)
        sm == "qcom" || hw.startsWith("qcom") || hw.startsWith("msm") || hw.startsWith("sdm") -> {
            // Distinguish by SOC_MODEL: SM4450 = 4-series, SM6450 = 6-series,
            // SM7450 = 7-series, SM8550 = 8 Gen 2, SM8650 = 8 Gen 3.
            // The first two digits give the tier.
            val sm = model.replace("SM", "").replace("sm", "")
            when {
                sm.startsWith("4") -> SocFamily.SNAPDRAGON_4
                sm.startsWith("6") -> SocFamily.SNAPDRAGON_6
                sm.startsWith("7") || sm.startsWith("7") -> SocFamily.SNAPDRAGON_7
                sm.startsWith("8") -> SocFamily.SNAPDRAGON_8
                else -> SocFamily.SNAPDRAGON_OLDER
            }
        }

        // MediaTek Dimensity / Helio
        sm == "mediatek" || hw.startsWith("mt") || hw.startsWith("mt") -> {
            when {
                combined.contains("dimensity 9") || combined.contains("mt6989") || combined.contains("mt6991") -> SocFamily.DIMENSITY_9000
                combined.contains("dimensity 8") || combined.contains("mt6896") || combined.contains("mt6983") -> SocFamily.DIMENSITY_8000
                combined.contains("dimensity 7") || combined.contains("mt6895") || combined.contains("mt6853") -> SocFamily.DIMENSITY_7000
                combined.contains("dimensity 6") || combined.contains("mt6833") -> SocFamily.DIMENSITY_6000
                combined.contains("dimensity 1") || combined.contains("mt6889") -> SocFamily.DIMENSITY_1000
                combined.contains("helio g99") || combined.contains("mt6789") -> SocFamily.HELIO_G99
                combined.contains("helio") -> SocFamily.HELIO_OLDER
                else -> SocFamily.OTHER
            }
        }

        // Samsung Exynos
        sm == "samsung" || hw.startsWith("exynos") || combined.contains("exynos") -> {
            when {
                combined.contains("exynos 2400") || combined.contains("s5e9945") -> SocFamily.EXYNYS_2400
                combined.contains("exynos 2200") || combined.contains("s5e9925") -> SocFamily.EXYNOS_2200
                combined.contains("exynos 2100") || combined.contains("s5e9840") -> SocFamily.EXYNOS_2100
                combined.contains("exynos 14") || combined.contains("s5e8535") || combined.contains("s5e8845") -> SocFamily.EXYNOS_1400
                combined.contains("exynos") -> SocFamily.EXYNOS_OLDER
                else -> SocFamily.OTHER
            }
        }

        // Google Tensor
        sm == "google" || hw.startsWith("gs") || combined.contains("tensor") -> {
            when {
                combined.contains("tensor g5") || combined.contains("tensor 5") -> SocFamily.TENSOR_G5
                combined.contains("tensor g4") || combined.contains("tensor 4") -> SocFamily.TENSOR_G4
                combined.contains("tensor g3") || combined.contains("tensor 3") -> SocFamily.TENSOR_G3
                combined.contains("tensor g2") || combined.contains("tensor 2") -> SocFamily.TENSOR_G2
                combined.contains("tensor g1") || combined.contains("tensor") -> SocFamily.TENSOR_G1
                else -> SocFamily.OTHER
            }
        }

        // HiSilicon Kirin (Huawei)
        hw.startsWith("hi") || combined.contains("kirin") -> {
            when {
                combined.contains("kirin 9000") || combined.contains("kirin 9010") || combined.contains("kirin 9020") -> SocFamily.KIRIN_9000
                combined.contains("kirin 8000") || combined.contains("kirin 8100") || combined.contains("kirin 8200") -> SocFamily.KIRIN_8000
                combined.contains("kirin") -> SocFamily.KIRIN_OLDER
                else -> SocFamily.OTHER
            }
        }

        // Unisoc / Spreadtrum
        sm == "unisoc" || hw.startsWith("ums") || hw.startsWith("sp") || combined.contains("spreadtrum") -> {
            when {
                combined.contains("tiger") || combined.contains("t606") || combined.contains("t612") ||
                combined.contains("t616") || combined.contains("t618") || combined.contains("t700") ||
                combined.contains("t7510") || combined.contains("t8200") -> SocFamily.UNISOC_TIGER
                combined.contains("sc9863") -> SocFamily.UNISOC_OLDER
                else -> SocFamily.UNISOC_OLDER
            }
        }

        // x86
        hw.startsWith("intel") || hw.contains("x86") || combined.contains("intel") -> SocFamily.X86_INTEL
        hw.startsWith("amd") || combined.contains("amd") -> SocFamily.X86_AMD

        // RISC-V
        hw.startsWith("riscv") || combined.contains("riscv") || combined.contains("sg2000") -> SocFamily.RISCV

        else -> SocFamily.OTHER
    }
}

/**
 * Infer GPU family from the GPU renderer string returned by GLES20.glGetString.
 * Real implementation calls into OpenGL ES in the Android probe; this is
 * the pattern matcher.
 */
fun inferGpuFamily(glRenderer: String): GpuFamily {
    val r = glRenderer.lowercase()
    return when {
        "adreno" in r -> GpuFamily.ADRENO
        "mali" in r && "immortalis" in r -> GpuFamily.IMMORTALIS
        "mali" in r -> GpuFamily.MALI
        "powervr" in r -> GpuFamily.POWERVR
        "xclipse" in r -> GpuFamily.XCLIPSE
        "img" in r -> GpuFamily.IMG
        "nvidia" in r || "tegra" in r -> GpuFamily.NVIDIA
        else -> GpuFamily.UNKNOWN
    }
}

/**
 * Best-effort NPU presence check. The reliable Android API for this is
 * `getSystemService(NeuralNetworksService.class)` and querying
 * `getAvailableDevices()`. The probe wraps that and returns true if at
 * least one device is available.
 */
fun inferNpuPresence(socFamily: SocFamily): Pair<Boolean, String?> = when (socFamily) {
    SocFamily.SNAPDRAGON_8,
    SocFamily.SNAPDRAGON_7 -> true to "Qualcomm Hexagon"
    SocFamily.SNAPDRAGON_6,
    SocFamily.SNAPDRAGON_4 -> false to null
    SocFamily.DIMENSITY_9000,
    SocFamily.DIMENSITY_8000 -> true to "MediaTek APU"
    SocFamily.DIMENSITY_7000,
    SocFamily.DIMENSITY_6000,
    SocFamily.DIMENSITY_1000 -> false to null
    SocFamily.HELIO_G99,
    SocFamily.HELIO_OLDER -> false to null
    SocFamily.EXYNYS_2400,
    SocFamily.EXYNOS_2200 -> true to "Samsung NPU"
    SocFamily.EXYNOS_2100,
    SocFamily.EXYNOS_1400,
    SocFamily.EXYNOS_OLDER -> false to null
    SocFamily.TENSOR_G5,
    SocFamily.TENSOR_G4,
    SocFamily.TENSOR_G3,
    SocFamily.TENSOR_G2,
    SocFamily.TENSOR_G1 -> true to "Google Edge TPU"
    SocFamily.KIRIN_9000 -> true to "HiSilicon NPU"
    SocFamily.KIRIN_8000,
    SocFamily.KIRIN_OLDER -> false to null
    else -> false to null
}

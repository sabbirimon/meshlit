package com.meshlit.core.common

/**
 * Machine-readable capability matrix — what this device can actually run.
 *
 * The meshlit Devices screen renders this as a card so the user can see,
 * at a glance, whether the local node supports a given model format,
 * context length, quantization, or hardware backend. The same data
 * structure is also serialized onto the wire (`/v1/health.capability`)
 * so peers can decide whether to route a job here without an out-of-band
 * probe.
 *
 * Scope (Slice 1): static, derived from Build.* + system probes. It does
 * NOT yet capture dynamic state (current load, thermal throttle, FGS
 * budget remaining). Slice 5 extends this with a `CapabilitySnapshot`
 * that overlays runtime state on top of the static matrix.
 *
 * What it covers:
 *  - [androidApi]  — Build.VERSION.SDK_INT, mapped to [CapabilityTier].
 *  - [abis]        — supported native ABIs (arm64-v8a, x86_64, etc.).
 *  - [ramTier]     — coarse RAM bucket (LIGHT / MID / HIGH / FRONTIER).
 *  - [gpu]         — whether the device ships a usable GPU delegate.
 *  - [npu]         — whether a vendor NPU SDK is available.
 *  - [formats]     — model file formats the engine can ingest (GGUF, ONNX, …).
 *  - [contextLimit]— upper bound on context size supported by this engine.
 *  - [quants]      — quantizations the engine can decode (Q4_K_M, Q8_0, …).
 *
 * The matrix is intentionally **not** a sealed enum: it must grow as new
 * formats / ABIs / backends ship. Each field has a sensible default so
 * older callers stay compatible.
 */
data class CapabilityMatrix(
    val androidApi: Int,
    val abis: Set<Abi>,
    val ramTier: RamTier,
    val gpu: GpuBackendAvailability,
    val npu: NpuAvailability,
    val formats: Set<FileFormat>,
    val contextLimit: Int,
    val quants: Set<Quantization>,
) {

    /**
     * Coarse device tier derived from [CapabilityTier]. Used by the
     * router when picking between data-parallel peers.
     */
    val tier: CapabilityTier
        get() = CapabilityTier.fromSdkInt(androidApi)

    /** "I can run GGUF Q8 at context 4096" → true. */
    fun supports(
        format: FileFormat,
        quant: Quantization,
        contextSize: Int,
    ): Boolean =
        format in formats &&
            quant in quants &&
            contextSize <= contextLimit

    /** Human-readable rendering for the Devices screen card. */
    fun summary(): String = buildString {
        appendLine("API $androidApi · $tier")
        appendLine("ABI: ${abis.joinToString { it.tag }}")
        appendLine("RAM: $ramTier")
        appendLine("GPU: $gpu · NPU: $npu")
        appendLine("Formats: ${formats.joinToString { it.tag }}")
        appendLine("Quants: ${quants.joinToString { it.tag }}")
        append("Context ≤ $contextLimit tokens")
    }

    companion object {
        /**
         * A canonical "no capabilities" matrix for tests and for the
         * NoOpInferenceEngine fallback path. Distinct from a real
         * device's matrix so the Devices screen can render "device not
         * yet probed" honestly.
         */
        val NONE = CapabilityMatrix(
            androidApi = 0,
            abis = emptySet(),
            ramTier = RamTier.LIGHT,
            gpu = GpuBackendAvailability.NONE,
            npu = NpuAvailability.UNAVAILABLE,
            formats = emptySet(),
            contextLimit = 0,
            quants = emptySet(),
        )
    }
}

/** Supported ABI buckets. Mirrors the AGP / NDK canonical set. */
enum class Abi(val tag: String) {
    ARM64_V8A("arm64-v8a"),
    ARMEABI_V7A("armeabi-v7a"),
    X86("x86"),
    X86_64("x86_64");

    companion object {
        /** Best-effort parse from a `Build.SUPPORTED_ABIS` string. */
        fun fromBuildAbis(supportedAbis: Array<String>): Set<Abi> =
            supportedAbis.mapNotNull { tag ->
                entries.firstOrNull { it.tag == tag }
            }.toSet()
    }
}

/**
 * Coarse RAM bucket. The thresholds match the device-compatibility
 * tiers in `docs/DEVICE_COMPATIBILITY.md`. Don't tighten without
 * updating the doc.
 */
enum class RamTier(val displayName: String, val minTotalRamGb: Int) {
    LIGHT("Light", 2),
    MID("Mid", 4),
    HIGH("High", 6),
    FRONTIER("Frontier", 8);

    companion object {
        fun fromTotalRamGb(totalRamGb: Int): RamTier = when {
            totalRamGb >= FRONTIER.minTotalRamGb -> FRONTIER
            totalRamGb >= HIGH.minTotalRamGb -> HIGH
            totalRamGb >= MID.minTotalRamGb -> MID
            else -> LIGHT
        }
    }
}

/** Whether the device has a usable GPU delegate for llama.cpp / ORT. */
enum class GpuBackendAvailability(val tag: String) {
    NONE("none"),
    VULKAN("vulkan"),
    OPENCL("opencl");

    val isAvailable: Boolean get() = this != NONE
}

/** Whether a vendor NPU SDK is available (Pixel TPU, Snapdragon Hexagon, …). */
enum class NpuAvailability(val tag: String) {
    UNAVAILABLE("unavailable"),
    /** The probe ran but found no usable NPU delegate. */
    PROBED_NO_DRIVER("probed_no_driver"),
    AVAILABLE("available");
}

/** Wire-facing file formats. Mirrors `FileFormat` in `:core-inference`. */
enum class FileFormat(val tag: String) {
    GGUF("gguf"),
    ONNX("onnx"),
    SAFETENSORS("safetensors");

    companion object {
        /** Detect from a file path's extension. */
        fun fromPath(path: String): FileFormat? = when {
            path.endsWith(".gguf", ignoreCase = true) -> GGUF
            path.endsWith(".onnx", ignoreCase = true) -> ONNX
            path.endsWith(".safetensors", ignoreCase = true) -> SAFETENSORS
            else -> null
        }
    }
}

/** Quantization schemes the engine can decode. Wire-facing. */
enum class Quantization(val tag: String) {
    F32("F32"),
    F16("F16"),
    Q8_0("Q8_0"),
    Q4_K_M("Q4_K_M"),
    Q5_K_M("Q5_K_M"),
    Q6_K("Q6_K"),
    Q4_0("Q4_0"),
    Q5_0("Q5_0"),
    Q2_K("Q2_K"),
    Q3_K_S("Q3_K_S"),
    IQ4_XS("IQ4_XS");

    companion object {
        /** All standard GGUF quants. Used by the bundle path. */
        val STANDARD_GGUF = setOf(Q8_0, Q4_K_M, Q5_K_M, Q6_K)
    }
}

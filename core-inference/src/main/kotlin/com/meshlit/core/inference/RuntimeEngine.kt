package com.meshlit.core.inference

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger

/**
 * Phase 2 — multi-runtime engine abstraction.
 *
 * A `RuntimeEngine` is a (file-format, inference-backend) pair. Today's
 * production runtime is GGUF + llama.cpp; ONNX, SafeTensors, and TFLite
 * are tracked as Phase 2 candidates because each adds ~3–8 MB to the
 * APK for a native runtime + EP plugins.
 *
 * The registry lives at [RuntimeRegistry]; the coordinator picks a
 * runtime via [pickForFormat] when loading a model.
 *
 * Note that the existing [InferenceEngine] interface is the *narrow*
 * contract the coordinator already speaks (loadModel / unloadModel /
 * infer / engineTag). `RuntimeEngine` is the *wider* contract that
 * pairs an engine with its format and metadata. The two coexist —
 * the coordinator still takes a single `InferenceEngine` instance and
 * the runtime registry just decides which one to hand it.
 *
 * ## Why a wrapper and not a second `InferenceEngine` per format?
 *
 * The per-token streaming protocol is identical regardless of the
 * file format. Forcing every runtime to reimplement
 * `loadModel/unloadModel/infer` is duplication. The wrapper pattern
 * keeps the streaming contract in one place and lets each format
 * specialize only the model-load path (e.g. ONNX needs an EP plugin,
 * SafeTensors needs a weights directory listing).
 */
interface RuntimeEngine {
    /** Stable identifier — `gguf-llama.cpp`, `onnx-ort`, etc. */
    val runtimeId: String

    /** Human-readable name shown in the Models screen + status card. */
    val displayName: String

    /** File formats this runtime can load. Order matters: first match wins. */
    val supportedFormats: List<FileFormat>

    /** Approximate APK footprint in bytes for the native runtime + EP plugins. */
    val approxApkFootprintBytes: Long

    /** Status: shipped / candidate / apple-only / unavailable. */
    val status: RuntimeStatus

    /** Engine handle. `null` for candidate runtimes that haven't shipped. */
    val engine: InferenceEngine?

    /**
     * Whether this runtime can load the given file path based on its
     * extension. Cheap; does not read the file.
     */
    fun supports(path: String): Boolean = supportedFormats.any { it.matches(path) }
}

/** Status of a runtime in the bundled build. */
enum class RuntimeStatus(val tag: String, val displayName: String) {
    SHIPPED("shipped", "Bundled"),          // active in the production APK
    CANDIDATE("candidate", "Phase 2"),      // arch in place, runtime not yet linked
    APPLE_ONLY("apple_only", "Apple-only"), // MLX / Core ML — N/A on Android
    UNAVAILABLE("unavailable", "Unavailable"),
}

/** Open-weight file formats Meshlit can ingest. Sealed so the compiler
 *  can check exhaustiveness in `pickForFormat` switches. */
sealed class FileFormat(val extension: String, val displayName: String) {
    object Gguf : FileFormat("gguf", "GGUF")
    object Onnx : FileFormat("onnx", "ONNX")
    object Safetensors : FileFormat("safetensors", "SafeTensors")
    object Tflite : FileFormat("tflite", "TFLite")
    object Mlx : FileFormat("mlx", "MLX")
    object Coreml : FileFormat("coreml", "Core ML")

    /** Whether the file path's extension matches this format. */
    fun matches(path: String): Boolean =
        path.endsWith(".$extension", ignoreCase = true)

    companion object {
        /**
         * All known formats. Order matches `supports` preference.
         * Initialized lazily to avoid the Kotlin sealed-class
         * companion-init ordering trap where referencing nested
         * `object` subclasses from the companion's `val` initializer
         * can NPE on first call.
         */
        val all: List<FileFormat> by lazy {
            listOf(Gguf, Onnx, Safetensors, Tflite, Mlx, Coreml)
        }

        /** Detect format from file path. Returns `null` if no match. */
        fun detect(path: String): FileFormat? = all.firstOrNull { it.matches(path) }
    }
}

/**
 * Static catalog of every runtime Meshlit knows about. The list is
 * fixed at compile time so we can render it in the Models screen
 * without reflection.
 *
 * The only [RuntimeStatus.SHIPPED] entry today is the GGUF + llama.cpp
 * pair — every other row is a Phase 2 candidate so the user sees
 * what we're working toward without us having to commit a date.
 */
object RuntimeRegistry {

    private val log = logger("RuntimeRegistry")

    /** Backed by [LlamaCppInferenceEngine] once the .so is loaded. */
    fun ggufLlamaCpp(engine: InferenceEngine?): RuntimeEngine = object : RuntimeEngine {
        override val runtimeId = "gguf-llama.cpp"
        override val displayName = "GGUF · llama.cpp"
        override val supportedFormats = listOf(FileFormat.Gguf)
        override val approxApkFootprintBytes = 12L * 1024L * 1024L  // ~12 MB libmeshlit_inference.so
        override val status = RuntimeStatus.SHIPPED
        override val engine: InferenceEngine? = engine
    }

    /** ONNX Runtime Mobile + EP plugins. Phase 2.x: promoted to shipped. */
    fun onnxOrt(engine: InferenceEngine? = null): RuntimeEngine = object : RuntimeEngine {
        override val runtimeId = "onnx-ort"
        override val displayName = "ONNX · ORT Mobile"
        override val supportedFormats = listOf(FileFormat.Onnx)
        // Footprint: ~8 MB aar (libonnxruntime.so) + ~6 MB for the
        // NNAPI / XNNPACK execution-provider plugins that ship
        // separately. Both are bundled in the aar we depend on.
        override val approxApkFootprintBytes = 8L * 1024L * 1024L + 6L * 1024L * 1024L
        override val status = RuntimeStatus.SHIPPED
        override val engine: InferenceEngine? = engine
    }

    /** SafeTensors via candle (Rust core). Candidate. */
    fun safetensorsCandle(engine: InferenceEngine? = null): RuntimeEngine = object : RuntimeEngine {
        override val runtimeId = "safetensors-candle"
        override val displayName = "SafeTensors · candle"
        override val supportedFormats = listOf(FileFormat.Safetensors)
        override val approxApkFootprintBytes = 6L * 1024L * 1024L
        override val status = RuntimeStatus.CANDIDATE
        override val engine: InferenceEngine? = engine
    }

    /** TFLite via LiteRT. Candidate. */
    fun tfliteLitert(engine: InferenceEngine? = null): RuntimeEngine = object : RuntimeEngine {
        override val runtimeId = "tflite-litert"
        override val displayName = "TFLite · LiteRT"
        override val supportedFormats = listOf(FileFormat.Tflite)
        override val approxApkFootprintBytes = 3L * 1024L * 1024L
        override val status = RuntimeStatus.CANDIDATE
        override val engine: InferenceEngine? = engine
    }

    /** MLX-LM — Apple Silicon only. */
    fun mlxApple(engine: InferenceEngine? = null): RuntimeEngine = object : RuntimeEngine {
        override val runtimeId = "mlx-apple"
        override val displayName = "MLX · MLX-LM"
        override val supportedFormats = listOf(FileFormat.Mlx)
        override val approxApkFootprintBytes = 0L
        override val status = RuntimeStatus.APPLE_ONLY
        override val engine: InferenceEngine? = engine
    }

    /** Core ML via Apple BNNS. Apple-only. */
    fun coremlApple(engine: InferenceEngine? = null): RuntimeEngine = object : RuntimeEngine {
        override val runtimeId = "coreml-apple"
        override val displayName = "Core ML · BNNS"
        override val supportedFormats = listOf(FileFormat.Coreml)
        override val approxApkFootprintBytes = 0L
        override val status = RuntimeStatus.APPLE_ONLY
        override val engine: InferenceEngine? = engine
    }

    /** All known runtimes — includes candidate + Apple-only rows so the
     *  Models screen can render the full roadmap. */
    val all: List<RuntimeEngine> = listOf(
        ggufLlamaCpp(engine = null),
        // Phase 2.x — promote ONNX Runtime to SHIPPED. The engine
        // instance is resolved lazily via [resolveOrtEngine] so
        // callers can keep a stable descriptor identity across the
        // app lifetime even though the underlying OrtEnvironment is
        // a singleton created on first access.
        onnxOrt(engine = resolveOrtEngine()),
        safetensorsCandle(),
        tfliteLitert(),
        mlxApple(),
        coremlApple(),
    )

    /** All runtimes that are actually shippable on the current platform. */
    val shippable: List<RuntimeEngine> = all.filter { it.status == RuntimeStatus.SHIPPED }

    /**
     * Phase 2.x — singleton accessor for the ORT engine instance.
     * Created on first call so we don't pay the JNI initialization
     * cost until a non-GGUF model is actually requested.
     */
    fun resolveOrtEngine(): InferenceEngine? {
        // The first call to OnnxOrtInferenceEngine() doesn't init
        // ORT — the engine stays in `nativeReady=false` until
        // `loadNativeLibrary()` is invoked. The coordinator calls
        // that explicitly during [pickEngine]. We return `null`
        // here so the catalog row doesn't claim a live engine
        // pointer; the coordinator swaps in the real engine on
        // load.
        return null
    }

    /**
     * Pick the runtime that can load the file at [path]. Resolution:
     *  1. Detect [FileFormat] from the extension.
     *  2. Find the first shippable runtime advertising that format.
     *  3. Fall back to a candidate so we can surface a clear "not
     *     bundled yet" error instead of crashing.
     */
    fun pickForPath(path: String): RuntimeResolution {
        val format = FileFormat.detect(path)
            ?: return RuntimeResolution.UnknownFormat(path)
        return pickForFormat(format)
    }

    /**
     * Pick the runtime for an already-known [format]. Same resolution
     * rules as [pickForPath] but skips extension detection. Useful
     * when the caller already knows the format (e.g. catalog entries
     * carry the format directly).
     */
    fun pickForFormat(format: FileFormat): RuntimeResolution {
        val shippableMatch = shippable.firstOrNull { it.supportedFormats.contains(format) }
        if (shippableMatch != null) {
            return RuntimeResolution.Found(shippableMatch, format)
        }
        val candidateMatch = all.firstOrNull {
            it.status == RuntimeStatus.CANDIDATE && it.supportedFormats.contains(format)
        }
        return if (candidateMatch != null) {
            RuntimeResolution.NotShipped(candidateMatch, format)
        } else {
            RuntimeResolution.Unsupported(format)
        }
    }

    /** Render a runtime summary for the Models / Settings screens. */
    fun summary(): RuntimeSummary {
        val shipped = shippable.firstOrNull()
        val shippedBytes = shippable?.sumOf { it.approxApkFootprintBytes } ?: 0L
        val candidateBytes = all
            .filter { it.status == RuntimeStatus.CANDIDATE }
            .sumOf { it.approxApkFootprintBytes }
        return RuntimeSummary(
            shippedCount = shippable.size,
            candidateCount = all.count { it.status == RuntimeStatus.CANDIDATE },
            appleOnlyCount = all.count { it.status == RuntimeStatus.APPLE_ONLY },
            shippedBytes = shippedBytes,
            candidateBytes = candidateBytes,
        )
    }

    /**
     * Phase 2.x — version stamp for the registry. Bumped any time a
     * runtime is added, removed, or has its status changed. The
     * Models screen caches this and shows a "new runtime available"
     * banner when the persisted version on disk is older than the
     * build's compile-time version.
     *
     * The bump is intentionally a `val` (not auto-derivable) so a
     * developer can ship a "what changed" log entry alongside the
     * bump.
     */
    const val REGISTRY_VERSION: Int = 2
    const val REGISTRY_CHANGE_NOTE: String =
        "Phase 2.x: ONNX Runtime Mobile is now a shipped runtime. " +
            "Phi-3.5-mini, Mistral-7B, Gemma-2, and any other ONNX-distributed " +
            "model can now load on-device. The supported-formats card now lists " +
            "two bundled runtimes (GGUF and ONNX); the rest remain on the Phase 3 roadmap."
}

/** Result of resolving a runtime for a given model file path. */
sealed interface RuntimeResolution {
    val format: FileFormat?

    data class Found(val runtime: RuntimeEngine, override val format: FileFormat) : RuntimeResolution
    data class NotShipped(val runtime: RuntimeEngine, override val format: FileFormat) : RuntimeResolution {
        val message: String
            get() = "${format.displayName} models need the ${runtime.displayName} runtime, " +
                "which is not bundled yet (${runtime.status.displayName}, " +
                "≈${runtime.approxApkFootprintBytes / (1024 * 1024)} MB APK)."
    }
    data class Unsupported(override val format: FileFormat) : RuntimeResolution {
        val message: String get() = "${format.displayName} is not supported by any Meshlit runtime."
    }
    data class UnknownFormat(val path: String) : RuntimeResolution {
        override val format: FileFormat? = null
        val message: String get() = "Could not detect model format from path: $path"
    }
}

/** Aggregate counts/footprint for the supported-formats card. */
data class RuntimeSummary(
    val shippedCount: Int,
    val candidateCount: Int,
    val appleOnlyCount: Int,
    val shippedBytes: Long,
    val candidateBytes: Long,
)

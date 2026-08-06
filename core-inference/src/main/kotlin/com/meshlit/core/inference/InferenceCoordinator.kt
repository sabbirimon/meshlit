package com.meshlit.core.inference

import com.meshlit.core.common.EGpuConnection
import com.meshlit.core.common.HostOS
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Owns the singleton [InferenceEngine] instance for the lifetime of
 * the app. Built once at process start; lives until [shutdown].
 *
 * Engine selection:
 *  1. If `System.getProperty("meshlit.inference.stub") == "true"` →
 *     always [NoOpInferenceEngine] (used by tests that don't need a
 *     real model load).
 *  2. Else try [LlamaCppInferenceEngine.loadNativeLibrary].
 *     If it succeeds, that's our engine.
 *  3. On failure, log and fall back to [NoOpInferenceEngine] —
 *     the app must surface a typed error rather than silently
 *     synthesize a placeholder reply.
 *
 * Concurrency:
 *  - Only one inference runs at a time. Concurrent callers wait on
 *    [inferMutex]. This is the simplest correct policy; llama.cpp's
 *    contexts aren't thread-safe and parallel inference across
 *    multiple contexts requires explicit memory budgets (Phase 3).
 *  - The current job is tracked in [currentJob]. [cancel] cooperatively
 *    cancels it; the engine checks the coroutine's active state per
 *    token and aborts cleanly.
 *
 * State:
 *  - [engineTag] reflects the active engine ("runanywhere" /
 *    "llama.cpp" / "onnx-ort" / "none").
 *  - [state] is a [CoordinatorState] Flow that the UI subscribes to.
 *  - [events] is a SharedFlow of [InferenceEvent] for finer-grained
 *    updates (load started, model loaded, token, completion).
 */
class InferenceCoordinator(
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
) {

    private val log = logger("InferenceCoordinator")

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val llamaEngine = LlamaCppInferenceEngine()
    private val onnxEngine = OnnxOrtInferenceEngine()
    /**
     * Last-resort engine. Returns a typed `MeshlitError.Native(
     * "no_engine_for_format:...")` on every operation so a load
     * never silently produces a placeholder reply. Previously a
     * stub engine served this role and echoed a deterministic
     * "(stub) Got it — ..." string; the user explicitly asked for
     * the stub to be removed, so the fallback now fails loudly
     * with a typed error instead.
     */
    private val noOpEngine = NoOpInferenceEngine()

    /**
     * Phase 2.x — RunAnywhere-backed engine. The host calls
     * [RunAnywhereInferenceEngine.initialize] at app start (in
     * `MeshlitApplication.onCreate`); once initialised, this engine
     * takes priority over the placeholder llama.cpp engine for GGUF
     * loads because the SDK actually executes the model rather
     * than returning the JNI-not-implemented error path.
     */
    private val runAnywhereEngine = RunAnywhereInferenceEngine()

    /**
     * The active engine. Resolved once at startup but the latest
     * runtime registry is consulted on every load so we can react
     * when a candidate runtime is later shipped (Phase 2).
     */
    private val engine: InferenceEngine = pickEngine()

    /**
     * Last resolved runtime for the most recent load. `null` if nothing
     * has been loaded yet. Surfaced through [currentRuntime] so the UI
     * can render the runtime name in the status card.
     */
    @Volatile private var lastRuntime: RuntimeEngine? = null
    val currentRuntime: RuntimeEngine? get() = lastRuntime

    /** Last format we resolved for a load. Null when nothing has been loaded. */
    @Volatile private var lastFormat: FileFormat? = null
    val currentFormat: FileFormat? get() = lastFormat

    private val inferMutex = Mutex()

    @Volatile private var currentJob: Job? = null

    val engineTag: String get() = when {
        // Use [isInitialized] rather than [isReady] so the UI flips
        // to the real runtime the moment `MeshlitApplication.onCreate`
        // finishes SDK init, not after the first `loadModel` lands.
        // Otherwise the Jobs screen keeps showing the
        // "No engine active" banner even when the SDK is up.
        runAnywhereEngine.isInitialized() -> runAnywhereEngine.engineTag
        else -> engine.engineTag
    }

    private val _state = MutableStateFlow<CoordinatorState>(CoordinatorState.Idle)
    val state: StateFlow<CoordinatorState> = _state.asStateFlow()

    /**
     * Phase 2 — the shard refs this device is currently hosting.
     * Backed by the coordinator's `loadedShards` field; updated by
     * `loadModel(..., manifest = ...)` when a sharded load completes.
     *
     * Empty for whole-model loads. Each entry maps 1:1 onto a
     * `ShardRef` and is replicated on the wire via `/v1/model`
     * `shardRanges` so the planner can pick a replacement peer
     * without an out-of-band query.
     */
    private val _loadedShards = MutableStateFlow<List<com.meshlit.core.inference.net.ShardRef>>(emptyList())
    val loadedShards: StateFlow<List<com.meshlit.core.inference.net.ShardRef>> = _loadedShards.asStateFlow()

    private val _events = MutableSharedFlow<InferenceEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<InferenceEvent> = _events.asSharedFlow()

    private fun pickEngine(): InferenceEngine {
        val forceStub = System.getProperty("meshlit.inference.stub") == "true"
        if (forceStub) {
            log.info("coord.engine_pick", "forcing no-op engine (system property set)")
            lastRuntime = RuntimeRegistry.ggufLlamaCpp(noOpEngine)
            lastFormat = null
            return noOpEngine
        }
        // Phase 2.x — probe every shipped runtime, remember which
        // ones came up, then bind each load to the matching engine.
        // We *don't* keep a single active engine anymore: the
        // coordinator dispatches per-load based on the file path so
        // a `.gguf` shipped asset lands on llama.cpp (when present)
        // and an externally-imported `.onnx` lands on ORT — without
        // either crashing the other.
        val llamaOk = runCatching { llamaEngine.loadNativeLibrary() }.getOrDefault(false)
        val onnxOk = runCatching { onnxEngine.loadNativeLibrary() }.getOrDefault(false)
        // RunAnywhere is *not* probed here — its `initialize(context)`
        // call needs an Android `Context`, which the coordinator
        // doesn't have (it's a plain Kotlin object). The host calls
        // `MeshlitApplication.onCreate` → `RunAnywhereInferenceEngine.initialize`
        // before the coordinator picks up any work; `isInitialized()`
        // returns true once that's happened and `engineFor(.gguf)`
        // then routes GGUF loads here.
        //
        // We deliberately do NOT mark the engine as "available" yet
        // because the SDK hasn't been registered. It self-registers
        // at app start; if `isInitialized()` is still false at load
        // time we fall through to the placeholder llama.cpp engine,
        // which returns a typed `Native` error explaining the SDK
        // isn't up. That's the same failure mode users saw before —
        // except now it routes through RunAnywhere first, so the
        // moment `MeshlitApplication.onCreate` succeeds the load
        // lands on a real model with no coordinator changes.
        // Keep a compatibility pointer for `engineTag` and friends.
        // The actual load path goes through `engineFor(format)` below.
        val fallbackEngine = when {
            llamaOk -> {
                log.info("coord.engine_pick", "engine_for_gguf=llama.cpp")
                lastRuntime = RuntimeRegistry.ggufLlamaCpp(llamaEngine)
                lastFormat = FileFormat.Gguf
                llamaEngine as InferenceEngine
            }
            onnxOk -> {
                log.info("coord.engine_pick", "engine_for_onnx=onnx-ort (no llama.cpp)")
                lastRuntime = RuntimeRegistry.onnxOrt(onnxEngine)
                lastFormat = FileFormat.Onnx
                onnxEngine as InferenceEngine
            }
            else -> {
                log.info("coord.engine_pick", "no native lib available; using no-op engine")
                lastRuntime = RuntimeRegistry.ggufLlamaCpp(noOpEngine)
                lastFormat = null
                noOpEngine as InferenceEngine
            }
        }
        // Pre-warm: pickEngine previously returned the live engine
        // and lazy-loaded everything else; now we just remember
        // which engines came up so `engineFor(format)` can dispatch.
        llamaAvailable = llamaOk
        onnxAvailable = onnxOk
        return fallbackEngine
    }

    /** Available native bindings, set once by [pickEngine]. */
    @Volatile private var llamaAvailable: Boolean = false
    @Volatile private var onnxAvailable: Boolean = false

    /**
     * Resolve the runtime engine for a given file format and path.
     * Falls back through:
     *  1. RunAnywhere (Phase 2.x) if the path starts with the
     *     `runanywhere:` scheme, or if the format is GGUF and the
     *     SDK has been initialised at app start. The scheme prefix
     *     is the explicit "load by SDK id" path used when the user
     *     downloads a model from the RunAnywhere catalog and we
     *     don't have a local file path to hand the coordinator.
     *  2. llama.cpp if the format is GGUF and the placeholder .so
     *     is available.
     *  3. onnx-ort if the format is Onnx and that engine is available
     *  4. The NoOp engine for everything else (or when nothing
     *     native shipped). NoOp returns a typed failure so the UI
     *     surfaces a clear "no engine for this format" error rather
     *     than a fake reply.
     */
    private fun engineFor(modelPath: String): InferenceEngine {
        if (modelPath.startsWith(RUNANYWHERE_SCHEME)) {
            return runAnywhereEngine
        }
        val fmt = FileFormat.detect(modelPath)
        return when (fmt) {
            // GGUF priority: RunAnywhere wins whenever the SDK has
            // been initialised at app start (the host calls
            // `RunAnywhereInferenceEngine.initialize(context)` from
            // `MeshlitApplication.onCreate`). Tiebreak by
            // initialisation flag so the first load goes through
            // the SDK and `loadedModelId` gets set, which keeps
            // subsequent loads on the real engine.
            FileFormat.Gguf -> when {
                runAnywhereEngine.isInitialized() -> runAnywhereEngine
                llamaAvailable -> llamaEngine
                else -> noOpEngine
            }
            FileFormat.Onnx -> if (onnxAvailable) onnxEngine else noOpEngine
            // SafeTensors / TFLite / MLX / CoreML / unknown all
            // route to NoOp until a Phase 3 build ships an actual
            // implementation. NoOp returns a typed failure so the
            // UI surfaces a clear error rather than a fake reply.
            else -> noOpEngine
        }
    }

    /**
     * Phase 2.x — explicit accessor for the RunAnywhere-backed engine.
     * The Models screen uses this to drive `downloadModelById(...)`
     * directly, since the SDK's download flow isn't a normal
     * `loadModel` operation (it streams weights from the network
     * rather than reading a local file).
     */
    fun runAnywhereEngine(): RunAnywhereInferenceEngine = runAnywhereEngine

    /** Display name of the active runtime for the status card. */
    val runtimeDisplayName: String
        get() = when {
            runAnywhereEngine.isInitialized() -> "RunAnywhere llama.cpp"
            lastRuntime?.displayName != null -> lastRuntime?.displayName!!
            engine.engineTag == "none" -> "No engine available — open Advanced → Runtimes"
            else -> "Unknown runtime"
        }

    /**
     * Load a model. Wraps [InferenceEngine.loadModel] and updates
     * [state]. The caller passes a [BackendHints] derived from
     * the device profile (chipset, GPU, eGPU).
     *
     * Phase 2 extension:
     *  - [layerStart] / [layerEnd] restrict the load to a single
     *    shard's layer range. `layerEnd = Int.MAX_VALUE` means
     *    "all remaining layers" (the default for whole-model loads).
     *  - [manifest] carries KV cache + tokenizer metadata for the
     *    load. Engines that aren't shard-aware can ignore it.
     *  - The runtime registry is consulted on every load. If the
     *    file format isn't shippable yet (e.g. ONNX before Phase 2
     *    ships), we return a typed failure rather than silently
     *    falling back to the GGUF engine.
     */
    suspend fun loadModel(
        modelPath: String,
        contextSize: Int = 4096,
        gpuLayers: Int = 0,
        hints: BackendHints = BackendHints.CpuOnly,
        layerStart: Int = 0,
        layerEnd: Int = Int.MAX_VALUE,
        manifest: com.meshlit.core.inference.net.ShardManifest? = null,
    ): MeshlitResult<ModelInfo> = loadModelInternal(
        modelPath = modelPath,
        contextSize = contextSize,
        gpuLayers = gpuLayers,
        hints = hints,
        layerStart = layerStart,
        layerEnd = layerEnd,
        manifest = manifest,
    )

    /**
     * Acquire a model through the cluster-shard incubator. The
     * incubator decides between a bundled asset, a single-shard
     * distribution, a multi-shard distribution, and a whole-model
     * fallback — see `ClusterStorageIncubator`. Once it returns a
     * local contiguous file, this method delegates to [loadModel]
     * with `manifest = null`.
     *
     * The incubator must be installed via
     * [com.meshlit.core.inference.cluster.ClusterStorageIncubator.install]
     * before this is called. If not, throws with a startup-order hint.
     */
    suspend fun loadShardedModel(
        modelId: String,
        contextSize: Int = 4096,
        gpuLayers: Int = 0,
        hints: BackendHints = BackendHints.CpuOnly,
    ): MeshlitResult<ModelInfo> {
        val incubator = com.meshlit.core.inference.cluster.ClusterStorageIncubator.get()
        val file = try {
            incubator.acquireModel(modelId)
        } catch (t: Throwable) {
            _state.value = CoordinatorState.Error(t.message ?: "incubator acquire failed")
            _events.tryEmit(InferenceEvent.LoadFailed(t.message ?: "incubator acquire failed"))
            return MeshlitResult.Failure(
                com.meshlit.core.common.MeshlitError.Native(
                    "incubator.acquire:${t.message ?: t.javaClass.simpleName}",
                ),
            )
        }
        return loadModelInternal(
            modelPath = file.absolutePath,
            contextSize = contextSize,
            gpuLayers = gpuLayers,
            hints = hints,
            layerStart = 0,
            layerEnd = Int.MAX_VALUE,
            manifest = null,
        )
    }

    /** Shared load path used by both [loadModel] and [loadShardedModel].
     *  Kept private to prevent accidental public delegation chains. */
    private suspend fun loadModelInternal(
        modelPath: String,
        contextSize: Int,
        gpuLayers: Int,
        hints: BackendHints,
        layerStart: Int,
        layerEnd: Int,
        manifest: com.meshlit.core.inference.net.ShardManifest?,
    ): MeshlitResult<ModelInfo> {
        // Phase 2 — resolve the runtime for this file path. We do
        // this *before* flipping the state to Loading so a bad
        // extension results in a clean Error state instead of a
        // Loading state that never completes.
        val resolution = RuntimeRegistry.pickForPath(modelPath)
        when (resolution) {
            is RuntimeResolution.Found -> {
                lastRuntime = resolution.runtime
                lastFormat = resolution.format
            }
            is RuntimeResolution.NotShipped -> {
                lastFormat = resolution.format
                _state.value = CoordinatorState.Error(resolution.message)
                _events.tryEmit(InferenceEvent.LoadFailed(resolution.message))
                return MeshlitResult.Failure(
                    com.meshlit.core.common.MeshlitError.Invalid(resolution.message),
                )
            }
            is RuntimeResolution.Unsupported -> {
                lastFormat = resolution.format
                _state.value = CoordinatorState.Error(resolution.message)
                _events.tryEmit(InferenceEvent.LoadFailed(resolution.message))
                return MeshlitResult.Failure(
                    com.meshlit.core.common.MeshlitError.Invalid(resolution.message),
                )
            }
            is RuntimeResolution.UnknownFormat -> {
                _state.value = CoordinatorState.Error(resolution.message)
                _events.tryEmit(InferenceEvent.LoadFailed(resolution.message))
                return MeshlitResult.Failure(
                    com.meshlit.core.common.MeshlitError.Invalid(resolution.message),
                )
            }
        }
        _state.value = CoordinatorState.Loading(modelPath, lastRuntime, lastFormat)
        _events.tryEmit(InferenceEvent.LoadStarted(modelPath))
        // Wait for any in-flight chat generation to drain before
        // delegating to the engine. Without this barrier the native
        // runner races the new load and crashes mid-stream when the
        // user swaps models from the picker. The barrier is
        // installed by the chat activity on resume and cleared on
        // pause — see `LlmModelChangeInterlock`. If no barrier is
        // installed (e.g. CLI / test paths) this is a no-op.
        LlmModelChangeInterlock.awaitReadyForModelChange()
        val request = ModelLoadRequest(
            modelPath = modelPath,
            contextSize = contextSize,
            gpuLayers = gpuLayers,
            backendHints = hints,
            layerStart = layerStart,
            layerEnd = layerEnd,
            manifest = manifest,
        )
        // Pick the runtime engine for this file's format. If a
        // bundle shipped a GGUF but only the ORT engine came up
        // native-ready, the load lands on the NoOp engine instead
        // of crashing on the wrong engine. NoOp returns a typed
        // failure so the UI surfaces a clear reason.
        val targetEngine = engineFor(modelPath)
        val result = targetEngine.loadModel(request)
        _state.value = when (result) {
            is MeshlitResult.Success -> CoordinatorState.Ready(result.value, lastRuntime, lastFormat)
            is MeshlitResult.Failure -> CoordinatorState.Error(result.error.tag, lastRuntime, lastFormat)
        }
        _loadedShards.value = if (result is MeshlitResult.Success && manifest != null) {
            // The coordinator is hosting one slice of the manifest.
            // Surface it so the planner can route follow-on work here.
            val role = manifest.shards.firstOrNull {
                it.layerStart == layerStart && it.layerEnd == layerEnd
            }?.stageRole ?: com.meshlit.core.inference.net.StageRole.MiddleStage(0)
            listOf(
                com.meshlit.core.inference.net.ShardRef(
                    modelId = manifest.modelId,
                    layerStart = layerStart,
                    layerEnd = if (layerEnd == Int.MAX_VALUE) manifest.totalLayers else layerEnd,
                    stageRole = role,
                    sha256 = manifest.modelSha256,
                ),
            )
        } else emptyList()
        _events.tryEmit(
            when (result) {
                is MeshlitResult.Success -> InferenceEvent.LoadSucceeded(result.value)
                is MeshlitResult.Failure -> InferenceEvent.LoadFailed(result.error.tag)
            },
        )
        return result
    }

    suspend fun unloadModel() {
        // Unload every engine that might have a session open. Cheap
        // when nothing is loaded — each engine's unload is a no-op.
        // Phase 2.x — also unload the RunAnywhere-backed engine so
        // the next load doesn't reuse a stale descriptor.
        runAnywhereEngine.unloadModel()
        llamaEngine.unloadModel()
        onnxEngine.unloadModel()
        noOpEngine.unloadModel()
        _state.value = CoordinatorState.Idle
        _events.tryEmit(InferenceEvent.Unloaded)
    }

    fun loadedModel(): ModelInfo? {
        // The user-facing "what's currently loaded" question is
        // answered by whichever engine actually has a model. We
        // check in priority order: RunAnywhere wins when it has a
        // model loaded (so the status card reflects the real
        // on-device model rather than a stale descriptor).
        runAnywhereEngine.loadedModel()?.let { return it }
        llamaEngine.loadedModel()?.let { return it }
        onnxEngine.loadedModel()?.let { return it }
        return noOpEngine.loadedModel()
    }

    /**
     * Dispatch an inference. Serialized — concurrent callers wait on
     * the mutex. Returns the result when generation finishes; tokens
     * stream via [InferenceRequest.onToken].
     */
    suspend fun infer(request: InferenceRequest): MeshlitResult<InferenceResult> =
        inferMutex.withLock {
            withContext(dispatcher) {
                // Pick the engine that currently holds the loaded
                // model. Falls through to NoOp if no native engine
                // has a session — that gives us a typed
                // `no_engine_for_infer` failure instead of a
                // misleading success.
                val targetEngine = pickEngineForInfer(request)
                if (!targetEngine.isReady()) {
                    return@withContext MeshlitResult.Failure(
                        com.meshlit.core.common.MeshlitError.Invalid("coord.inference.not_loaded"),
                    )
                }
                _state.value = CoordinatorState.Generating(
                    startedAtMs = System.currentTimeMillis(),
                    runtime = lastRuntime,
                    format = lastFormat,
                )
                _events.tryEmit(InferenceEvent.GenerationStarted(request.prompt))
                // Run inference directly on the caller's coroutine so
                // [cancel] (which cancels the FGS-bound job) propagates
                // through `coroutineContext.ensureActive()` in the
                // engine's per-token loop. We used to wrap this in
                // `scope.launch` *and* call engine.infer inline, which
                // fired every onToken callback twice — visible to the
                // user as duplicated agent replies.
                currentJob = coroutineContext[Job]
                try {
                    val result = targetEngine.infer(request)
                    _events.tryEmit(InferenceEvent.GenerationFinished(result))
                    if (targetEngine.isReady()) {
                        _state.value = CoordinatorState.Ready(
                            targetEngine.loadedModel()!!,
                            runtime = lastRuntime,
                            format = lastFormat,
                        )
                    }
                    result
                } finally {
                    currentJob = null
                }
            }
        }

    /** Pick the engine that should run an inference, preferring the one
     *  that currently has a session loaded. Falls through to NoOp so
     *  the user gets a typed failure instead of a phantom reply when
     *  no engine is ready.
     *
     *  Phase 2.x — RunAnywhere takes priority when it has a model
     *  loaded because the placeholder llama.cpp engine never actually
     *  serves tokens (its JNI surface is declared but unimplemented).
     *  Without this priority, `infer()` would route to llama.cpp and
     *  return a typed `Native` error after the user successfully
     *  downloaded and loaded a model via RunAnywhere.
     */
    private fun pickEngineForInfer(request: InferenceRequest): InferenceEngine {
        if (runAnywhereEngine.isReady()) return runAnywhereEngine
        if (llamaEngine.isReady()) return llamaEngine
        if (onnxEngine.isReady()) return onnxEngine
        return noOpEngine
    }

    /** Cancel the current inference. No-op when nothing is running. */
    fun cancel() {
        currentJob?.cancel()
        currentJob = null
    }

    /** Build [BackendHints] from a device profile + eGPU detection. */
    fun buildHints(
        cpuThreads: Int,
        gpuLayers: Int,
        gpuBackend: GpuBackend,
        egpu: EGpuConnection? = null,
        hostOS: HostOS = HostOS.ANDROID,
    ): BackendHints = BackendHints(
        cpuThreads = cpuThreads,
        gpuLayers = gpuLayers,
        gpuBackend = gpuBackend,
        egpu = egpu,
        hostOS = hostOS,
    )

    fun shutdown() {
        cancel()
        scope.launch {
            llamaEngine.unloadModel()
            onnxEngine.unloadModel()
            noOpEngine.unloadModel()
        }
    }
}

/** Coarse state of the coordinator — what the UI binds to. */
sealed interface CoordinatorState {
    data object Idle : CoordinatorState
    /**
     * The runtime + format are surfaced here so the status card
     * can render "Loading GGUF · llama.cpp" without a separate
     * lookup. Both fields are nullable because the coordinator may
     * not have resolved a runtime yet (e.g. before any load).
     */
    data class Loading(val modelPath: String, val runtime: RuntimeEngine? = null, val format: FileFormat? = null) : CoordinatorState
    data class Ready(val model: ModelInfo, val runtime: RuntimeEngine? = null, val format: FileFormat? = null) : CoordinatorState
    data class Generating(val startedAtMs: Long, val runtime: RuntimeEngine? = null, val format: FileFormat? = null) : CoordinatorState
    data class Error(val message: String, val runtime: RuntimeEngine? = null, val format: FileFormat? = null) : CoordinatorState
}

/** Finer-grained events for UI log / debug overlay. */
sealed interface InferenceEvent {
    data class LoadStarted(val modelPath: String) : InferenceEvent
    data class LoadSucceeded(val model: ModelInfo) : InferenceEvent
    data class LoadFailed(val reason: String) : InferenceEvent
    data object Unloaded : InferenceEvent
    data class GenerationStarted(val prompt: String) : InferenceEvent
    data class GenerationFinished(val result: MeshlitResult<InferenceResult>) : InferenceEvent
}

/**
 * Path prefix that signals to [InferenceCoordinator] "this is a
 * RunAnywhere SDK model id, dispatch to [RunAnywhereInferenceEngine]
 * regardless of file extension". The model id follows the prefix,
 * e.g. `runanywhere:smollm2-360m-instruct-q8_0`.
 *
 * Used by the Jobs screen when the user picks "Download starter
 * model" — the SDK owns the file path on disk and we don't want to
 * leak its filesystem layout back into the coordinator's
 * `FileFormat.detect(...)` logic.
 */
const val RUNANYWHERE_SCHEME = "runanywhere:"
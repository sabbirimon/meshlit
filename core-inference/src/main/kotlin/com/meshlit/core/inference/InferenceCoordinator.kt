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

/**
 * Owns the singleton [InferenceEngine] instance for the lifetime of
 * the app. Built once at process start; lives until [shutdown].
 *
 * Engine selection:
 *  1. If `System.getProperty("meshlit.inference.stub") == "true"` →
 *     always [JvmStubInferenceEngine] (used by tests, CI, emulators).
 *  2. Else try [LlamaCppInferenceEngine.loadNativeLibrary].
 *     If it succeeds, that's our engine.
 *  3. On failure, log and fall back to [JvmStubInferenceEngine] —
 *     the app must remain functional even when the .so is missing.
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
 *  - [engineTag] reflects the active engine ("stub" / "llama.cpp").
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
    private val stubEngine = JvmStubInferenceEngine()

    private val engine: InferenceEngine = pickEngine()

    private val inferMutex = Mutex()

    @Volatile private var currentJob: Job? = null

    val engineTag: String get() = engine.engineTag

    private val _state = MutableStateFlow<CoordinatorState>(CoordinatorState.Idle)
    val state: StateFlow<CoordinatorState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<InferenceEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<InferenceEvent> = _events.asSharedFlow()

    private fun pickEngine(): InferenceEngine {
        val forceStub = System.getProperty("meshlit.inference.stub") == "true"
        if (forceStub) {
            log.info("coord.engine_pick", "forcing stub engine (system property set)")
            return stubEngine
        }
        val loaded = llamaEngine.loadNativeLibrary()
        return if (loaded) {
            log.info("coord.engine_pick", "using llama.cpp engine")
            llamaEngine
        } else {
            log.info("coord.engine_pick", "falling back to stub engine (no native lib)")
            stubEngine
        }
    }

    /**
     * Load a model. Wraps [InferenceEngine.loadModel] and updates
     * [state]. The caller passes a [BackendHints] derived from
     * the device profile (chipset, GPU, eGPU).
     */
    suspend fun loadModel(
        modelPath: String,
        contextSize: Int = 4096,
        gpuLayers: Int = 0,
        hints: BackendHints = BackendHints.CpuOnly,
    ): MeshlitResult<ModelInfo> {
        _state.value = CoordinatorState.Loading(modelPath)
        _events.tryEmit(InferenceEvent.LoadStarted(modelPath))
        val request = ModelLoadRequest(
            modelPath = modelPath,
            contextSize = contextSize,
            gpuLayers = gpuLayers,
            backendHints = hints,
        )
        val result = engine.loadModel(request)
        _state.value = when (result) {
            is MeshlitResult.Success -> CoordinatorState.Ready(result.value)
            is MeshlitResult.Failure -> CoordinatorState.Error(result.error.tag)
        }
        _events.tryEmit(
            when (result) {
                is MeshlitResult.Success -> InferenceEvent.LoadSucceeded(result.value)
                is MeshlitResult.Failure -> InferenceEvent.LoadFailed(result.error.tag)
            },
        )
        return result
    }

    suspend fun unloadModel() {
        engine.unloadModel()
        _state.value = CoordinatorState.Idle
        _events.tryEmit(InferenceEvent.Unloaded)
    }

    fun loadedModel(): ModelInfo? = engine.loadedModel()

    /**
     * Dispatch an inference. Serialized — concurrent callers wait on
     * the mutex. Returns the result when generation finishes; tokens
     * stream via [InferenceRequest.onToken].
     */
    suspend fun infer(request: InferenceRequest): MeshlitResult<InferenceResult> =
        inferMutex.withLock {
            withContext(dispatcher) {
                if (!engine.isReady()) {
                    return@withContext MeshlitResult.Failure(
                        com.meshlit.core.common.MeshlitError.Invalid("coord.inference.not_loaded"),
                    )
                }
                _state.value = CoordinatorState.Generating(
                    startedAtMs = System.currentTimeMillis(),
                )
                _events.tryEmit(InferenceEvent.GenerationStarted(request.prompt))
                val job = scope.launch {
                    val result = engine.infer(request)
                    _events.tryEmit(InferenceEvent.GenerationFinished(result))
                    _state.value = CoordinatorState.Ready(engine.loadedModel()!!)
                }
                currentJob = job
                try {
                    val result = engine.infer(request)
                    result
                } finally {
                    currentJob = null
                }
            }
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
        scope.launch { engine.unloadModel() }
    }
}

/** Coarse state of the coordinator — what the UI binds to. */
sealed interface CoordinatorState {
    data object Idle : CoordinatorState
    data class Loading(val modelPath: String) : CoordinatorState
    data class Ready(val model: ModelInfo) : CoordinatorState
    data class Generating(val startedAtMs: Long) : CoordinatorState
    data class Error(val message: String) : CoordinatorState
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
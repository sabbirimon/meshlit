package com.meshlit.core.inference

import android.content.Context
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.runanywhere.sdk.foundation.bridge.extensions.CppBridgeModelPaths
import com.runanywhere.sdk.foundation.bridge.extensions.CppBridgeModelRegistry
import com.runanywhere.sdk.llm.llamacpp.LlamaCPP
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.deleteModel
import com.runanywhere.sdk.public.extensions.downloadModelStream
import com.runanywhere.sdk.public.extensions.generateStream
import com.runanywhere.sdk.public.extensions.loadModel
import com.runanywhere.sdk.public.extensions.unloadModel
import com.meshlit.core.inference.models.ModelFiles
import com.runanywhere.sdk.public.extensions.registerModel
import com.runanywhere.sdk.public.types.RAModelInfo
import com.runanywhere.sdk.public.types.RAModelLoadRequest
import ai.runanywhere.proto.v1.DownloadProgress
import ai.runanywhere.proto.v1.InferenceFramework
import ai.runanywhere.proto.v1.LLMStreamEvent
import ai.runanywhere.proto.v1.ModelCategory
import ai.runanywhere.proto.v1.SDKEnvironment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Phase 2.x — RunAnywhere SDK-backed inference engine.
 *
 * Wraps the upstream `com.runanywhere.sdk.public.RunAnywhere` entry
 * point behind the [InferenceEngine] contract that the rest of the
 * app already speaks. The Jobs / Agent / Terminal screens talk to
 * [InferenceCoordinator] exactly as they always have; this engine is
 * swapped into the coordinator's runtime registry so the user gets
 * real on-device LLM generation rather than the typed
 * no_engine_for_format failure returned by [NoOpInferenceEngine].
 *
 * Why a new engine and not extending the existing ones:
 *
 *  - `LlamaCppInferenceEngine` declares JNI `external fun` symbols
 *    on a `libmeshlit_inference.so` we have not built yet. Swapping
 *    that .so for the RunAnywhere-shipped `libllama.so` would
 *    require maintaining our own JNI surface — a long-term cost for
 *    no user-visible gain.
 *  - `NoOpInferenceEngine` is the last-resort fallback used when no
 *    shipped runtime came up native-ready; it surfaces typed
 *    `no_engine_for_format` failures instead of synthetic replies.
 *  - `OnnxOrtInferenceEngine` is the second shipped runtime; it
 *    handles `.onnx` only. The RunAnywhere integration handles
 *    `.gguf` via llama.cpp — different format, different runtime,
 *    deserves its own engine class.
 *
 * Lifecycle:
 *
 *  1. The host calls [initialize] with the Application context once
 *     at process start. This calls `LlamaCPP.register()` (the
 *     upstream extension point for the llama.cpp backend) and then
 *     `RunAnywhere.initialize(context, DEV)`. Both are idempotent
 *     — repeated calls are no-ops.
 *  2. `loadModel(...)` calls the SDK's `loadModel(RAModelLoadRequest)`.
 *     The model id is recovered from the local file's name (we
 *     treat the model's basename without the extension as the
 *     canonical SDK id) so a file picked on disk maps onto the
 *     same id the host's Models screen uses for downloads.
 *  3. `infer(...)` runs `generateStream(...)` and emits tokens via
 *     the `onToken` callback so the UI's progressive text reveal
 *     keeps working without changes.
 *  4. `unloadModel()` is a best-effort — RunAnywhere has no public
 *     unload API yet, so we clear our local handle and let the
 *     process state hand back to the SDK.
 *
 * Threading: every blocking SDK call is dispatched onto
 * [Dispatchers.IO] inside a `withContext`. The Jobs screen fires
 * `infer()` from the main scope, so without the dispatch the
 * generation loop would block the UI on its first sample step.
 *
 * Failure modes (mapped to [MeshlitResult.Failure]):
 *
 *  - SDK not initialised  → `MeshlitError.Native("runanywhere.not_initialised")`
 *  - Model file missing    → `MeshlitError.Invalid("runanywhere.model_missing:<path>")`
 *  - SDK load threw        → `MeshlitError.Native("runanywhere.load_failed:<msg>", cause)`
 *  - SDK generate threw    → `MeshlitError.Native("runanywhere.generate_failed:<msg>", cause)`
 *  - Caller-cancelled      → `FinishReason.CANCELLED` in the success result;
 *                            the [MeshlitResult.Failure] path isn't used because
 *                            cancellation is a normal outcome, not a fault.
 */
class RunAnywhereInferenceEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    /**
     * Default model id used when [loadModel] is called with a
     * `.gguf` path but the basename doesn't match a model the
     * upstream catalog knows about. Set to the smallest canonical
     * model so tests / unknown assets still produce *some* answer
     * rather than a hard failure.
     */
    private val defaultModelId: String = DEFAULT_MODEL_ID,
    /**
     * SDK environment flag passed to `RunAnywhere.initialize`.
     * DEVELOPMENT selects the SDK's development environment; it is not a
     * verified telemetry opt-out. A real run attempted the development telemetry
     * endpoint (DNS failed). See docs/remaining-engine-bugs.md before changing
     * environments or advertising zero-network behavior.
     */
    private val environment: SDKEnvironment = SDKEnvironment.SDK_ENVIRONMENT_DEVELOPMENT,
) : InferenceEngine {

    override val engineTag: String = "runanywhere"

    private val log = logger("RunAnywhereInferenceEngine")

    @Volatile private var initialized: Boolean = false
    @Volatile private var modelInfo: ModelInfo? = null
    @Volatile private var loadedModelId: String? = null
    // Single-flight init lock. The SDK's own `LlamaCPP.register()` is
    // idempotent but acquiring its internal mutex twice in parallel is
    // wasteful; this serialises the whole init sequence on a single
    // coroutine mutex so callers can launch from any context.
    private val initMutex = Mutex()

    /**
     * Hook called once at app start, before the first
     * `loadModel`. Registers the llama.cpp backend and hands the
     * SDK an `Application` context so it can read storage /
     * permissions / ABI.
     *
     * Suspend: the SDK's `LlamaCPP.register()` is itself a
     * suspending call. Callers must launch this on a coroutine
     * scope (e.g. the application-scoped `CoroutineScope`). Safe
     * to call multiple times — the [initMutex] serialises and the
     * early `initialized` check makes subsequent calls a no-op.
     */
    suspend fun initialize(context: Context) {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            try {
                // `LlamaCPP.register()` is a suspend call in the
                // 0.20.x line (it grabs a Mutex and waits for the
                // JNI bindings to load). Caller is expected to
                // dispatch on IO if the JNI load is heavy.
                LlamaCPP.register()
                RunAnywhere.initialize(context = context.applicationContext, environment = environment)
                initialized = true
                log.info(
                    "runanywhere.initialized",
                    "RunAnywhere SDK initialized",
                    mapOf("env" to environment.name),
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                // Don't claim initialisation succeeded if either
                // call threw — leave `initialized` at false so the
                // coordinator's `engineFor(.gguf)` falls through to
                // the next available engine (likely NoOp).
                log.warn(
                    "runanywhere.init_failed",
                    "RunAnywhere init failed",
                    mapOf("error" to (t.message ?: t.javaClass.simpleName)),
                )
            }
        }
    }

    /** Whether the SDK is registered and ready to accept `loadModel`. */
    override fun isReady(): Boolean = initialized && loadedModelId != null

    /**
     * Whether [initialize] has been called successfully. This is
     * distinct from [isReady] (which additionally requires a model
     * to be loaded). The coordinator's dispatch uses this so a fresh
     * `loadModel(...)` call can route to the SDK even when no model
     * has been loaded yet — using [isReady] here was the root cause
     * of the stub-reply bug (the very first load skipped the real
     * engine and landed on the placeholder stub).
     */
    fun isInitialized(): Boolean = initialized

    /** Currently loaded model, or null when nothing is loaded. */
    override fun loadedModel(): ModelInfo? = modelInfo

    override suspend fun loadModel(request: ModelLoadRequest): MeshlitResult<ModelInfo> =
        withContext(dispatcher) {
            if (!initialized) {
                return@withContext MeshlitResult.Failure(
                    com.meshlit.core.common.MeshlitError.Native(
                        "runanywhere.not_initialised: call RunAnywhereInferenceEngine.initialize(context) at app start",
                    ),
                )
            }
            val path = request.modelPath
            if (path.isBlank()) {
                return@withContext MeshlitResult.Failure(
                    com.meshlit.core.common.MeshlitError.Invalid("runanywhere.empty_path"),
                )
            }
            // The coordinator dispatches to this engine either when
            // the file extension is `.gguf` or when the path is
            // prefixed with `runanywhere:`. The prefix form is the
            // "load a known SDK id" path used by the Jobs screen's
            // "Download starter model" flow, where the SDK owns the
            // on-disk path and we don't want to leak its layout.
            val (modelId, syntheticDisplayName, syntheticSizeBytes) = if (
                path.startsWith(RUNANYWHERE_SCHEME)
            ) {
                val id = path.removePrefix(RUNANYWHERE_SCHEME).ifBlank { defaultModelId }
                Triple(id, id, -1L)
            } else {
                val file = File(path)
                if (!file.exists()) {
                    return@withContext MeshlitResult.Failure(
                        com.meshlit.core.common.MeshlitError.Invalid(
                            "runanywhere.model_missing:$path",
                        ),
                    )
                }
                // The SDK accepts a `RAModelInfo` keyed by id. For files
                // on disk we use the basename (sans extension) as the id.
                // For known bundled models the host's Models screen uses
                // the SDK's canonical id (`smollm2-360m-instruct-q8_0`,
                // etc.) directly, so the two code paths land on the same
                // catalog row.
                if (request.layerStart != 0 || request.layerEnd != Int.MAX_VALUE || request.manifest != null) {
                    return@withContext MeshlitResult.Failure(com.meshlit.core.common.MeshlitError.Invalid(
                        "runanywhere.pipeline_unsupported: use the native RPC layer backend"))
                }
                val prepared = try {
                    ModelFiles.validateGguf(file)
                    ("local-" + ModelFiles.sha256(file)) to file
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Exception) {
                    return@withContext MeshlitResult.Failure(com.meshlit.core.common.MeshlitError.Invalid(
                        "runanywhere.model_prepare:${error.message}", error))
                }
                val resolvedId = prepared.first
                // Register the installed artifact, not a download-only id. The registry
                // and lifecycle must agree on availability and the actual native path.
                CppBridgeModelRegistry.save(RAModelInfo(
                    id = resolvedId, name = file.nameWithoutExtension,
                    local_path = prepared.second.absolutePath,
                    format = ai.runanywhere.proto.v1.ModelFormat.MODEL_FORMAT_GGUF,
                    category = ModelCategory.MODEL_CATEGORY_LANGUAGE,
                    framework = InferenceFramework.INFERENCE_FRAMEWORK_LLAMA_CPP,
                    is_downloaded = true, is_available = true,
                    download_size_bytes = file.length(), context_length = request.contextSize,
                ))
                Triple(resolvedId, file.name, file.length())
            }

            return@withContext try {
                val result = RunAnywhere.loadModel(
                    request = RAModelLoadRequest(
                        model_id = modelId,
                        category = ModelCategory.MODEL_CATEGORY_LANGUAGE,
                        framework = InferenceFramework.INFERENCE_FRAMEWORK_LLAMA_CPP,
                    ),
                )
                if (!result.success) {
                    modelInfo = null
                    loadedModelId = null
                    log.warn(
                        "runanywhere.load_failed",
                        "RunAnywhere load failed",
                        mapOf(
                            "id" to modelId,
                            "error" to (result.error_message ?: "unknown"),
                        ),
                    )
                    return@withContext MeshlitResult.Failure(
                        com.meshlit.core.common.MeshlitError.Native(
                            "runanywhere.load_failed:${result.error_message ?: "unknown"}",
                        ),
                    )
                }
                // `RAModelLoadResult` carries `model_id` and
                // `resolved_path` but no human name. Use the model's
                // display name from the file basename (or the SDK id
                // for the synthetic `runanywhere:<id>` path) so the
                // Jobs screen's "Ready · 1000M params · GGUF" header
                // shows something identifiable.
                val displayName = syntheticDisplayName
                val resolvedPath = result.resolved_path.takeIf { it.isNotBlank() } ?: path
                val info = ModelInfo(
                    modelPath = resolvedPath,
                    modelName = displayName,
                    contextSize = 0, // Pinned SDK does not expose its effective native context.
                    parameterCount = 0L, // SDK doesn't surface this in the load result
                    quantization = guessQuantization(displayName),
                    embeddingDim = 0,
                    sizeBytes = if (syntheticSizeBytes > 0) syntheticSizeBytes else 0L,
                    loadedAtMs = System.currentTimeMillis(),
                )
                modelInfo = info
                loadedModelId = modelId
                log.info(
                    "runanywhere.loaded",
                    "RunAnywhere model loaded",
                    mapOf(
                        "id" to modelId,
                        "name" to displayName,
                        "sizeBytes" to info.sizeBytes,
                    ),
                )
                MeshlitResult.Success(info)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                modelInfo = null
                loadedModelId = null
                log.warn(
                    "runanywhere.load_failed",
                    "RunAnywhere load failed",
                    mapOf(
                        "id" to modelId,
                        "error" to (t.message ?: t.javaClass.simpleName),
                    ),
                )
                MeshlitResult.Failure(
                    com.meshlit.core.common.MeshlitError.Native(
                        "runanywhere.load_failed:${t.message ?: t.javaClass.simpleName}",
                        t,
                    ),
                )
            }
        }

    override suspend fun unloadModel() {
        withContext(dispatcher) {
            if (initialized && loadedModelId != null) {
                val result = RunAnywhere.unloadModel(ai.runanywhere.proto.v1.ModelUnloadRequest(
                    model_id = loadedModelId.orEmpty(), category = ModelCategory.MODEL_CATEGORY_LANGUAGE,
                    framework = InferenceFramework.INFERENCE_FRAMEWORK_LLAMA_CPP,
                ))
                check(result.success) { "Inference backend failed to unload the model" }
            }
        }
        modelInfo = null
        loadedModelId = null
        log.info("runanywhere.unloaded", "RunAnywhere engine cleared locally")
    }

    override suspend fun infer(request: InferenceRequest): MeshlitResult<InferenceResult> =
        withContext(dispatcher) {
            val info = modelInfo
                ?: return@withContext MeshlitResult.Failure(
                    com.meshlit.core.common.MeshlitError.Invalid("runanywhere.not_loaded"),
                )
            if(request.reuseContext) return@withContext MeshlitResult.Failure(com.meshlit.core.common.MeshlitError.Invalid("runanywhere.explicit_cache_reuse_unavailable"))
            val started = System.currentTimeMillis()
            val accounting=SdkStreamAccounting()
            try {
                val events: Flow<LLMStreamEvent> = RunAnywhere.generateStream(
                    prompt = request.prompt,
                    options = ai.runanywhere.proto.v1.LLMGenerationOptions(
                        max_tokens = request.maxTokens, temperature = request.temperature,
                        top_p = request.topP, top_k = request.topK,
                        repetition_penalty = request.repeatPenalty, stop_sequences = request.stopSequences,
                        seed = request.seed, streaming_enabled = true,
                    ),
                )
                events.collect { event ->
                    coroutineContext.ensureActive()
                    accounting.accept(event)?.let{request.onToken(it)}
                }
            } catch (t: kotlinx.coroutines.CancellationException) {
                // Re-throw so the calling coroutine observes the cancel.
                throw t
            } catch (t: Throwable) {
                log.warn(
                    "runanywhere.generate_failed",
                    "RunAnywhere generate failed",
                    mapOf("error" to (t.message ?: t.javaClass.simpleName)),
                )
                return@withContext MeshlitResult.Failure(
                    com.meshlit.core.common.MeshlitError.Native(
                        "runanywhere.generate_failed:${t.message ?: t.javaClass.simpleName}",
                        t,
                    ),
                )
            }
            val durationMs = System.currentTimeMillis() - started
            val result=try {accounting.finish(durationMs)} catch(e:IllegalStateException){
                return@withContext MeshlitResult.Failure(com.meshlit.core.common.MeshlitError.Native("runanywhere.incomplete_stream",e))
            }
            request.onComplete(result)
            log.info(
                "runanywhere.infer.done",
                "RunAnywhere generation complete",
                mapOf(
                    "model" to info.modelName,
                    "tokens" to result.generatedTokens,
                    "durationMs" to durationMs,
                    "reason" to result.finishReason.tag,
                ),
            )
            MeshlitResult.Success(result)
        }

    /**
     * Public hook for the Models screen: download a model by id and
     * stream progress. Returns a `Flow<DownloadProgressView>` so the
     * UI can render a progress bar without coupling to the SDK's
     * concrete type.
     *
     * Two things have to happen before the SDK can plan a download:
     *
     *  1. The model id has to be **registered** with a URL, framework,
     *     and memory hint via `RunAnywhere.registerModel(...)`. The
     *     SDK's `resolveModelForDownload(...)` walks the registry
     *     looking for `download_url`; without that field populated
     *     the planner aborts with "Unable to create a download plan".
     *  2. The SDK's `downloadModelStream(...)` is then called with a
     *     `RAModelInfo(id = modelId)` — the id is the only required
     *     field because step 1 already attached the URL.
     *
     * The flow is collected on [dispatcher] because the SDK does
     * network IO on the producer side.
     *
     * @param url direct HTTPS URL the SDK should fetch from. Defaults
     *   to the entry's catalog URL via [setCatalogDownloadUrl] when
     *   the host calls [downloadModelById] with an entry that already
     *   carries a URL.
     * @param displayName human-readable name shown in the SDK's
     *   `Registered models` list. Defaults to `modelId` when the
     *   caller doesn't supply one.
     * @param memoryRequirementBytes upper-bound memory hint that the
     *   SDK uses for compatibility preflight. Defaults to 0 so the
     *   preflight doesn't gate the download when the caller doesn't
     *   know the size up front.
     */
    fun downloadModelById(
        modelId: String,
        url: String = currentCatalogUrl(modelId),
        displayName: String = modelId,
        memoryRequirementBytes: Long = 0L,
    ): Flow<DownloadProgressView> = flow {
        // 1) Register (or re-register) the model so the SDK's
        //    planner has a URL to plan against. Re-register is
        //    idempotent: a model with the same id just has its
        //    metadata refreshed. The `LLAMA_CPP` framework matches
        //    every entry in the curated catalog — STT / TTS / VLM
        //    use `ONNX` and aren't routed through this engine.
        if (url.isNotBlank()) {
            runCatching {
                RunAnywhere.registerModel(
                    id = modelId,
                    name = displayName,
                    url = url,
                    framework = InferenceFramework.INFERENCE_FRAMEWORK_LLAMA_CPP,
                    modality = ModelCategory.MODEL_CATEGORY_LANGUAGE,
                    memoryRequirement = memoryRequirementBytes.takeIf { it > 0 },
                )
                log.info(
                    "runanywhere.register",
                    "model registered for download",
                    mapOf("id" to modelId),
                )
            }.onFailure { t ->
                log.warn(
                    "runanywhere.register.fail",
                    "${t.message}",
                    mapOf("id" to modelId),
                )
                throw t
            }
        } else {
            log.warn(
                "runanywhere.register.skip",
                "no URL for model; SDK planner will likely reject the download",
                mapOf("id" to modelId),
            )
        }
        // 2) Drive the SDK's download flow. The id is the only
        //    required field on `RAModelInfo` because step 1 already
        //    registered the URL with the registry.
        val sdkFlow: Flow<DownloadProgress> = RunAnywhere.downloadModelStream(
            model = RAModelInfo(id = modelId),
        )
        sdkFlow.collect { progress ->
            emit(adaptDownloadProgress(progress))
        }
    }.flowOn(dispatcher)

    /**
     * Catalog-side URL cache, populated by [setCatalogDownloadUrl].
     * The Models screen pushes the canonical URL into this map before
     * launching [downloadModelById] so the SDK registration step has
     * a URL even when the caller doesn't pass one explicitly.
     */
    private val catalogUrlById: MutableMap<String, String> = java.util.concurrent.ConcurrentHashMap()

    /**
     * Record the canonical URL for a given catalog id. Called by
     * the Models screen once it has resolved an entry from
     * [com.meshlit.inference.RunAnywhereCatalog]. The URL survives
     * across coroutines so a re-download (e.g. after delete) hits
     * the same artifact.
     */
    fun setCatalogDownloadUrl(modelId: String, url: String) {
        if (url.isNotBlank()) catalogUrlById[modelId] = url
    }

    /**
     * Drop the on-disk copy of a model previously downloaded via
     * the SDK. Mirrors the upstream `RunAnywhere.deleteModel(id)`
     * contract used by `ModelSelectionViewModel.delete(...)` —
     * paired with the bundled-model interlock so the user can
     * safely swap a model mid-session.
     *
     * Returns `MeshlitResult.Success(Unit)` when the SDK reports
     * the deletion succeeded, or a typed `MeshlitError.Native` if
     * the SDK is uninitialized / throws. The Files API clears the
     * disk and the in-memory registry entry but does not touch the
     * currently-loaded session — callers must call
     * [com.meshlit.core.inference.InferenceCoordinator.unloadModel]
     * themselves first.
     */
    suspend fun deleteDownloadedModel(modelId: String): MeshlitResult<Unit> {
        if (!isInitialized()) {
            return MeshlitResult.Failure(
                com.meshlit.core.common.MeshlitError.Native(
                    "runanywhere.delete.not_initialized",
                ),
            )
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                RunAnywhere.deleteModel(modelId)
                Unit
            }.fold(
                onSuccess = { MeshlitResult.Success(Unit) },
                onFailure = { t ->
                    MeshlitResult.Failure(
                        com.meshlit.core.common.MeshlitError.Native(
                            "runanywhere.delete.failed:${t.message ?: t.javaClass.simpleName}",
                            t,
                        ),
                    )
                },
            )
        }
    }

    /** Resolve the SDK registry's actual artifact after its transfer finishes. */
    fun downloadedModelFile(modelId:String):File {
        val model=CppBridgeModelRegistry.get(modelId) ?: error("RunAnywhere did not register a downloaded model")
        val path=model.local_path.takeIf{it.isNotBlank()} ?: error("RunAnywhere did not report a local model path")
        return File(path).also { require(it.isFile) { "RunAnywhere artifact is missing" } }
    }

    private fun currentCatalogUrl(modelId: String): String =
        catalogUrlById[modelId].orEmpty()

    /**
     * Best-effort extraction of token text from the SDK's stream
     * event object. The SDK's `LLMStreamEvent` carries the token
     * text on `getToken()` plus an `event_kind` discriminator
     * (`LLM_STREAM_EVENT_KIND_TOKEN` for incremental tokens) and
     * an `is_final` flag for the terminal event — direct typed
     * access via the proto class.
     */

    /** Adapter from SDK progress → host view-model type. */
    private fun adaptDownloadProgress(progress: DownloadProgress): DownloadProgressView {
        return DownloadProgressView(
            modelId = progress.model_id.orEmpty(),
            progress = progress.overall_progress.coerceIn(0f, 1f),
            bytesDownloaded = progress.bytes_downloaded,
            totalBytes = progress.total_bytes,
            state = progress.state.name,
            error = progress.error_message.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Best-effort quantization from the model filename. GGUF
     * filenames typically include the quant tag (e.g.
     * `llama-3.1-8b-instruct.Q4_K_M.gguf`).
     */
    private fun guessQuantization(name: String): String {
        val regex = Regex("(?i)(Q\\d+_K_(?:S|M|L)|Q\\d+_0|F16|F32|BF16)")
        return regex.find(name)?.value ?: "unknown"
    }

    companion object {
        /**
         * Default model id used when callers want to download a
         * known-good starter model. The SDK's catalog ships this
         * exact id (it's the one in the upstream README), so we
         * surface it as the default rather than inventing one.
         */
        const val DEFAULT_MODEL_ID = "smollm2-360m-instruct-q8_0"
    }
}

/**
 * Host-facing view of the SDK's download progress — decouples the
 * UI from the SDK's proto type so the same Compose screen keeps
 * working when we swap the SDK or upgrade to a newer release.
 */
data class DownloadProgressView(
    val modelId: String,
    val progress: Float,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val state: String,
    val error: String?,
)

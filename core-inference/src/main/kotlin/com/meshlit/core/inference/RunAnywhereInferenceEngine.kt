package com.meshlit.core.inference

import android.content.Context
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.runanywhere.sdk.llm.llamacpp.LlamaCPP
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.downloadModelStream
import com.runanywhere.sdk.public.extensions.generateStream
import com.runanywhere.sdk.public.extensions.loadModel
import com.runanywhere.sdk.public.extensions.registerModel
import com.runanywhere.sdk.public.types.RAModelInfo
import com.runanywhere.sdk.public.types.RAModelLoadRequest
import ai.runanywhere.proto.v1.DownloadProgress
import ai.runanywhere.proto.v1.InferenceFramework
import ai.runanywhere.proto.v1.LLMStreamEvent
import ai.runanywhere.proto.v1.LLMStreamEventKind
import ai.runanywhere.proto.v1.ModelCategory
import ai.runanywhere.proto.v1.SDKEnvironment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
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
     * `DEVELOPMENT` keeps telemetry off in the dev APK; production
     * builds should switch this to `PRODUCTION` once the SDK's
     * telemetry contract is signed off.
     */
    private val environment: SDKEnvironment = SDKEnvironment.SDK_ENVIRONMENT_DEVELOPMENT,
) : InferenceEngine {

    override val engineTag: String = "runanywhere"

    private val log = logger("RunAnywhereInferenceEngine")

    @Volatile private var initialized: Boolean = false
    @Volatile private var modelInfo: ModelInfo? = null
    @Volatile private var loadedModelId: String? = null

    /**
     * Hook called once at app start, before the first
     * `loadModel`. Registers the llama.cpp backend and hands the
     * SDK an `Application` context so it can read storage /
     * permissions / ABI.
     *
     * Safe to call multiple times — the SDK's idempotent guard on
     * `LlamaCPP.register()` means the second call is a no-op.
     */
    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                // `LlamaCPP.register()` is a suspend call in the
                // 0.20.x line (it grabs a Mutex and waits for the
                // JNI bindings to load). Block briefly here — the
                // host calls `initialize` from the Application's
                // `onCreate`, where we have no surrounding coroutine
                // and want the SDK ready before the first activity
                // asks for it.
                runBlocking { LlamaCPP.register() }
                RunAnywhere.initialize(context = context.applicationContext, environment = environment)
                initialized = true
                log.info(
                    "runanywhere.initialized",
                    "RunAnywhere SDK initialized",
                    mapOf("env" to environment.name),
                )
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
                Triple(
                    file.nameWithoutExtension.ifBlank { defaultModelId },
                    file.name,
                    file.length(),
                )
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
                val displayName = result.model_id.takeIf { it.isNotBlank() } ?: syntheticDisplayName
                val resolvedPath = result.resolved_path.takeIf { it.isNotBlank() } ?: path
                val info = ModelInfo(
                    modelPath = resolvedPath,
                    modelName = displayName,
                    contextSize = request.contextSize,
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
            } catch (t: Throwable) {
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
        // The upstream SDK has no public unload() yet — the model
        // handle is dropped when the SDK's loader is re-invoked or
        // the process exits. We clear our local state so the
        // coordinator's `pickEngineForInfer` falls through to
        // another engine and the UI doesn't keep claiming a loaded
        // model that no longer answers.
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
            val started = System.currentTimeMillis()
            val accumulator = StringBuilder()
            var stopReason = FinishReason.NATURAL_STOP
            var tokensEmitted = 0
            try {
                // The SDK emits one flow event per token with a
                // terminal event whose `is_final == true`. We map
                // each non-terminal event into a single-token chunk
                // for the existing `onToken` callback contract used
                // by `NoOpInferenceEngine`'s fallback path and
                // `OnnxOrtInferenceEngine`'s downstream consumers.
                val events: Flow<LLMStreamEvent> = RunAnywhere.generateStream(
                    prompt = request.prompt,
                    options = null,
                )
                events.collect { event ->
                    if (event.event_kind == LLMStreamEventKind.LLM_STREAM_EVENT_KIND_TOKEN &&
                        event.token.isNotEmpty()
                    ) {
                        coroutineContext.ensureActive()
                        val text = event.token
                        accumulator.append(text)
                        tokensEmitted += 1
                        request.onToken(text)
                        if (request.stopSequences.isNotEmpty() &&
                            accumulator.toString().contains(request.stopSequences.first())
                        ) {
                            stopReason = FinishReason.STOP_SEQUENCE
                            throw StopIterationSentinel()
                        }
                        if (tokensEmitted >= request.maxTokens) {
                            stopReason = FinishReason.MAX_TOKENS
                            throw StopIterationSentinel()
                        }
                    } else if (event.is_final) {
                        // Natural end-of-stream.
                        return@collect
                    }
                }
            } catch (sentinel: StopIterationSentinel) {
                // Co-operative early-exit. `stopReason` was set before
                // we threw. Swallow and fall through to result build.
            } catch (t: kotlinx.coroutines.CancellationException) {
                stopReason = FinishReason.CANCELLED
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
            val tps = if (durationMs > 0) tokensEmitted * 1000f / durationMs else 0f
            val result = InferenceResult(
                promptTokens = 0,
                generatedTokens = tokensEmitted,
                totalDurationMs = durationMs,
                tokensPerSecond = tps,
                finishReason = stopReason,
                finalText = accumulator.toString(),
            )
            request.onComplete(result)
            log.info(
                "runanywhere.infer.done",
                "RunAnywhere generation complete",
                mapOf(
                    "model" to info.modelName,
                    "tokens" to tokensEmitted,
                    "durationMs" to durationMs,
                    "reason" to stopReason.tag,
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
                    mapOf("id" to modelId, "url" to url),
                )
            }.onFailure { t ->
                log.warn(
                    "runanywhere.register.fail",
                    "${t.message}",
                    mapOf("id" to modelId, "url" to url),
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

/** Internal sentinel — local-only control flow, not the SDK's
 *  `CancellationException`. Must extend `Throwable` so we can
 *  `throw` it inside a `flow.collect` block and unwind to the
 *  outer `try { … } catch (sentinel: StopIterationSentinel)`. */
private class StopIterationSentinel : Throwable()

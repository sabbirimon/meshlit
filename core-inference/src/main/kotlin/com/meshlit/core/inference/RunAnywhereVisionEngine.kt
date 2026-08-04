package com.meshlit.core.inference

import com.meshlit.core.common.logger
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.processImageStream
import com.runanywhere.sdk.public.types.RAVLMImage
import ai.runanywhere.proto.v1.VLMGenerationOptions
import ai.runanywhere.proto.v1.VLMImage
import ai.runanywhere.proto.v1.VLMImageFormat
import ai.runanywhere.proto.v1.VLMModelFamily
import ai.runanywhere.proto.v1.VLMResult
import ai.runanywhere.proto.v1.VLMStreamEvent
import ai.runanywhere.proto.v1.VLMStreamEventKind
import ai.runanywhere.proto.v1.VLMChatTemplate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okio.Buffer

/**
 * Phase 2.x — wraps the RunAnywhere SDK's vision-language (VLM)
 * surface. Used by the Vision screen to run a prompt against an
 * image picked from the device.
 *
 * **Backend reality**: The SDK 0.20.12 declares `RAVLMImage` and
 * `processImageStream` on the public `RunAnywhere` surface, but the
 * underlying AAR that implements the VLM inference (CoreML /
 * MediaPipe / llama.cpp vision) is **not** on the current
 * classpath. The SDK throws `NoClassDefFoundError` or `LinkageError`
 * the first time the VLM call is dispatched. Rather than wrapping
 * a non-working call, we catch the failure mode and surface a
 * structured [VisionError.BackendMissing] so the Vision screen can
 * render a friendly "VLM backend not yet shipped" card.
 *
 * Why we still write the wired path:
 *
 *  - Flipping the native backend on later is a one-line change in
 *    `core-inference/build.gradle.kts` (add `runanywhere-vlm` when
 *    it's released). The screen code, the API shape, and the calls
 *    are already correct and don't need to be touched.
 *  - The proto types are on the SDK jar today, so the call site
 *    compiles cleanly even though the runtime would fail.
 *  - Tests / unit-level verification can use this engine to ensure
 *    the screens render the right state when the backend is missing.
 *
 * Threading:
 *
 *  - The flow is `flowOn(dispatcher)` (default IO). The SDK's image
 *    encoding happens on the producer side.
 *  - Image bytes are passed as `okio.ByteString` (the `encoded`
 *    field on [VLMImage]) so the SDK can dispatch to whichever
 *    framework backend unboxes them internally.
 */
class RunAnywhereVisionEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val log = logger("RunAnywhereVisionEngine")

    /** Host-typed view of a VLM stream. The UI maps each kind onto
     *  a Compose Card; the terminal [Done] replaces the running
     *  partial with the final text and timing metadata. */
    sealed interface VisionStreamView {
        /** The SDK has acknowledged the image and started processing. */
        data class Started(val requestId: String) : VisionStreamView
        /** Image was encoded; tokens are about to land. */
        data object ImageEncoded : VisionStreamView
        /** Streaming a single token. */
        data class Token(val text: String) : VisionStreamView
        /** Terminal success. */
        data class Done(
            val text: String,
            val promptTokens: Int,
            val completionTokens: Int,
            val timeToFirstTokenMs: Long,
            val totalDurationMs: Long,
        ) : VisionStreamView
        /** Terminal error. */
        data class Failed(val message: String) : VisionStreamView
        /** The VLM native backend isn't on the classpath. The UI
         *  surfaces this as a friendly "not yet shipped" card. */
        data object BackendMissing : VisionStreamView
    }

    /**
     * Run a VLM prompt against a JPEG/PNG image. The prompt is a
     * natural-language question (e.g. "What's in this image?") — the
     * model emits tokens until either [VLMGenerationOptions.max_tokens]
     * is exhausted or the model emits a finish reason.
     *
     * @param imageBytes raw JPEG/PNG bytes (e.g. from
     *   `ActivityResultContracts.PickVisualMedia`).
     * @param format image format. Defaults to JPEG — the most common
     *   output from the photo picker.
     * @param prompt text prompt to send with the image.
     * @param options generation parameters. Defaults favour low
     *   temperature for deterministic image captioning.
     */
    fun processImage(
        imageBytes: ByteArray,
        prompt: String,
        format: VLMImageFormat = VLMImageFormat.VLM_IMAGE_FORMAT_JPEG,
        options: VLMGenerationOptions = defaultOptions(),
    ): Flow<VisionStreamView> = flow {
        val vlmImage: RAVLMImage = VLMImage(
            file_path = null,
            encoded = Buffer().write(imageBytes).readByteString(),
            raw_rgb = null,
            base64 = null,
            width = 0,
            height = 0,
            format = format,
            media_type = null,
            name = null,
            size_bytes = imageBytes.size.toLong(),
            metadata = emptyMap(),
        )
        emitStream(vlmImage, prompt, options)
    }.flowOn(dispatcher)

    /**
     * Run a VLM prompt against an image on disk. Same as
     * [processImage] but the SDK reads the file directly — preferred
     * for large images that would otherwise inflate the IPC payload.
     */
    fun processImageFile(
        filePath: String,
        prompt: String,
        options: VLMGenerationOptions = defaultOptions(),
    ): Flow<VisionStreamView> = flow {
        val vlmImage: RAVLMImage = VLMImage(
            file_path = filePath,
            encoded = null,
            raw_rgb = null,
            base64 = null,
            width = 0,
            height = 0,
            format = VLMImageFormat.VLM_IMAGE_FORMAT_UNSPECIFIED,
            media_type = null,
            name = null,
            size_bytes = 0L,
            metadata = emptyMap(),
        )
        emitStream(vlmImage, prompt, options)
    }.flowOn(dispatcher)

    /** Shared emission loop — catches the missing-backend failure
     *  mode and forwards every other event into the host view. */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<VisionStreamView>.emitStream(
        vlmImage: RAVLMImage,
        prompt: String,
        options: VLMGenerationOptions,
    ) {
        val sdkFlow: Flow<VLMStreamEvent> = try {
            RunAnywhere.processImageStream(
                vlmImage,
                prompt,
                options,
            )
        } catch (e: NoClassDefFoundError) {
            log.warn(
                "runanywhere.vision.backend_missing",
                "VLM native backend not on classpath",
                mapOf("class" to (e.message ?: "unknown")),
            )
            emit(VisionStreamView.BackendMissing)
            return
        } catch (e: LinkageError) {
            log.warn(
                "runanywhere.vision.backend_missing",
                "VLM native backend link error",
                mapOf("error" to (e.message ?: "unknown")),
            )
            emit(VisionStreamView.BackendMissing)
            return
        } catch (e: UnsupportedOperationException) {
            // Some SDK builds throw this when VLM isn't loaded.
            log.warn(
                "runanywhere.vision.unsupported",
                "VLM call not supported in this SDK build",
                mapOf("error" to (e.message ?: "unknown")),
            )
            emit(VisionStreamView.BackendMissing)
            return
        }
        try {
            sdkFlow.collect { event ->
                when (event.kind) {
                    VLMStreamEventKind.VLM_STREAM_EVENT_KIND_STARTED -> {
                        val id = event.request_id
                        if (!id.isNullOrEmpty()) emit(VisionStreamView.Started(id))
                    }
                    VLMStreamEventKind.VLM_STREAM_EVENT_KIND_IMAGE_ENCODED ->
                        emit(VisionStreamView.ImageEncoded)
                    VLMStreamEventKind.VLM_STREAM_EVENT_KIND_TOKEN -> {
                        val tok = event.token
                        if (!tok.isNullOrEmpty()) emit(VisionStreamView.Token(tok))
                    }
                    VLMStreamEventKind.VLM_STREAM_EVENT_KIND_COMPLETED -> {
                        val r: VLMResult? = event.result
                        emit(
                            VisionStreamView.Done(
                                text = r?.text.orEmpty(),
                                promptTokens = r?.prompt_tokens ?: 0,
                                completionTokens = r?.completion_tokens ?: 0,
                                timeToFirstTokenMs = r?.time_to_first_token_ms ?: 0L,
                                totalDurationMs = r?.processing_time_ms ?: 0L,
                            ),
                        )
                        return@collect
                    }
                    VLMStreamEventKind.VLM_STREAM_EVENT_KIND_ERROR -> {
                        val msg = event.error_message
                        emit(VisionStreamView.Failed(msg ?: "unknown"))
                        return@collect
                    }
                    else -> { /* UNSPECIFIED — ignore */ }
                }
            }
        } catch (e: NoClassDefFoundError) {
            // Some SDK builds defer the linkage check to the first
            // collect. If that's here, surface the same fallback.
            log.warn(
                "runanywhere.vision.backend_missing",
                "VLM native backend not on classpath (mid-stream)",
                mapOf("class" to (e.message ?: "unknown")),
            )
            emit(VisionStreamView.BackendMissing)
        } catch (e: LinkageError) {
            log.warn(
                "runanywhere.vision.backend_missing",
                "VLM native backend link error (mid-stream)",
                mapOf("error" to (e.message ?: "unknown")),
            )
            emit(VisionStreamView.BackendMissing)
        } catch (t: Throwable) {
            log.warn(
                "runanywhere.vision.failed",
                "VLM generation failed",
                mapOf("error" to (t.message ?: t.javaClass.simpleName)),
            )
            emit(VisionStreamView.Failed(t.message ?: t.javaClass.simpleName))
        }
    }

    /** Default options for VLM: streaming on, low temperature for
     *  image captioning, modest token budget. */
    private fun defaultOptions(): VLMGenerationOptions = VLMGenerationOptions(
        prompt = "",
        max_tokens = 512,
        temperature = 0.4f,
        top_p = 0.9f,
        top_k = 40,
        streaming_enabled = true,
        system_prompt = "You are a helpful assistant that describes images.",
        max_image_size = 1024,
        n_threads = 0,
        use_gpu = false,
        model_family = VLMModelFamily.VLM_MODEL_FAMILY_UNSPECIFIED,
        custom_chat_template = VLMChatTemplate(
            template_text = "",
            image_marker = "",
            default_system_prompt = "",
        ),
    )

    companion object {
        private val INSTANCE = java.util.concurrent.atomic.AtomicReference<RunAnywhereVisionEngine?>(null)

        fun install() {
            INSTANCE.compareAndSet(null, RunAnywhereVisionEngine())
        }

        fun get(): RunAnywhereVisionEngine =
            INSTANCE.get() ?: error(
                "RunAnywhereVisionEngine not installed — call install() from MeshlitApplication.onCreate",
            )
    }
}

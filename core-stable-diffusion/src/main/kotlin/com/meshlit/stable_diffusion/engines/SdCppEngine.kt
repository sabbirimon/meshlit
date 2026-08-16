package com.meshlit.stable_diffusion.engines

import android.content.Context
import android.util.Base64
import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.stable_diffusion.SdConstraints
import com.meshlit.stable_diffusion.SdEngine
import com.meshlit.stable_diffusion.SdGeneratedImage
import com.meshlit.stable_diffusion.SdLoadRequest
import com.meshlit.stable_diffusion.SdModelInfo
import com.meshlit.stable_diffusion.SdProgressEvent
import com.meshlit.stable_diffusion.SdRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Phase 4.x — `stable-diffusion.cpp` engine. The JNI stub body in
 * MVP1 returns typed "not implemented" failures so the dispatch
 * path is exercised end-to-end. Phase 2 swaps the native function
 * bodies for real sd.cpp + ggml calls.
 *
 * Lifecycle (mirrors `NativeParser.ensureLoaded()` in
 * `:core-terminal`):
 *  - `loadNativeLibrary()` is called lazily on first engine
 *    construction. The result is cached for the lifetime of the
 *    process; a missing library does not throw — `isReady` stays
 *    false and every op returns a typed failure.
 *  - `nativeLoadModel` writes a magic handle (0xC0FFEEL) to the
 *    outHandle array. The Kotlin side uses that to drive
 *    `isReady=true`. Phase 2 returns the real `sd_ctx_t*`.
 *
 * Threading:
 *  - All `nativeXxx` calls run on `Dispatchers.IO` via
 *    `withContext`. The UI thread never blocks.
 *
 * Progress:
 *  - The JNI side calls a `ProgressCallback` once per scheduler
 *    step. Phase 2 emits a `Step(current, total, previewB64)`
 *    event for each call. MVP1 emits nothing.
 */
class SdCppEngine(@Suppress("UNUSED_PARAMETER") context: Context) : SdEngine {

    override val engineTag: String = "sd.cpp-gguf"

    @Volatile private var nativeReady: Boolean = false
    @Volatile private var loadFailed: Boolean = false
    @Volatile private var handle: Long = 0L
    @Volatile private var modelInfo: SdModelInfo? = null

    // In-process progress channel. Phase 2 wires the JNI callback
    // to push SdProgressEvent.Step / Decoding here; MVP1 just
    // completes the txt2img call so callers don't deadlock.
    private val progressChannel = MutableSharedFlow<SdProgressEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val isReady: Boolean
        get() = handle != 0L && nativeReady

    override val loadedModel: SdModelInfo?
        get() = modelInfo

    override suspend fun loadModel(req: SdLoadRequest): MeshlitResult<SdModelInfo> = withContext(Dispatchers.IO) {
        if (!loadNativeLibrary()) {
            return@withContext MeshlitResult.Failure(
                MeshlitError.Native(
                    "sd.lib_not_linked",
                    IllegalStateException(
                        "libmeshlit_sd.so failed to load. Check that the .so is present in jniLibs/<abi>/ and the ABI matches the device.",
                    ),
                ),
            )
        }
        try {
            val outHandle = LongArray(1)
            val rc = nativeLoadModel(
                req.unetPath,
                req.textEncoderPath,
                req.vaePath,
                req.threads,
                req.gpuLayers,
                outHandle,
            )
            if (rc != 0) {
                return@withContext MeshlitResult.Failure(
                    MeshlitError.Native("sd.load_failed", IllegalStateException("nativeLoadModel returned rc=$rc")),
                )
            }
            handle = outHandle[0]
            val info = SdModelInfo(
                runtime = SdRuntime.StableDiffusionCpp,
                modelId = File(req.unetPath).name,
                unetPath = req.unetPath,
                textEncoderPath = req.textEncoderPath,
                vaePath = req.vaePath,
                taesdPath = req.taesdPath,
                approxSizeMb = listOfNotNull(req.unetPath, req.textEncoderPath, req.vaePath, req.taesdPath)
                    .sumOf { runCatching { File(it).length() }.getOrDefault(0L) / 1_000_000L },
                loadedAtEpochSec = System.currentTimeMillis() / 1000,
            )
            modelInfo = info
            progressChannel.tryEmit(SdProgressEvent.Loading(100))
            MeshlitResult.Success(info)
        } catch (t: Throwable) {
            MeshlitResult.Failure(MeshlitError.Native("sd.load_threw", t))
        }
    }

    override suspend fun unloadModel() = withContext(Dispatchers.IO) {
        if (handle != 0L && nativeReady) {
            runCatching { nativeUnload(handle) }
        }
        handle = 0L
        modelInfo = null
    }

    override suspend fun txt2img(c: SdConstraints): MeshlitResult<SdGeneratedImage> = withContext(Dispatchers.IO) {
        if (!isReady) {
            return@withContext MeshlitResult.Failure(
                MeshlitError.Native("sd.not_loaded", IllegalStateException("Tap Load in the Local SD Models card before generating.")),
            )
        }
        // OOM guard — reject requests that would blow past the
        // process memory budget before we even ask the native
        // side. Keeps a misconfigured sd.cpp from crashing the
        // whole app.
        val maxMem = Runtime.getRuntime().maxMemory()
        val requestedBytes = c.width.toLong() * c.height.toLong() * 4L
        if (requestedBytes > maxMem / 4L) {
            return@withContext MeshlitResult.Failure(
                MeshlitError.Native(
                    "sd.image_too_large",
                    IllegalStateException(
                        "Requested ${c.width}x${c.height} (${requestedBytes / 1_000_000L} MB) exceeds 1/4 of available process memory ($maxMem bytes). Lower the resolution or enable VAE tiling.",
                    ),
                ),
            )
        }
        try {
            val pngBytes: ByteArray? = nativeTxt2img(
                handle,
                c.prompt,
                c.negativePrompt,
                c.steps,
                c.cfgScale,
                c.sampler,
                c.seed,
                c.width,
                c.height,
            )
            if (pngBytes == null) {
                progressChannel.tryEmit(SdProgressEvent.Failed("sd.native_stub"))
                return@withContext MeshlitResult.Failure(
                    MeshlitError.Native(
                        "sd.native_stub",
                        IllegalStateException(
                            "libmeshlit_sd.so is a stub build — install stable-diffusion.cpp + ggml and rebuild to enable on-device inference.",
                        ),
                    ),
                )
            }
            progressChannel.tryEmit(SdProgressEvent.Decoding(null))
            progressChannel.tryEmit(SdProgressEvent.Completed)
            MeshlitResult.Success(
                SdGeneratedImage(
                    base64Png = Base64.encodeToString(pngBytes, Base64.NO_WRAP),
                    seed = c.seed.takeIf { it != -1L } ?: 0L,
                    durationSec = 0f,
                    prompt = c.prompt,
                ),
            )
        } catch (t: Throwable) {
            progressChannel.tryEmit(SdProgressEvent.Failed(t.message ?: "native_crash"))
            MeshlitResult.Failure(MeshlitError.Native("sd.txt2img_threw", t))
        }
    }

    override suspend fun img2img(c: SdConstraints): MeshlitResult<SdGeneratedImage> =
        MeshlitResult.Failure(
            MeshlitError.Native(
                "sd.img2img_unsupported",
                IllegalStateException("img2img on-device is Phase 2. Phase 1 only supports txt2img; for img2img, use the remote sd-server."),
            ),
        )

    override suspend fun interrupt() {
        withContext(Dispatchers.IO) {
            if (handle != 0L && nativeReady) {
                runCatching { nativeInterrupt(handle) }
            }
            progressChannel.tryEmit(SdProgressEvent.Failed("interrupted"))
        }
    }

    override fun progress(): Flow<SdProgressEvent> = progressChannel.asSharedFlow()

    // ── JNI ──────────────────────────────────────────────────────

    private fun loadNativeLibrary(): Boolean {
        if (nativeReady) return true
        if (loadFailed) return false
        return try {
            System.loadLibrary("meshlit_sd")
            val version = runCatching { nativeVersion() }.getOrNull()
            nativeReady = version != null
            loadFailed = !nativeReady
            nativeReady
        } catch (t: Throwable) {
            loadFailed = true
            false
        }
    }

    private external fun nativeVersion(): String?
    private external fun nativeLoadModel(
        unetPath: String,
        textEncoderPath: String?,
        vaePath: String?,
        threads: Int,
        gpuLayers: Int,
        outHandle: LongArray,
    ): Int
    private external fun nativeTxt2img(
        handle: Long,
        prompt: String,
        negativePrompt: String,
        steps: Int,
        cfg: Float,
        sampler: String,
        seed: Long,
        width: Int,
        height: Int,
    ): ByteArray?
    private external fun nativeUnload(handle: Long)
    private external fun nativeInterrupt(handle: Long)
}
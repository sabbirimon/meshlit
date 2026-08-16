package com.meshlit.stable_diffusion

import android.content.Context
import com.meshlit.stable_diffusion.engines.DiffusersEngine
import com.meshlit.stable_diffusion.engines.ExecuTorchEngine
import com.meshlit.stable_diffusion.engines.OnnxSdEngine
import com.meshlit.stable_diffusion.engines.SdCppEngine
import com.meshlit.stable_diffusion.engines.StubSdEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Phase 4.x — Routes `imageGenSdRuntimeFlow` to the matching
 * [SdEngine] implementation. Called by the `ImageGenScreen` whenever
 * the user picks a runtime or whenever the persisted value
 * changes.
 *
 * The router always returns a non-null engine — even an unknown
 * runtime string falls through to [StubSdEngine] so callers never
 * have to null-check.
 *
 * The router does not cache engine instances — Phase 2 will add a
 * `MutableStateFlow<SdEngine>` so the screen can observe the
 * active engine and re-render the status chip when the user
 * switches runtimes. For MVP1, `pick()` is called fresh on every
 * txt2img; engines are cheap to construct (no native work until
 * `loadModel`).
 */
class SdEngineRouter(
    private val context: Context,
    private val runtimeFlow: Flow<String>,
) {
    /** Suspend because the runtime value lives in DataStore —
     *  first() waits for the initial emission. */
    suspend fun pick(): SdEngine {
        val runtime = SdRuntime.fromKey(runtimeFlow.first())
        return build(runtime)
    }

    /** Synchronous overload used by callers that already know
     *  the runtime key (e.g. the bridge threading a user-picked
     *  runtime through). When the key is unknown it falls back to
     *  the persisted runtime, then to [SdRuntime.Stub]. */
    fun pickForKey(runtimeKey: String?): SdEngine {
        val runtime = runtimeKey?.let { SdRuntime.fromKeyOrNull(it) }
            ?: runCatching {
                kotlinx.coroutines.runBlocking { SdRuntime.fromKey(runtimeFlow.first()) }
            }.getOrDefault(SdRuntime.Stub)
        return build(runtime)
    }

    /** Observable active engine. The screen collects this so the
     *  status chip flips the moment the user picks a new runtime
     *  without waiting for the next txt2img call. */
    fun observe(): Flow<SdEngine> = runtimeFlow.map {
        build(SdRuntime.fromKey(it))
    }

    private fun build(runtime: SdRuntime): SdEngine = when (runtime) {
        SdRuntime.Stub -> StubSdEngine()
        SdRuntime.StableDiffusionCpp -> SdCppEngine(context)
        SdRuntime.OnnxRuntime -> OnnxSdEngine()
        SdRuntime.DiffusersPython -> DiffusersEngine()
        SdRuntime.ExecuTorch -> ExecuTorchEngine()
    }
}
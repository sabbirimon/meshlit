package com.meshlit.stable_diffusion

import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.runBlocking

/**
 * Phase 4.x — Backwards-compatible facade for the old
 * `LocalSdEngine` typed stub that lived in `:app/imagegen`. The
 * old class probed `System.loadLibrary("meshlit_sd")` directly and
 * returned `sd.not_linked`; this facade delegates every call to
 * [SdEngineRouter.pick] so the rest of the app can keep treating
 * SD as a single pluggable engine.
 *
 * The facade is intentionally thin: it exists to make the
 * `StableDiffusionBridge` constructor signature backwards-
 * compatible and to give callers (tests, ImageGenScreen) a stable
 * entry point. New code should depend on [SdEngine] directly.
 *
 * Threading:
 *  - `isLibraryLinked` / `engineTag` use `runBlocking { router.pick() }`
 *    so the call can stay synchronous. The router's `pick()`
 *    waits on `runtimeFlow.first()` which is fast (DataStore has
 *    the value cached after the first read).
 *  - `txt2img` / `interrupt` are `suspend` so they don't need the
 *    runBlocking trampoline.
 *
 * Migration:
 *  - `LocalSdEngine.isLibraryLinked` (val Boolean) → `isLibraryLinked()` (suspend fun).
 *  - `LocalSdEngine.engineTag` (val String) → `engineTag()` (suspend fun).
 *  - `LocalSdEngine.txt2img(c)` → signature unchanged.
 *  - `LocalSdEngine.interrupt()` → signature unchanged.
 */
class LocalSdEngine(
    private val router: SdEngineRouter,
) {
    /** Whether the runtime backing the router's current pick is
     *  actually wired in (true for sd.cpp when libmeshlit_sd.so is
     *  loaded, true for the stub-class runtimes when their AAR is
     *  on the classpath, false when the JNI lib isn't loaded). */
    suspend fun isLibraryLinked(): Boolean {
        return when (val engine = router.pick()) {
            is com.meshlit.stable_diffusion.engines.SdCppEngine -> true
            is com.meshlit.stable_diffusion.engines.StubSdEngine -> false
            else -> engine.engineTag != "sd-stub"
        }
    }

    /** Engine tag for the active pick. Stable per-runtime string
     *  ("sd.cpp-gguf" / "onnx-ort" / "diffusers-py" /
     *  "executorch-pte" / "sd-stub"). */
    suspend fun engineTag(): String = router.pick().engineTag

    /** Whether a model is currently loaded into the active engine.
     *  False when the router is on StubSdEngine. */
    suspend fun isReady(): Boolean = router.pick().isReady

    /** Currently loaded model metadata. */
    suspend fun loadedModel(): SdModelInfo? = router.pick().loadedModel

    /** Pass-through to the active engine's txt2img. The
     *  [runtimeKey] override lets the bridge thread a user-picked
     *  runtime (e.g. "onnx") through without having to update
     *  `SettingsRepository.imageGenSdRuntimeFlow` first; the
     *  router still re-queries DataStore when the key is null. */
    suspend fun txt2img(c: SdConstraints, runtimeKey: String? = null): MeshlitResult<SdGeneratedImage> =
        router.pickForKey(runtimeKey).txt2img(c)

    /** Pass-through to the active engine's img2img. */
    suspend fun img2img(c: SdConstraints): MeshlitResult<SdGeneratedImage> =
        router.pick().img2img(c)

    /** Pass-through to the active engine's loadModel. The
     *  LocalSdModelCard Load button calls this. */
    suspend fun loadModel(req: SdLoadRequest): MeshlitResult<SdModelInfo> =
        router.pick().loadModel(req)

    /** Pass-through to the active engine's unloadModel. */
    suspend fun unloadModel() = router.pick().unloadModel()

    /** Shorter alias for [unloadModel] — the bridge and the
     *  LocalSdModelCard both expect `unload`. */
    suspend fun unload() = router.pick().unloadModel()

    /** Pass-through to the active engine's interrupt. */
    suspend fun interrupt() = router.pick().interrupt()

    /** Pass-through to the active engine's progress stream.
     *  Subscribed in parallel with [txt2img] so the UI can render
     *  the partially-decoded preview while the suspend call is in
     *  flight. Empty flow for stub engines.
     *
     *  Non-suspend so the bridge can build the flow outside a
     *  coroutine scope. Reads the current router pick via
     *  `pickForKey(null)` which uses the persisted runtime without
     *  needing the suspending DataStore read. */
    fun progress(): kotlinx.coroutines.flow.Flow<SdProgressEvent> =
        router.pickForKey(null).progress()

    // ── sync shims ────────────────────────────────────────────────
    //
    // The old API had `val isLibraryLinked: Boolean` (non-suspend).
    // Callers that can't go suspend — e.g. the bridge's `val
    // isLocalEngineAvailable: Boolean` getter — use these blocking
    // shims. Keep them on a separate code path so it's obvious
    // which callers are paying the runBlocking cost.

    @Deprecated("Blocking shim — prefer isLibraryLinked() (suspend).")
    fun isLibraryLinkedBlocking(): Boolean = runBlocking { isLibraryLinked() }

    @Deprecated("Blocking shim — prefer engineTag() (suspend).")
    fun engineTagBlocking(): String = runBlocking { engineTag() }
}
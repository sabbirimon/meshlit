package com.meshlit.core.inference

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException

/**
 * Selection "modality" — the picker slot the user is choosing for.
 * Mirrors `com.runanywhere.runanywhereai.ui.screens.models
 * .ModelSelectionContext` from the upstream RunAnywhere sample.
 *
 * Today only `LLM` is wired up. `STT`, `TTS`, `DIFFUSION`, `OCR` are
 * out of scope for this PR — they're reserved for Phase 2+.
 */
enum class ModelSelectionContext(val title: String, val loadsModel: Boolean) {
    LLM("Choose Chat Model", loadsModel = true),
    STT("Speech Recognition", loadsModel = true),
    TTS("Text to Speech", loadsModel = true),
    DIFFUSION("Image Generation", loadsModel = true),
    OCR("Document OCR", loadsModel = true),
}

/**
 * A lifecycle-confirmed model snapshot captured immediately before
 * inference. Mirrors upstream `RuntimeModelSnapshot`.
 *
 * The snapshot is display-only — every picker calls
 * [RuntimeModelSelection.queryCurrent] immediately before inference
 * to re-ask the native runtime so the mirror is never trusted as
 * the execution authority.
 */
data class RuntimeModelSnapshot(
    val id: String,
    val displayName: String,
    val framework: String,
)

/**
 * Process-wide model state shared by every model picker.
 *
 * Picker ViewModels are screen-scoped while the native model
 * lifecycle is process-scoped. Keeping `currentModelId` in each
 * ViewModel therefore allowed one screen to keep showing (and
 * recording) model A after another screen had loaded model B.
 * This store mirrors lifecycle-confirmed snapshots to every
 * picker, while [queryCurrent] still queries native state
 * immediately before inference so the mirror is never trusted as
 * the execution authority.
 *
 * Mirrors `com.runanywhere.runanywhereai.ui.screens.models
 * .RuntimeModelSelection` (examples/android/RunAnywhereAI —
 * upstream RunAnywhere sample). Local adaptation: we use our
 * `InferenceCoordinator` as the execution authority rather than
 * the SDK's `RunAnywhere.currentModel(...)` because the coordinator
 * already owns engine routing and `loadedModel()`. The mirror is
 * still per-context, still `Flow`-observable, and still cleared on
 * `unloadAllForDownload`.
 */
object RuntimeModelSelection {

    private val log = logger("RuntimeModelSelection")
    private val store = RuntimeModelSelectionStore()

    fun observe(context: ModelSelectionContext): Flow<RuntimeModelSnapshot?> =
        store.observe(context)

    fun cached(context: ModelSelectionContext): RuntimeModelSnapshot? =
        store.snapshot(context)

    fun clear(context: ModelSelectionContext, expectedModelId: String? = null) {
        val current = store.snapshot(context)
        if (expectedModelId == null || current?.id == expectedModelId) {
            publish(context, null)
        }
    }

    fun clearModelEverywhere(modelId: String) {
        ModelSelectionContext.entries.forEach { context ->
            if (store.snapshot(context)?.id == modelId) publish(context, null)
        }
    }

    fun clearAll() {
        ModelSelectionContext.entries
            .filter { it.loadsModel }
            .forEach { context -> publish(context, null) }
    }

    /**
     * Unload every resident model, then clear lifecycle mirrors.
     * Returns false when the underlying unload fails so callers
     * can keep their previous selection state.
     */
    suspend fun unloadAllForDownload(
        coordinator: InferenceCoordinator,
    ): Boolean {
        return try {
            LlmModelChangeInterlock.awaitReadyForModelChange()
            coordinator.unloadModel()
            clearAll()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            log.warn(
                "runtime.unload_failed",
                "unloadAllForDownload failed",
                mapOf("error" to (t.message ?: t.javaClass.simpleName)),
            )
            false
        }
    }

    /**
     * Query the coordinator for the model that would execute for
     * [context]. This is intentionally a suspending query rather
     * than a read from [cached] so the mirror never becomes the
     * execution authority. Mirrors upstream
     * `RuntimeModelSelection.queryCurrent`.
     */
    suspend fun queryCurrent(
        context: ModelSelectionContext,
        coordinator: InferenceCoordinator,
    ): RuntimeModelSnapshot? {
        if (!context.loadsModel) return null
        val info = coordinator.loadedModel() ?: run {
            publish(context, null)
            return null
        }
        val snapshot = RuntimeModelSnapshot(
            id = info.modelPath,
            displayName = info.modelName,
            framework = coordinator.engineTag,
        )
        publish(context, snapshot)
        return snapshot
    }

    suspend fun requireCurrent(
        context: ModelSelectionContext,
        coordinator: InferenceCoordinator,
    ): RuntimeModelSnapshot = queryCurrent(context, coordinator)
        ?: error(
            "No ${context.title.removePrefix("Choose ").lowercase()} is loaded.",
        )

    /**
     * Publish a snapshot directly. Used by
     * [RuntimeModelSelection] internals and (rarely) by tests.
     * Most callers go through [queryCurrent].
     */
    fun publish(context: ModelSelectionContext, snapshot: RuntimeModelSnapshot?) {
        store.publish(context, snapshot)
    }

    /**
     * Result-typed loader: returns [MeshlitResult.Failure] with a
     * typed [MeshlitError.Invalid] if nothing is loaded. Mirrors
     * the pattern upstream callers expect (e.g. `chatVm.send(...)`
     * checks the snapshot before inferring).
     */
    suspend fun ensureLoaded(
        context: ModelSelectionContext,
        coordinator: InferenceCoordinator,
    ): MeshlitResult<RuntimeModelSnapshot> {
        val snap = queryCurrent(context, coordinator)
            ?: return MeshlitResult.Failure(
                MeshlitError.Invalid(
                    "runtime.no_model_loaded:${context.name.lowercase()}",
                ),
            )
        return MeshlitResult.Success(snap)
    }
}

/**
 * Backing store behind [RuntimeModelSelection]. Kept separately so
 * it can be unit-tested without pulling in the coordinator or the
 * Android runtime.
 */
internal class RuntimeModelSelectionStore {
    private val snapshots = MutableStateFlow<Map<ModelSelectionContext, RuntimeModelSnapshot>>(emptyMap())

    fun observe(context: ModelSelectionContext): Flow<RuntimeModelSnapshot?> =
        snapshots.map { it[context] }.distinctUntilChanged()

    fun snapshot(context: ModelSelectionContext): RuntimeModelSnapshot? =
        snapshots.value[context]

    fun publish(context: ModelSelectionContext, snapshot: RuntimeModelSnapshot?) {
        synchronized(this) {
            snapshots.value = if (snapshot == null) {
                snapshots.value - context
            } else {
                snapshots.value + (context to snapshot)
            }
        }
    }
}
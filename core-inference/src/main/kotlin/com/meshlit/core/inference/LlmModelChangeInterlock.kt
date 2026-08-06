package com.meshlit.core.inference

/**
 * Process-local handoff between the activity-scoped chat and every
 * LLM picker. It makes model loading wait for any chat/native
 * cancellation barrier first.
 *
 * Mirrors `com.runanywhere.runanywhereai.ui.screens.models
 * .LlmModelChangeInterlock` (examples/android/RunAnywhereAI —
 * upstream RunAnywhere sample). Why we port it:
 *
 *  - The chat activity can be mid-generation when the user opens
 *    Models and picks a different model. Without a barrier the
 *    new `RunAnywhere.loadModel(...)` races against the in-flight
 *    infer loop and the native runner crashes.
 *  - The barrier is single-slot — only one owner can install a
 *    callback at a time. A second install overwrites the first;
 *    identity-checked `remove` keeps the swap safe.
 *  - The callback is `suspend` so the picker actually pauses on
 *    chat cancellation rather than busy-spinning.
 *
 * Wiring:
 *  - `MeshlitApplication.onCreate` (or the chat activity's
 *    `onCreate`) calls [install] with an await-callback that
 *    drains any in-flight generation.
 *  - `InferenceCoordinator.loadModelInternal` awaits
 *    [awaitReadyForModelChange] before delegating to the engine.
 *  - `RuntimeModelSelection.unloadAllForDownload` awaits the same.
 */
object LlmModelChangeInterlock {
    private var owner: Any? = null
    private var awaitReady: (suspend () -> Unit)? = null

    @Synchronized
    fun install(owner: Any, awaitReady: suspend () -> Unit) {
        this.owner = owner
        this.awaitReady = awaitReady
    }

    @Synchronized
    fun remove(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            awaitReady = null
        }
    }

    suspend fun awaitReadyForModelChange() {
        val callback = synchronized(this) { awaitReady }
        callback?.invoke()
    }
}
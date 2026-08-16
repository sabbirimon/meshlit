package com.meshlit.core.advanced.engines

import com.meshlit.core.common.MeshlitResult
import java.io.File

/**
 * Common SPI for every on-device AI engine (LLM, STT, TTS, VAD, embed,
 * OCR, vision, diarization, image gen). Each engine owns its own native
 * artifact and lifecycle, but they all expose the same shape so a
 * [SolutionRunner] can YAML-chain them.
 *
 * Lifecycle:
 *  1. [load] with the path to the model artifact. Idempotent: a second
 *     call is a no-op if the engine is already loaded.
 *  2. [run] with a request; returns a typed response. The request type
 *     is engine-specific (see the engine's own file for the request
 *     data class).
 *  3. [unload] releases native resources.
 *
 * Stub policy: every engine ships as a stub today. The stub returns a
 * deterministic placeholder text so the UI flow is testable end-to-end.
 * The stub is annotated `// STUB: real impl pending assets` and flips to
 * a real implementation when the artifact lands.
 */
interface BaseEngine<Req, Resp> {
    /** Engine identifier, e.g. `"whisper"`, `"kokoro"`, `"silero_vad"`. */
    val id: String

    /** Which [EngineCategory] this engine implements. */
    val category: EngineCategory

    /**
     * Path to the on-disk model artifact. `null` if no model has been
     * loaded yet. Set by [load], cleared by [unload].
     */
    val modelPath: File?

    /** True if the engine is ready to serve [run] calls. */
    val isLoaded: Boolean

    /** Load the model from [path]. Idempotent. */
    suspend fun load(path: File): MeshlitResult<Unit>

    /** Run the engine on a request. Caller must check [isLoaded] first. */
    suspend fun run(request: Req): MeshlitResult<Resp>

    /** Release any native resources. Safe to call multiple times. */
    suspend fun unload(): MeshlitResult<Unit>
}

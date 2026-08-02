package com.meshlit.core.inference.net

/**
 * Optional seam the embedded [InferenceHttpServer] consults once per
 * `POST /v1/infer` to record job-start and job-end events. The
 * default is a no-op so nodes that don't wire a listener still
 * serve inference.
 *
 * `:app` provides an implementation that bridges into the
 * `MetricsRegistry` so the `MetricsScreen` queue gauge and failure
 * breakdown reflect what the server is actually doing.
 *
 * The token returned from [start] is opaque to the server; the same
 * value must be handed back to [end] for the listener to pair them
 * correctly. The server is single-call-site so this is easy.
 */
interface JobLifecycle {

    /** Called the moment the server decides to handle the request. */
    fun start(): Token

    /** Called when the SSE stream completes (success or failure). */
    fun end(token: Token, outcome: Outcome)

    /** Outcome of a single inference job. */
    sealed class Outcome {
        data class Success(val generatedTokens: Int, val tokensPerSecond: Float) : Outcome()
        data class Failure(val tag: String, val message: String) : Outcome()
    }

    /** Opaque handle returned from [start] and consumed by [end]. */
    interface Token

    companion object {
        /** No-op listener; counts nothing. */
        val NOOP: JobLifecycle = object : JobLifecycle {
            override fun start(): Token = object : Token {}
            override fun end(token: Token, outcome: Outcome) = Unit
        }
    }
}

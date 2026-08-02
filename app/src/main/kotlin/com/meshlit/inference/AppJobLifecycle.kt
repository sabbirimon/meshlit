package com.meshlit.inference

import com.meshlit.core.inference.net.JobLifecycle

/**
 * Bridges the embedded `InferenceHttpServer`'s job events into the
 * process-wide [MetricsRegistry]. Each `/v1/infer` request starts
 * here (queue +1), then ends here with the captured outcome.
 *
 * The token handed back to the server is the [MetricsRegistry.JobToken]
 * which already holds the start time — we use it so the sparkline
 * sample rate isn't derailed by the server thread.
 */
class AppJobLifecycle(
    private val metrics: MetricsRegistry,
) : JobLifecycle {

    override fun start(): JobLifecycle.Token {
        return metrics.recordJobStart()
    }

    override fun end(token: JobLifecycle.Token, outcome: JobLifecycle.Outcome) {
        // The token type is concrete in our impl. Other implementations
        // are free to send something else; we silently ignore it.
        val mt = token as? MetricsRegistry.JobToken ?: return
        val wireOutcome = when (outcome) {
            is JobLifecycle.Outcome.Success -> MetricsRegistry.JobOutcome.Success(
                tokens = outcome.generatedTokens,
                tokensPerSecond = outcome.tokensPerSecond,
            )
            is JobLifecycle.Outcome.Failure -> MetricsRegistry.JobOutcome.Failure(
                tag = outcome.tag,
                message = outcome.message,
            )
        }
        metrics.recordJobEnd(mt, wireOutcome)
    }
}
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
    private val rewards: com.meshlit.peering.ContributionStore? = null,
) : JobLifecycle {

    override fun start(): JobLifecycle.Token {
        return ReceiptToken(metrics.recordJobStart(), java.util.UUID.randomUUID().toString())
    }

    override fun end(token: JobLifecycle.Token, outcome: JobLifecycle.Outcome) {
        // The token type is concrete in our impl. Other implementations
        // are free to send something else; we silently ignore it.
        val receipt = token as? ReceiptToken ?: return
        val mt = receipt.metrics
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
        if (outcome is JobLifecycle.Outcome.Success && outcome.executedLocally) {
            outcome.generatedTokens?.takeIf { it > 0 }?.let { rewards?.record(receipt.id, it) }
        }
    }
    private data class ReceiptToken(val metrics: MetricsRegistry.JobToken, val id: String) : JobLifecycle.Token
}

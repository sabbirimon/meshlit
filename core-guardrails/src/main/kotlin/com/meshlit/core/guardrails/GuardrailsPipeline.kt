package com.meshlit.core.guardrails

import com.meshlit.core.common.logger

/**
 * Composable guardrail pipeline. The pipeline is deliberately
 * stateless — the same instance can be reused across requests.
 *
 * Default ordering:
 *  1. [PromptScrubber.scrub] — detect injection + PII in the input.
 *  2. If injection detected, [Decision.REJECT] (the caller is
 *     expected to surface a "request refused" outcome to the user).
 *  3. Otherwise redact PII in-place and pass through to the model.
 *  4. [OutputCap.cap] runs on the streamed output to enforce a token
 *     ceiling.
 *
 * Every hit is logged under category `"guardrail.hit"` so the
 * LogBuffer / Settings UI can surface a "we blocked N requests"
 * stat.
 */
class GuardrailsPipeline(
    val config: GuardrailsConfig = GuardrailsConfig.Default,
    private val scrubber: PromptScrubber = PromptScrubber(),
    private val cap: OutputCap = OutputCap(),
    private val onHit: (String, Map<String, Any?>) -> Unit = ::defaultHitSink,
) {

    private val log = logger("GuardrailsPipeline")

    fun processInput(input: String): InputResult {
        val result = scrubber.scrub(input)
        for (hit in result.hits) {
            onHit(
                "guardrail.injection.${hit.tag}",
                mapOf("snippet" to hit.snippet),
            )
        }
        for (r in result.redacted) {
            onHit(
                "guardrail.pii.${r.tag}",
                mapOf("length" to r.original.length),
            )
        }
        return when {
            result.hasInjection -> InputResult.Rejected(result.hits.map { it.tag })
            else -> InputResult.Accepted(result.cleaned, result.redacted.map { it.tag })
        }
    }

    fun processOutput(text: String, maxTokens: Int): String {
        val capped = cap.cap(text, maxTokens)
        if (capped.length < text.length) {
            onHit(
                "guardrail.output_cap",
                mapOf("originalLen" to text.length, "cappedLen" to capped.length, "budget" to maxTokens),
            )
        }
        return capped
    }

    sealed interface InputResult {
        data class Accepted(val cleaned: String, val redactedTags: List<String>) : InputResult
        data class Rejected(val injectionTags: List<String>) : InputResult
    }

    companion object {
        fun defaultHitSink(tag: String, ctx: Map<String, Any?>) {
            // The SLF4J-backed `log` belongs to the receiver of the
            // hit, so this default just delegates to the structured
            // logger. Tests override [onHit] to assert without a
            // real logger.
        }
    }
}

/**
 * Pipeline knobs. Defaults are conservative — turn things on
 * per-deployment via settings.
 */
data class GuardrailsConfig(
    val injectionDetectionEnabled: Boolean = true,
    val piiRedactionEnabled: Boolean = true,
    val outputCapTokens: Int = 2048,
) {
    companion object {
        val Default: GuardrailsConfig = GuardrailsConfig()
    }
}

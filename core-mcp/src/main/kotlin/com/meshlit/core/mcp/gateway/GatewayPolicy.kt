package com.meshlit.core.mcp.gateway

import kotlinx.serialization.Serializable
import com.meshlit.core.guardrails.PromptScrubber

@Serializable enum class ContentMode { FILTERED, CUSTOM, MINIMAL }
/** Literal rules avoid regex denial-of-service on untrusted content. Not a moderation classifier. */
@Serializable data class GatewayPolicy(val mode:ContentMode=ContentMode.FILTERED,
    val inputTerms:List<String> = emptyList(), val outputTerms:List<String> = emptyList(),
    val maxInputChars:Int=32000, val maxOutputChars:Int=64000,
    val blockPromptInjection:Boolean=false,val allowAgentPromptProtectionChanges:Boolean=false) {
    fun agentPromptProtection(enabled:Boolean):GatewayPolicy {
        require(allowAgentPromptProtectionChanges) {"Owner has not delegated prompt-protection changes"}
        return copy(blockPromptInjection=enabled).also {it.validate()}
    }
    fun validate() {
        require(maxInputChars in 1..32000 && maxOutputChars in 1..64000)
        listOf(inputTerms,outputTerms).forEach { terms ->
            require(terms.size<=32 && terms.all { it.length in 1..100 && '\u0000' !in it })
        }
    }
    fun check(text:String,output:Boolean=false) {
        validate();require(text.length<=if(output) maxOutputChars else maxInputChars) { "Content exceeds gateway limit" }
        // Bounded, local heuristic only. Discard cleaned text, PII and snippets;
        // this gate neither rewrites content nor emits it to logs or a service.
        require(!blockPromptInjection || !scrubber.scrub(text).hasInjection) { "Content blocked by optional prompt-injection protection" }
        val terms=if(output) outputTerms else inputTerms
        require(mode==ContentMode.MINIMAL || terms.none { text.contains(it,ignoreCase=true) }) { "Content blocked by gateway policy" }
    }
    companion object { private val scrubber=PromptScrubber() }
}

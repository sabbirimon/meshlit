package com.meshlit.chat

import com.meshlit.agent.AgentTaskPreset

/** Pure draft transformation. Saving this conversation is a separate explicit action. */
object ChatTaskPreset {
    fun unavailableReason(preset:AgentTaskPreset,options:ChatOptions,coreCandidate:Boolean=false,review:Boolean=false):String? {
        if(preset.suggestedTools.isEmpty()) return null
        if(coreCandidate || review) return "Task presets with tools require the Experimental app outside Play review."
        if(!options.supportsLocalToolSelection) return "Task presets with tools need a local or paired-cluster model. Use local model for tools below."
        if(preset.suggestedTools.sumOf { it.count }>10) return "This task exceeds the ten-tool limit."
        return null
    }

    fun apply(preset:AgentTaskPreset,options:ChatOptions,coreCandidate:Boolean=false,review:Boolean=false):ChatOptions {
        val reason=unavailableReason(preset,options,coreCandidate,review)
        require(reason==null) { reason.orEmpty() }
        var draft=options.copy(systemPrompt=preset.instruction)
        ChatToolGroup.entries.forEach { draft=it.update(draft,false,review) }
        preset.suggestedTools.forEach { draft=it.update(draft,true,review) }
        return draft
    }
}

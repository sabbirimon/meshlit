package com.meshlit.core.inference.models

import kotlinx.serialization.Serializable

/** Human-authored prompt instructions, not a modification to model weights or provider policy. */
@Serializable data class LocalModelBehavior(val enabled:Boolean=false,val instructions:String="") {
    fun validate(){require(instructions.length<=4000 && '\u0000' !in instructions){"Local instructions must be at most 4,000 characters"};require(!enabled || instructions.isNotBlank()){ "Enter instructions before enabling" }}
    fun decorate(prompt:String):String {
        validate()
        if(!enabled) return prompt
        require(prompt.length<=96000){"Prompt is too large for custom local instructions"}
        return "User-configured local instructions:\n${instructions.trim()}\n\nRequest:\n$prompt"
    }
}

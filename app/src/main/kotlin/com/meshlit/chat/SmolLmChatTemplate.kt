package com.meshlit.chat

/** Qwen and SmolLM2 instruction models use ChatML role markers.
 * Keep user and generated text inside their own message; never let literal markers create roles. */
internal fun smolLmChatPrompt(system: String, history: List<ChatMessage>, user: String): String {
    fun content(value: String) = value
        .replace("<|im_start|>", "<| im_start |>")
        .replace("<|im_end|>", "<| im_end |>")
    return buildString {
        append("<|im_start|>system\n")
        append(content(system.take(7600).ifBlank { "You are Meshlit, a helpful AI assistant." }))
        append("<|im_end|>\n")
        history.takeLast(20).forEach { message ->
            if (message.role in setOf("user", "assistant")) {
                append("<|im_start|>").append(message.role).append('\n')
                append(content(message.text.take(4000))).append("<|im_end|>\n")
            }
        }
        append("<|im_start|>user\n").append(content(user)).append("<|im_end|>\n<|im_start|>assistant\n")
    }
}

/** All three adapters accept raw completion prompts; native CPU/RPC need the same role formatting. */
internal fun usesChatMlPrompt(engine:String,modelName:String):Boolean =
    engine in setOf("runanywhere","llama-native-local","llama-rpc-layer") &&
        listOf("SmolLM2","Qwen2.5","Qwen3").any { modelName.contains(it,ignoreCase=true) }

/** Qwen3's published ChatML template supports an empty thinking prefix for fast mode. */
internal fun qwen3ChatPrompt(system:String,history:List<ChatMessage>,user:String):String {
    val thinking=qwen3ThinkingRequested(user)
    val prompt=smolLmChatPrompt(system,history,user)
    return prompt+if(thinking) "<think>\n" else "<think>\n\n</think>\n\n"
}

internal fun qwen3ThinkingRequested(user:String)=user.contains("/think",ignoreCase=true) && !user.contains("/no_think",ignoreCase=true)

/** Qwen3's opening thinking marker is supplied by the template, so it is not in streamed text. */
internal fun qwen3AnswerText(raw:String,thinkingStarted:Boolean):String {
    val close=raw.indexOf("</think>",ignoreCase=true)
    if(close>=0 && (thinkingStarted || raw.trimStart().startsWith("<think>",ignoreCase=true))) return raw.substring(close+8).trimStart()
    return if(thinkingStarted || raw.trimStart().startsWith("<think>",ignoreCase=true)) "" else raw
}

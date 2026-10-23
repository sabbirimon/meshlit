package com.meshlit.chat

/** Pinned bundled SmolLM2-135M-Instruct uses the publisher's ChatML role markers.
 * Keep user and generated text inside their own message; never let literal markers create roles. */
internal fun smolLmChatPrompt(system: String, history: List<ChatMessage>, user: String): String {
    fun content(value: String) = value
        .replace("<|im_start|>", "<| im_start |>")
        .replace("<|im_end|>", "<| im_end |>")
    return buildString {
        append("<|im_start|>system\n")
        append(content(system.take(5200).ifBlank { "You are a helpful AI assistant named SmolLM, trained by Hugging Face." }))
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

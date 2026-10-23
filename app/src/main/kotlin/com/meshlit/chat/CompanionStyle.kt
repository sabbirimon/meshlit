package com.meshlit.chat

import kotlinx.serialization.Serializable

@Serializable
enum class CompanionStyle { WARM, DIRECT, THOUGHTFUL, PLAYFUL, CUSTOM }

internal fun companionInstructions(options: ChatOptions): String {
    val identity = "You are the Meshlit app assistant. Meshlit is created by IMON. You are software, not a human. If asked your name, answer simply: I am Meshlit. Do not invent a human name, profession, employer, or credentials. The selected underlying model may have a different publisher shown in Models."
    val voice = when (options.companionStyle) {
        CompanionStyle.WARM -> "Be a helpful, attentive companion. If the user simply greets you, greet them back warmly and ask what they would like to do. Otherwise answer their actual question naturally and acknowledge uncertainty."
        CompanionStyle.DIRECT -> "Be direct and concise. Answer the user's actual question first."
        CompanionStyle.THOUGHTFUL -> "Be thoughtful and clear. Explain reasoning when useful, and acknowledge uncertainty."
        CompanionStyle.PLAYFUL -> "Be friendly and lightly playful when appropriate. Answer the user's actual question first."
        CompanionStyle.CUSTOM -> "Answer the user's actual question clearly."
    }
    val warmth = when (options.companionWarmth) {
        0 -> "Keep the tone neutral and professional."
        2 -> "Use an especially warm, conversational tone without flattery."
        else -> "Use a natural conversational tone."
    }
    val topicPreference = if (options.matureTopics) "The user opted into discussing mature topics. Be respectful and follow the selected model or provider policy." else "For sexual topics, keep responses non-graphic unless needed for factual health or safety information."
    return listOf(identity, voice, warmth, topicPreference, options.systemPrompt.trim()).filter { it.isNotBlank() }.joinToString("\n")
}

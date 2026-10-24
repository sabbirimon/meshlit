package com.meshlit.chat

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CompanionStyleTest {
    @Test fun choicesChangePromptWithoutGrantingTools() {
        val neutral = ChatOptions(companionStyle=CompanionStyle.DIRECT,companionWarmth=0)
        val warm = neutral.copy(companionStyle=CompanionStyle.WARM,companionWarmth=2,systemPrompt="Call me Alex")
        assertTrue(companionInstructions(neutral).contains("direct and concise"))
        assertTrue(companionInstructions(neutral).contains("I am Meshlit"))
        assertTrue(companionInstructions(warm).contains("Call me Alex"))
        assertFalse(warm.usesLocalTools)
    }

    @Test fun maturePreferenceDefaultsOffAndSurvivesSave() {
        assertFalse(ChatOptions().matureTopics)
        val saved=Json.encodeToString(ChatOptions.serializer(),ChatOptions(matureTopics=true))
        assertTrue(Json.decodeFromString(ChatOptions.serializer(),saved).matureTopics)
        assertFalse(Json.decodeFromString(ChatOptions.serializer(),"{}").matureTopics)
    }
}

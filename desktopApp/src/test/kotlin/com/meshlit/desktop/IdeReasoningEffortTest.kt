package com.meshlit.desktop

import kotlinx.serialization.json.*
import kotlin.test.*

class IdeReasoningEffortTest {
    private fun route(adapter: String = "reasoning_effort", endpoint: String = "https://example.test/v1") = buildJsonObject {
        put("kind", "compatible"); put("endpoint", endpoint); put("model", "configured-model")
        put("effortSupport", buildJsonObject {
            put("endpoint", endpoint); put("model", "configured-model"); put("adapter", adapter)
            put("values", buildJsonArray { add("low"); add("high") })
        })
    }
    private val sdk = buildJsonObject {
        put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", "Question") }) })
        put("reasoning_effort", "high"); put("reasoning", buildJsonObject { put("effort", "high") })
        put("think", true); put("thinking", buildJsonObject { put("type", "enabled") }); put("enable_thinking", true); put("max_tokens", 50000); put("temperature", 0.5)
    }
    @Test fun autoOmitsAllSdkEffortAndKeepsNativeOutputLimit() {
        for (r in listOf(null, buildJsonObject { put("kind", "compatible") }, buildJsonObject { put("kind", "local") })) {
            val result = ideCompletionBody(sdk, r, "auto", "actual-model", 256, false)
            for (field in listOf("reasoning_effort", "reasoning", "think", "thinking", "enable_thinking")) assertNull(result[field])
            assertEquals(256, result["max_tokens"]!!.jsonPrimitive.int)
            assertEquals(sdk["messages"], result["messages"])
        }
    }
    @Test fun autoUsesConfiguredReasoningModelSchemaWithoutSettingEffort() {
        val result = ideCompletionBody(sdk, route(), "auto", "configured-model", 256, false)
        assertEquals(256, result["max_completion_tokens"]!!.jsonPrimitive.int)
        assertNull(result["max_tokens"]); assertNull(result["temperature"])
        assertNull(result["reasoning_effort"]); assertNull(result["reasoning"])
    }
    @Test fun explicitEffortUsesConfiguredAdapterWithoutChangingBudgetOrMessages() {
        val result = ideCompletionBody(sdk, route(), "low", "configured-model", 256, false)
        assertEquals("low", result["reasoning_effort"]!!.jsonPrimitive.content)
        assertEquals(256, result["max_completion_tokens"]!!.jsonPrimitive.int)
        assertNull(result["max_tokens"]); assertNull(result["temperature"]); assertNull(result["reasoning"])
        assertEquals(sdk["messages"], result["messages"])
        val router = ideCompletionBody(sdk, route("reasoning.effort", "https://openrouter.ai/api/v1"), "high", "configured-model", 256, false)
        assertEquals("high", router["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertNull(router["reasoning_effort"])
    }
    @Test fun unsupportedOrWrongBindingIsRejectedBeforeBuildingRequest() {
        assertFails { ideCompletionBody(sdk, null, "high", "local", 256, false) }
        assertFails { ideCompletionBody(sdk, route(), "medium", "configured-model", 256, false) }
        assertFails { ideCompletionBody(sdk, route(), "ultra", "configured-model", 256, false) }
        assertFails { ideCompletionBody(sdk, route("reasoning.effort"), "high", "configured-model", 256, false) }
        val changed = JsonObject(route().toMutableMap().apply { put("model", JsonPrimitive("another-model")) })
        assertFails { ideCompletionBody(sdk, changed, "high", "another-model", 256, false) }
    }
}

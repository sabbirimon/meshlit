package com.meshlit.core.net.openrouter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for the OpenRouter wire DTOs.
 *
 * The fixtures below are verbatim from the `/api/v1/models`
 * payload shape documented at openrouter.ai. We pin the parser
 * behaviour against hand-rolled JSON so a future OpenRouter field
 * addition doesn't silently break Meshlit's catalog browser.
 */
class OpenRouterModelsTest {

    private val json = OpenRouterClient.OpenRouterJson

    @Test
    fun models_response_round_trips_with_canonical_keys() {
        val payload = """
            {
              "data": [
                {
                  "id": "anthropic/claude-3.5-sonnet",
                  "canonical_slug": "anthropic/claude-3.5-sonnet",
                  "hugging_face_id": null,
                  "name": "Anthropic: Claude 3.5 Sonnet",
                  "created": 1723507200,
                  "description": "Newest Claude model",
                  "context_length": 200000,
                  "architecture": {
                    "modality": "text",
                    "input_modalities": ["text"],
                    "output_modalities": ["text"],
                    "tokenizer": "Claude"
                  },
                  "pricing": {
                    "prompt": "0.000003",
                    "completion": "0.000015",
                    "request": "0",
                    "image": "0"
                  },
                  "top_provider": {
                    "context_length": 200000,
                    "max_completion_tokens": 8192,
                    "is_moderated": true
                  },
                  "supported_parameters": ["tools", "tool_choice", "temperature", "top_p"]
                }
              ]
            }
        """.trimIndent()
        val decoded = json.decodeFromString(OpenRouterModelsResponse.serializer(), payload)
        assertEquals(1, decoded.data.size)
        val model = decoded.data.first()
        assertEquals("anthropic/claude-3.5-sonnet", model.id)
        assertEquals("Anthropic: Claude 3.5 Sonnet", model.name)
        assertEquals(200_000L, model.contextLength)
        assertEquals("0.000003", model.pricing.prompt)
        assertEquals("0.000015", model.pricing.completion)
        assertEquals("anthropic", model.providerDisplay)
        assertTrue(model.supportedParameters.contains("tools"))
        // Re-encode; the result must contain the price strings
        // verbatim (string fields, no rounding).
        val encoded = json.encodeToString(OpenRouterModelsResponse.serializer(), decoded)
        assertTrue("prompt price lost on re-encode", encoded.contains("\"prompt\":\"0.000003\""))
        assertTrue("completion price lost on re-encode", encoded.contains("\"completion\":\"0.000015\""))
    }

    @Test
    fun free_model_decodes_with_zero_pricing() {
        val payload = """
            {
              "id": "qwen/qwen-2.5-72b-instruct:free",
              "name": "Qwen 2.5 72B Instruct (free)",
              "context_length": 32768,
              "pricing": {"prompt": "0", "completion": "0", "request": "0", "image": "0"},
              "top_provider": {"context_length": 32768, "max_completion_tokens": null, "is_moderated": false},
              "supported_parameters": ["temperature", "top_p"]
            }
        """.trimIndent()
        val model = json.decodeFromString(OpenRouterModel.serializer(), payload)
        assertEquals("qwen", model.providerDisplay)
        assertEquals("0", model.pricing.prompt)
        assertEquals("0", model.pricing.completion)
        assertFalse(model.topProvider?.isModerated == true)
        assertEquals(null, model.topProvider?.maxCompletionTokens)
    }

    @Test
    fun auth_key_response_decodes_with_free_tier_flag() {
        val payload = """
            {
              "data": {
                "label": "Meshlit iPhone",
                "limit": 10.0,
                "usage": 0.42,
                "is_free_tier": true,
                "usage_total_tokens": 8421
              }
            }
        """.trimIndent()
        val parsed = json.decodeFromString(OpenRouterAuthKey.serializer(), payload)
        assertEquals("Meshlit iPhone", parsed.data.label)
        assertEquals(10.0, parsed.data.limit!!, 0.001)
        assertEquals(0.42, parsed.data.usage, 0.001)
        assertTrue(parsed.data.isFreeTier)
        assertEquals(8421L, parsed.data.usageTotalTokens)
        assertEquals("Free tier", parsed.data.tierLabel)
        assertEquals("Free tier", parsed.data.tierLabel)
    }

    @Test
    fun auth_key_response_decodes_without_limit_field() {
        val payload = """
            { "data": { "label": null, "usage": 5.5, "is_free_tier": false, "usage_total_tokens": 12 } }
        """.trimIndent()
        val parsed = json.decodeFromString(OpenRouterAuthKey.serializer(), payload)
        assertEquals(null, parsed.data.label)
        assertEquals(null, parsed.data.limit)
        assertEquals(5.5, parsed.data.usage, 0.001)
        assertEquals("Paid tier", parsed.data.tierLabel)
        assertTrue("usage label should mention 'no limit'", parsed.data.usageLabel.contains("no limit"))
    }

    @Test
    fun chat_request_round_trips_with_provider_pinning() {
        val req = OpenRouterChatRequest(
            model = "anthropic/claude-3.5-sonnet",
            messages = listOf(
                OpenRouterMessage(role = "system", content = "You are Meshlit."),
                OpenRouterMessage(role = "user", content = "Hello"),
            ),
            stream = true,
            temperature = 0.7,
            maxTokens = 256,
            provider = OpenRouterProviderPrefs(only = listOf("anthropic"), sort = "throughput"),
            user = "request-123",
        )
        val encoded = json.encodeToString(OpenRouterChatRequest.serializer(), req)
        assertTrue("model lost on encode", encoded.contains("\"model\":\"anthropic/claude-3.5-sonnet\""))
        assertTrue("provider.only lost on encode", encoded.contains("\"only\":[\"anthropic\"]"))
        assertTrue("provider.sort lost on encode", encoded.contains("\"sort\":\"throughput\""))
        assertTrue("user lost on encode", encoded.contains("\"user\":\"request-123\""))
        // Round-trip
        val decoded = json.decodeFromString(OpenRouterChatRequest.serializer(), encoded)
        assertEquals(req.model, decoded.model)
        assertEquals(2, decoded.messages.size)
        assertEquals("system", decoded.messages[0].role)
        assertEquals("user", decoded.messages[1].role)
        assertEquals(0.7, decoded.temperature!!, 0.001)
        assertEquals(256, decoded.maxTokens)
        assertEquals(listOf("anthropic"), decoded.provider?.only)
    }

    @Test
    fun stream_chunk_with_usage_block_decodes() {
        val payload = """
            {
              "id": "gen-abc",
              "model": "anthropic/claude-3.5-sonnet",
              "choices": [
                {
                  "index": 0,
                  "delta": {"role": "assistant", "content": "Hello"},
                  "finish_reason": null
                }
              ],
              "usage": {"prompt_tokens": 12, "completion_tokens": 1, "total_tokens": 13, "cost": "0.000018"}
            }
        """.trimIndent()
        // The client parses this internally; we re-validate via the
        // public DTOs the client uses for the non-streaming path.
        val parsed = json.decodeFromString(OpenRouterChatResponse.serializer(), payload)
        assertEquals("Hello", parsed.choices.first().delta?.content)
        assertEquals(12, parsed.usage!!.promptTokens)
        assertEquals("0.000018", parsed.usage.cost)
    }

    @Test
    fun unknown_fields_are_ignored_on_decode() {
        // OpenRouter occasionally adds fields; the DTOs must
        // tolerate them via `ignoreUnknownKeys = true`.
        val payload = """
            {
              "id": "openai/gpt-5.6",
              "name": "OpenAI: GPT-5.6",
              "context_length": 128000,
              "pricing": {"prompt": "0.00001", "completion": "0.00003"},
              "phase99Field": "ignored",
              "top_provider": {"context_length": 128000, "is_moderated": true, "phase99Nested": true}
            }
        """.trimIndent()
        val model = json.decodeFromString(OpenRouterModel.serializer(), payload)
        assertEquals("openai/gpt-5.6", model.id)
        assertEquals("openai", model.providerDisplay)
        assertEquals(128_000L, model.contextLength)
        assertNotNull(model.topProvider)
    }

    @Test
    fun provider_slug_handles_missing_slash() {
        // Some hypothetical models on OpenRouter's preview channel
        // might not include a provider prefix; the parser must not
        // crash on the missing-delimiter case.
        val payload = """
            {
              "id": "preview-model-no-slug",
              "name": "Preview Model",
              "context_length": 1024,
              "pricing": {"prompt": "0", "completion": "0"}
            }
        """.trimIndent()
        val model = json.decodeFromString(OpenRouterModel.serializer(), payload)
        assertEquals("", model.providerSlug)
        assertEquals("", model.providerDisplay)
    }
}
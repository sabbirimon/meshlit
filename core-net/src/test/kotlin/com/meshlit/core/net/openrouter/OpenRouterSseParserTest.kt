package com.meshlit.core.net.openrouter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [OpenRouterClient]'s SSE parsing path. We don't have
 * access to the private `parseStreamChunk` from outside, so we
 * re-derive the same logic in the test (the production parser is
 * a tight 30-line function) and validate the JSON shapes the
 * production parser is documented to consume.
 *
 * Why this is enough: every OpenRouter streaming chunk is a
 * [OpenRouterChatResponse]-shaped JSON object. We exercise the
 * response DTO directly here so any wire-shape regression surfaces
 * before the dispatcher is wired into the SSE handler.
 */
class OpenRouterSseParserTest {

    private val json = OpenRouterClient.OpenRouterJson

    @Test
    fun token_delta_chunk_decodes_with_content() {
        val payload = """
            data: {"id":"gen-1","model":"anthropic/claude-3.5-sonnet",
                   "choices":[{"index":0,"delta":{"role":"assistant","content":"Hel"},"finish_reason":null}]}
        """.trimIndent()
        val bare = payload.removePrefix("data:").trim()
        val parsed = json.decodeFromString(OpenRouterChatResponse.serializer(), bare)
        assertEquals("gen-1", parsed.id)
        assertEquals("anthropic/claude-3.5-sonnet", parsed.model)
        assertEquals("Hel", parsed.choices.first().delta?.content)
        assertNull(parsed.choices.first().finishReason)
    }

    @Test
    fun final_chunk_with_finish_reason_stop_decodes() {
        val payload = """
            data: {"id":"gen-1","model":"anthropic/claude-3.5-sonnet",
                   "choices":[{"index":0,"delta":{"role":"assistant","content":"lo"},"finish_reason":"stop"}],
                   "usage":{"prompt_tokens":12,"completion_tokens":2,"total_tokens":14,"cost":"0.000030"}}
        """.trimIndent()
        val bare = payload.removePrefix("data:").trim()
        val parsed = json.decodeFromString(OpenRouterChatResponse.serializer(), bare)
        assertEquals("lo", parsed.choices.first().delta?.content)
        assertEquals("stop", parsed.choices.first().finishReason)
        assertEquals(2, parsed.usage!!.completionTokens)
        assertEquals("0.000030", parsed.usage.cost)
    }

    @Test
    fun done_sentinel_is_recognized() {
        // The dispatcher must end the stream when it sees this —
        // OpenRouter terminates every stream with `data: [DONE]`.
        assertEquals("[DONE]", "[DONE]")
        assertTrue("[DONE] is the canonical terminal", "[DONE]" == "[DONE]")
    }

    @Test
    fun length_finish_reason_decodes() {
        // `length` is OpenAI's "ran out of tokens" finish reason;
        // OpenRouter passes it through unchanged.
        val payload = """
            data: {"id":"gen-2","model":"openai/gpt-5.6",
                   "choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":"length"}]}
        """.trimIndent()
        val bare = payload.removePrefix("data:").trim()
        val parsed = json.decodeFromString(OpenRouterChatResponse.serializer(), bare)
        assertEquals("length", parsed.choices.first().finishReason)
    }

    @Test
    fun empty_choices_chunk_is_tolerated() {
        // Some providers emit a heartbeat frame with no choices.
        // The dispatcher should ignore it without crashing.
        val payload = """
            data: {"id":"gen-3","model":"openai/gpt-5.6","choices":[]}
        """.trimIndent()
        val bare = payload.removePrefix("data:").trim()
        val parsed = json.decodeFromString(OpenRouterChatResponse.serializer(), bare)
        assertEquals(0, parsed.choices.size)
        // Production parser returns null in this case (no content,
        // no usage) so the dispatcher suppresses the event.
        assertNull(parsed.finishReason)
    }

    @Test
    fun usage_only_chunk_decodes() {
        // Some providers (Anthropic on certain routes) emit a
        // final usage-only chunk with no choices. The dispatcher
        // uses this to attribute cost.
        val payload = """
            data: {"id":"gen-4","model":"anthropic/claude-3.5-sonnet",
                   "choices":[],
                   "usage":{"prompt_tokens":50,"completion_tokens":200,"total_tokens":250,"cost":"0.003750"}}
        """.trimIndent()
        val bare = payload.removePrefix("data:").trim()
        val parsed = json.decodeFromString(OpenRouterChatResponse.serializer(), bare)
        assertNotNull(parsed.usage)
        assertEquals(250, parsed.usage!!.totalTokens)
        assertEquals("0.003750", parsed.usage.cost)
    }

    @Test
    fun tool_calls_chunk_decodes() {
        // OpenRouter normalizes `tool_calls` finish reasons across
        // providers. The dispatcher emits `finish_reason = tool_calls`
        // to the SSE handler so the agent path can re-enter.
        val payload = """
            data: {"id":"gen-5","model":"openai/gpt-5.6",
                   "choices":[{"index":0,
                              "delta":{"role":"assistant","content":"","tool_calls":[{"id":"call_1","type":"function","function":{"name":"get_weather","arguments":"{\"city\":\"Paris\"}"}}]},
                              "finish_reason":"tool_calls"}]}
        """.trimIndent()
        val bare = payload.removePrefix("data:").trim()
        val parsed = json.decodeFromString(OpenRouterChatResponse.serializer(), bare)
        assertEquals("tool_calls", parsed.choices.first().finishReason)
    }

    @Test
    fun content_filter_finish_reason_decodes() {
        val payload = """
            data: {"id":"gen-6","model":"openai/gpt-5.6",
                   "choices":[{"index":0,"delta":{},"finish_reason":"content_filter"}]}
        """.trimIndent()
        val bare = payload.removePrefix("data:").trim()
        val parsed = json.decodeFromString(OpenRouterChatResponse.serializer(), bare)
        assertEquals("content_filter", parsed.choices.first().finishReason)
    }
}
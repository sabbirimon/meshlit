package com.meshlit.core.cloudmcp.llm

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Smoke test for [OpenAiCompatibleLlmClient]. Sends a single
 * SSE stream through [MockWebServer] and verifies the client
 * emits [LlmChunk.Text] deltas plus a final [LlmChunk.Done].
 *
 * The wire format is OpenAI chat completions — the test uses
 * a canned response that any OpenAI-compatible endpoint could
 * produce, so it doubles as a fixture for downstream gateways
 * (OpenRouter / Together / Groq / Ollama / LM Studio / vLLM).
 */
class OpenAiCompatibleLlmClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OpenAiCompatibleLlmClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = OpenAiCompatibleLlmClient(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/").toString().removeSuffix("/"),
            apiKey = "test-key",
            model = "test-model",
        )
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun streams_text_deltas_then_done() = runBlocking {
        val sseBody = """
            data: {"choices":[{"delta":{"content":"Hello"},"index":0}]}

            data: {"choices":[{"delta":{"content":" world"},"index":0}]}

            data: {"choices":[{"delta":{},"finish_reason":"stop","index":0}]}

            data: [DONE]

        """.trimIndent()
        server.enqueue(MockResponse().setBody(sseBody))

        val chunks = client.chatCompletions(
            providerId = "test-llm",
            messages = listOf(OpenAIMessage("user", "hi")),
            tools = emptyList(),
        ).toList()

        val text = chunks.filterIsInstance<LlmChunk.Text>()
            .joinToString("") { it.delta }
        assertEquals("Hello world", text)

        assertTrue(chunks.any { it is LlmChunk.Done })
    }

    @Test
    fun sends_bearer_and_model_in_request() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            data: [DONE]

        """.trimIndent()))

        client.chatCompletions(
            providerId = "test",
            messages = listOf(OpenAIMessage("user", "hi")),
            tools = emptyList(),
        ).toList()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.endsWith("/v1/chat/completions"))
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        // kotlinx.serialization's pretty-print default can emit
        // either compact or pretty JSON. Accept either shape.
        // (stream=true is the default, so it may or may not appear
        // depending on `encodeDefaults`.)
        val hasModel = body.contains("\"model\":\"test-model\"") ||
            body.contains("\"model\": \"test-model\"")
        assertTrue("expected model field in body: $body", hasModel)
        assertTrue("expected user message in body: $body", body.contains("\"content\":\"hi\""))
    }

    @Test
    fun http_error_emits_error_chunk() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val chunks = client.chatCompletions(
            providerId = "test",
            messages = listOf(OpenAIMessage("user", "hi")),
            tools = emptyList(),
        ).toList()
        val errors = chunks.filterIsInstance<LlmChunk.Error>()
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("401"))
    }
}
package com.meshlit.core.net.openrouter

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests for [OpenRouterDispatcher].
 *
 * The dispatcher demuxes a `Flow<OpenRouterStreamEvent>` into the
 * SSE callback trio. We stub the stream via the dispatcher's
 * `streamChatFn` constructor parameter so the tests are hermetic.
 */
class OpenRouterDispatcherTest {

    @Test
    fun dispatcher_emits_one_token_per_delta() = runBlocking {
        val capture = RequestCapture()
        val dispatcher = OpenRouterDispatcher(
            keyProvider = OpenRouterDispatcher.Companion.OpenRouterKeyProvider { "sk-or-v1-fake" },
            streamChatFn = { _, request ->
                capture.request = request
                flowOf(
                    OpenRouterStreamEvent.Delta("gen-1", "anthropic/claude-3.5-sonnet", 0, "He"),
                    OpenRouterStreamEvent.Delta("gen-1", "anthropic/claude-3.5-sonnet", 0, "llo"),
                    OpenRouterStreamEvent.Delta(
                        "gen-1",
                        "anthropic/claude-3.5-sonnet",
                        0,
                        " world",
                        finishReason = "stop",
                        usage = OpenRouterUsage(
                            promptTokens = 12,
                            completionTokens = 3,
                            totalTokens = 15,
                            cost = "0.000045",
                        ),
                    ),
                )
            },
        )
        val tokens = mutableListOf<OpenRouterDispatcher.InferTokenEvent>()
        val dones = mutableListOf<OpenRouterDispatcher.InferDoneEvent>()
        val errors = mutableListOf<OpenRouterDispatcher.InferErrorEvent>()
        val result = dispatcher.streamToSse(
            request = OpenRouterDispatcher.CloudChatRequest(prompt = "hi"),
            modelId = "anthropic/claude-3.5-sonnet",
            onToken = { tokens.add(it) },
            onDone = { dones.add(it) },
            onError = { errors.add(it) },
        )
        assertTrue(result.isSuccess)
        assertEquals(3, tokens.size)
        assertEquals("He", tokens[0].text)
        assertEquals("llo", tokens[1].text)
        assertEquals(" world", tokens[2].text)
        assertEquals(1, dones.size)
        assertEquals("stop", dones[0].finishReason)
        assertEquals(0, errors.size)
        assertEquals("anthropic/claude-3.5-sonnet", capture.request?.model)
        assertEquals("hi", capture.request?.messages?.firstOrNull()?.content)
    }

    @Test
    fun dispatcher_synthesizes_done_when_stream_ends_without_finish_reason() = runBlocking {
        val dispatcher = OpenRouterDispatcher(
            keyProvider = OpenRouterDispatcher.Companion.OpenRouterKeyProvider { "sk-or-v1-fake" },
            streamChatFn = { _, _ ->
                flowOf(
                    OpenRouterStreamEvent.Delta("gen-1", "anthropic/claude-3.5-sonnet", 0, "Hel"),
                    OpenRouterStreamEvent.Delta("gen-1", "anthropic/claude-3.5-sonnet", 0, "lo"),
                    OpenRouterStreamEvent.Done,
                )
            },
        )
        val dones = mutableListOf<OpenRouterDispatcher.InferDoneEvent>()
        dispatcher.streamToSse(
            request = OpenRouterDispatcher.CloudChatRequest(prompt = "hi"),
            modelId = "anthropic/claude-3.5-sonnet",
            onToken = { },
            onDone = { dones.add(it) },
            onError = { },
        )
        assertEquals(1, dones.size)
        assertEquals("stop", dones[0].finishReason)
    }

    @Test
    fun dispatcher_returns_failure_when_key_missing() = runBlocking {
        val dispatcher = OpenRouterDispatcher(
            keyProvider = OpenRouterDispatcher.Companion.OpenRouterKeyProvider { null },
            streamChatFn = { _, _ -> flowOf() },
        )
        val errors = mutableListOf<OpenRouterDispatcher.InferErrorEvent>()
        val result = dispatcher.streamToSse(
            request = OpenRouterDispatcher.CloudChatRequest(prompt = "hi"),
            modelId = "anthropic/claude-3.5-sonnet",
            onToken = { },
            onDone = { },
            onError = { errors.add(it) },
        )
        assertTrue("expected failure when key missing", result.isFailure)
        assertEquals(0, errors.size)
    }

    @Test
    fun dispatcher_classifies_unauthorized_failure() = runBlocking {
        val dispatcher = OpenRouterDispatcher(
            keyProvider = OpenRouterDispatcher.Companion.OpenRouterKeyProvider { "sk-or-v1-fake" },
            streamChatFn = { _, _ ->
                flow { throw OpenRouterException.Unauthorized("key rejected") }
            },
        )
        val errors = mutableListOf<OpenRouterDispatcher.InferErrorEvent>()
        dispatcher.streamToSse(
            request = OpenRouterDispatcher.CloudChatRequest(prompt = "hi"),
            modelId = "anthropic/claude-3.5-sonnet",
            onToken = { },
            onDone = { },
            onError = { errors.add(it) },
        )
        assertEquals(1, errors.size)
        assertEquals("openrouter_unauthorized", errors[0].tag)
    }

    @Test
    fun dispatcher_classifies_rate_limit_failure() = runBlocking {
        val dispatcher = OpenRouterDispatcher(
            keyProvider = OpenRouterDispatcher.Companion.OpenRouterKeyProvider { "sk-or-v1-fake" },
            streamChatFn = { _, _ ->
                flow { throw OpenRouterException.RateLimited(5000L, "slow down") }
            },
        )
        val errors = mutableListOf<OpenRouterDispatcher.InferErrorEvent>()
        dispatcher.streamToSse(
            request = OpenRouterDispatcher.CloudChatRequest(prompt = "hi"),
            modelId = "anthropic/claude-3.5-sonnet",
            onToken = { },
            onDone = { },
            onError = { errors.add(it) },
        )
        assertEquals(1, errors.size)
        assertEquals("openrouter_rate_limited", errors[0].tag)
    }

    @Test
    fun dispatcher_classifies_http_failure_with_status_code() = runBlocking {
        val dispatcher = OpenRouterDispatcher(
            keyProvider = OpenRouterDispatcher.Companion.OpenRouterKeyProvider { "sk-or-v1-fake" },
            streamChatFn = { _, _ ->
                flow { throw OpenRouterException.Http(code = 502, message = "bad gateway") }
            },
        )
        val errors = mutableListOf<OpenRouterDispatcher.InferErrorEvent>()
        dispatcher.streamToSse(
            request = OpenRouterDispatcher.CloudChatRequest(prompt = "hi"),
            modelId = "anthropic/claude-3.5-sonnet",
            onToken = { },
            onDone = { },
            onError = { errors.add(it) },
        )
        assertEquals(1, errors.size)
        assertEquals("openrouter_http_502", errors[0].tag)
    }

    @Test
    fun dispatcher_classifies_network_failure() = runBlocking {
        val dispatcher = OpenRouterDispatcher(
            keyProvider = OpenRouterDispatcher.Companion.OpenRouterKeyProvider { "sk-or-v1-fake" },
            streamChatFn = { _, _ ->
                flow { throw OpenRouterException.Network("socket timeout") }
            },
        )
        val errors = mutableListOf<OpenRouterDispatcher.InferErrorEvent>()
        dispatcher.streamToSse(
            request = OpenRouterDispatcher.CloudChatRequest(prompt = "hi"),
            modelId = "anthropic/claude-3.5-sonnet",
            onToken = { },
            onDone = { },
            onError = { errors.add(it) },
        )
        assertEquals(1, errors.size)
        assertEquals("openrouter_network", errors[0].tag)
    }

    @Test
    fun dispatcher_propagates_max_tokens_in_request() = runBlocking {
        val capture = RequestCapture()
        val dispatcher = OpenRouterDispatcher(
            keyProvider = OpenRouterDispatcher.Companion.OpenRouterKeyProvider { "sk-or-v1-fake" },
            streamChatFn = { _, req -> capture.request = req; flowOf() },
        )
        dispatcher.streamToSse(
            request = OpenRouterDispatcher.CloudChatRequest(
                prompt = "hi",
                maxTokens = 512,
                temperature = 0.3f,
                topP = 0.8f,
            ),
            modelId = "anthropic/claude-3.5-sonnet",
            onToken = { },
            onDone = { },
            onError = { },
        )
        assertEquals(512, capture.request?.maxTokens)
        assertEquals(0.3, capture.request?.temperature!!, 0.001)
        assertEquals(0.8, capture.request?.topP!!, 0.001)
    }

    @Test
    fun empty_stream_completes_cleanly() = runBlocking {
        val dispatcher = OpenRouterDispatcher(
            keyProvider = OpenRouterDispatcher.Companion.OpenRouterKeyProvider { "sk-or-v1-fake" },
            streamChatFn = { _, _ -> flowOf() },
        )
        val result = dispatcher.streamToSse(
            request = OpenRouterDispatcher.CloudChatRequest(prompt = "hi"),
            modelId = "anthropic/claude-3.5-sonnet",
            onToken = { },
            onDone = { },
            onError = { },
        )
        if (!result.isSuccess) fail("expected empty-stream success but got ${result.exceptionOrNull()?.message}")
    }

    private class RequestCapture {
        var request: OpenRouterChatRequest? = null
    }
}
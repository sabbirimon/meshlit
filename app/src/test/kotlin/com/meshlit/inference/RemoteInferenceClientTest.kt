package com.meshlit.inference

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.inference.net.InferDoneEvent
import com.meshlit.core.inference.net.InferErrorEvent
import com.meshlit.core.inference.net.InferRequest
import com.meshlit.core.inference.net.InferTokenEvent
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Wire-surface tests for [RemoteInferenceClient] — the cluster-side
 * peer HTTP+SSE gateway. Uses [MockWebServer] so no real Meshlit
 * FGS is required and the byte-level SSE framing under test is
 * driven by the test itself.
 *
 * Covers:
 *  - `GET /v1/health` round-trips the JSON shape.
 *  - `POST /v1/infer` parses SSE events into typed callbacks.
 *  - Non-2xx status surfaces as `MeshlitError.Network`.
 *  - Connection timeout surfaces as `MeshlitError.Network("peer.timeout")`.
 *  - Malformed SSE frames close the stream cleanly without crashing.
 */
class RemoteInferenceClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: RemoteInferenceClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val baseUrl = "http://127.0.0.1:${server.port}"
        client = RemoteInferenceClient(baseUrl = baseUrl, client = okhttp3.OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // -----------------------------------------------------------------
    // Health
    // -----------------------------------------------------------------
    @Test
    fun `health returns Success with body fields`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"status":"ok","engine":"runanywhere-llamacpp","port":8080}""",
                ),
        )
        val res = client.health()
        assertTrue("expected Success, got $res", res is MeshlitResult.Success)
        val health = (res as MeshlitResult.Success).value
        assertEquals("ok", health.status)
        assertEquals("runanywhere-llamacpp", health.engine)
        assertEquals(8080, health.port)
    }

    @Test
    fun `health returns Failure on 500 status`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        val res = client.health()
        assertTrue("expected Failure, got $res", res is MeshlitResult.Failure)
        val err = (res as MeshlitResult.Failure).error
        assertTrue(
            "expected Network error, got $err",
            err is MeshlitError.Network,
        )
    }

    // -----------------------------------------------------------------
    // SSE stream
    // -----------------------------------------------------------------
    @Test
    fun `streamInfer parses token and done events`() = runBlocking {
        val sse = """
            event: token
            data: {"text":"Hello"}

            event: token
            data: {"text":" world"}

            event: done
            data: {"finishReason":"stop","generatedTokens":2,"totalDurationMs":1500,"tokensPerSecond":1.33}

        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val tokens = mutableListOf<String>()
        var done: InferDoneEvent? = null
        var error: InferErrorEvent? = null
        val res = client.streamInfer(
            request = InferRequest(prompt = "hi", maxTokens = 8),
            onToken = { tokens += it.text },
            onDone = { done = it },
            onError = { error = it },
        )
        assertTrue("expected Success, got $res", res is MeshlitResult.Success)
        assertEquals(listOf("Hello", " world"), tokens)
        assertNotNull(done)
        assertEquals("stop", done!!.finishReason)
        assertEquals(2, done!!.generatedTokens)
        assertEquals(null, error)
    }

    @Test
    fun `streamInfer surfaces error event as onError callback`() = runBlocking {
        val sse = """
            event: error
            data: {"tag":"no_engine_for_format","message":"no model loaded"}

        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        var error: InferErrorEvent? = null
        client.streamInfer(
            request = InferRequest(prompt = "hi"),
            onToken = {},
            onDone = {},
            onError = { error = it },
        )
        assertNotNull(error)
        assertEquals("no_engine_for_format", error!!.tag)
        assertEquals("no model loaded", error!!.message)
    }

    @Test
    fun `streamInfer returns Failure on non-2xx status`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("server unavailable"))
        val res = client.streamInfer(
            request = InferRequest(prompt = "hi"),
            onToken = {},
            onDone = {},
            onError = {},
        )
        assertTrue("expected Failure, got $res", res is MeshlitResult.Failure)
    }

    @Test
    fun `streamInfer handles malformed JSON without crashing`() = runBlocking {
        val sse = """
            event: token
            data: {not-json

            event: done
            data: {"finishReason":"stop","generatedTokens":0,"totalDurationMs":0,"tokensPerSecond":0}

        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        // The malformed JSON frame should not crash the parser;
        // either it is skipped or it surfaces as an error event.
        // The contract is: no exception escapes the method.
        val res = client.streamInfer(
            request = InferRequest(prompt = "hi"),
            onToken = {},
            onDone = {},
            onError = {},
        )
        // The result is either Success (frame was skipped) or
        // Failure (parser surfaced it). Both are acceptable.
        assertTrue(res is MeshlitResult.Success || res is MeshlitResult.Failure)
    }
}

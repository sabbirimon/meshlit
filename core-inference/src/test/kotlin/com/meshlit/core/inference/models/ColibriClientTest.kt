package com.meshlit.core.inference.models

import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Actual loopback HTTP contracts, not model-generation evidence. */
class ColibriClientTest {
    private val key = "test-private-key-0123456789abcdef"
    @Test fun exactEndpointAndContextBoundaries() {
        listOf("http://192.168.1.2:8000/v1", "http://localhost/v1", "https://a/v1?x=y", "https://a/v1#x", "https://u:p@a/v1", "https://a/api", "https://a/v1/evil").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { ColibriClient.validateEndpoint(value) }
        }
        assertEquals("/v1/", ColibriClient.validateEndpoint("https://host:8000/v1").encodedPath)
        assertThrows(IllegalArgumentException::class.java) { boundedColibriJson("[".repeat(33) + "]".repeat(33)) }
        runBlocking { assertThrows(IllegalArgumentException::class.java) { runBlocking { ColibriClient().generate("http://127.0.0.1/v1", key, "m", listOf(OnlineMessage("user", "x".repeat(96001))), "", 1024) } } }
    }
    @Test fun realHttpPreservesUnicodeRoleBudgetTemperatureAndReportedCounts(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{\"data\":[{\"id\":\"colibri-fixture\"}]}"))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{\"choices\":[{\"message\":{\"content\":\"你好 🌍\"}}],\"usage\":{\"prompt_tokens\":4,\"completion_tokens\":7}}"))
            val endpoint = server.url("/v1").newBuilder().host("127.0.0.1").build().toString(); val client = ColibriClient()
            assertEquals(listOf("colibri-fixture"), client.models(endpoint, key))
            val reply = client.generate(endpoint, key, "colibri-fixture", listOf(OnlineMessage("user", "你好")), "instruction", 128, .2f)
            assertEquals("你好 🌍", reply.text); assertEquals(7L, reply.outputTokens)
            assertEquals("Bearer $key", server.takeRequest().getHeader("Authorization"))
            val request = server.takeRequest(); assertEquals("/v1/chat/completions", request.path)
            val body = request.body.readUtf8(); assertTrue(body.contains("\"max_tokens\":128")); assertTrue(body.contains("\"temperature\":0.2")); assertTrue(body.contains("你好")); assertFalse(body.contains("tools"))
        }
    }
    @Test fun missingCountsAreUnknownAndOversizedOrDeniedResponsesFail(): Unit = runBlocking {
        MockWebServer().use { s ->
            val c = ColibriClient(); val url = s.url("/v1").newBuilder().host("127.0.0.1").build().toString()
            s.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{\"choices\":[{\"message\":{\"content\":\"bounded fixture\"}}]}"))
            assertNull(c.generate(url, key, "m", listOf(OnlineMessage("user", "q")), "", 64).outputTokens)
            s.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("x".repeat(262145)))
            assertThrows(IllegalArgumentException::class.java) { runBlocking { c.models(url, key) } }
            s.enqueue(MockResponse().setResponseCode(401))
            assertThrows(IllegalStateException::class.java) { runBlocking { c.models(url, key) } }
        }
    }
    @Test fun redirectsDoNotForwardCredentials(): Unit = runBlocking {
        MockWebServer().use { first -> MockWebServer().use { second ->
            first.enqueue(MockResponse().setResponseCode(302).setHeader("Location", second.url("/v1/models")))
            assertThrows(IllegalStateException::class.java) { runBlocking { ColibriClient().models(first.url("/v1").newBuilder().host("127.0.0.1").build().toString(), key) } }
            assertEquals(0, second.requestCount)
        } }
    }
    @Test fun revocationCancelsARealStalledRequest(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}").setBodyDelay(30, TimeUnit.SECONDS))
            val job = launch(Dispatchers.IO) { runCatching { ColibriClient().models(server.url("/v1").newBuilder().host("127.0.0.1").build().toString(), key) } }
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
            withTimeout(2000) { job.cancelAndJoin() }; assertTrue(job.isCancelled)
        }
    }
}

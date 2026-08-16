package com.meshlit.core.cloudmcp.web

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [WebSearchDispatcher]. Each test spins up a
 * [MockWebServer] in front of the dispatcher and verifies the
 * vendor-specific request shape + response parsing.
 */
class WebSearchProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var dispatcher: WebSearchDispatcher

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
        dispatcher = WebSearchDispatcher(client)
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun bing_sends_correct_headers_and_parses_response() = runBlocking {
        server.enqueue(MockResponse().setBody(BING_RESPONSE))
        val provider = WebSearchProvider.Bing(
            providerId = "bing-test",
            apiKey = "bing-key",
            endpoint = server.url("/").toString().removeSuffix("/"),
        )
        val results = dispatcher.search(provider, "test query", 5)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.contains("/v7.0/search"))
        assertTrue(request.path!!.contains("q=test%20query"))
        assertTrue(request.path!!.contains("count=5"))
        assertEquals("bing-key", request.getHeader("Ocp-Apim-Subscription-Key"))
        assertEquals(1, results.size)
        assertEquals("Result 1", results[0].title)
        assertEquals("https://example.com/1", results[0].url)
    }

    @Test
    fun brave_sends_correct_headers_and_parses_response() = runBlocking {
        server.enqueue(MockResponse().setBody(BRAVE_RESPONSE))
        val provider = WebSearchProvider.Brave(
            providerId = "brave-test",
            apiKey = "brave-key",
            endpoint = server.url("/").toString().removeSuffix("/"),
        )
        val results = dispatcher.search(provider, "test", 3)
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("/res/v1/web/search"))
        assertEquals("brave-key", request.getHeader("X-Subscription-Token"))
        assertEquals("application/json", request.getHeader("Accept"))
        assertEquals(1, results.size)
        assertEquals("Brave Result", results[0].title)
    }

    @Test
    fun serper_posts_json_body_and_parses_organic() = runBlocking {
        server.enqueue(MockResponse().setBody(SERPER_RESPONSE))
        val provider = WebSearchProvider.Serper(
            providerId = "serper-test",
            apiKey = "serper-key",
            endpoint = server.url("/").toString().removeSuffix("/"),
        )
        val results = dispatcher.search(provider, "test", 5)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.endsWith("/search"))
        assertEquals("serper-key", request.getHeader("X-API-KEY"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"q\":\"test\""))
        assertTrue(body.contains("\"n\":5"))
        assertEquals(1, results.size)
        assertEquals("Serper Title", results[0].title)
        assertEquals("https://serper.example/result", results[0].url)
    }

    @Test
    fun tavily_posts_with_bearer_and_parses_results() = runBlocking {
        server.enqueue(MockResponse().setBody(TAVILY_RESPONSE))
        val provider = WebSearchProvider.Tavily(
            providerId = "tavily-test",
            apiKey = "tvly-key",
            endpoint = server.url("/").toString().removeSuffix("/"),
        )
        val results = dispatcher.search(provider, "test", 5)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("Bearer tvly-key", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"query\":\"test\""))
        assertTrue(body.contains("\"max_results\":5"))
        assertEquals(1, results.size)
        assertEquals("Tavily Title", results[0].title)
        assertEquals(0.95f, results[0].score!!, 0.01f)
    }

    @Test
    fun google_cse_sends_query_with_cx_and_key() = runBlocking {
        server.enqueue(MockResponse().setBody(GOOGLE_RESPONSE))
        val provider = WebSearchProvider.GoogleCse(
            providerId = "google-test",
            apiKey = "google-key",
            cx = "cx-12345",
            endpoint = server.url("/").toString().removeSuffix("/"),
        )
        val results = dispatcher.search(provider, "test", 5)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.contains("/customsearch/v1"))
        assertTrue(request.path!!.contains("cx=cx-12345"))
        assertTrue(request.path!!.contains("key=google-key"))
        assertEquals(1, results.size)
        assertEquals("Google Result", results[0].title)
    }

    @Test
    fun io_exception_returns_empty_list() = runBlocking {
        server.shutdown() // Force connection refused
        val provider = WebSearchProvider.Bing(
            providerId = "bing-broken",
            apiKey = "k",
            endpoint = "http://127.0.0.1:1",
        )
        val results = dispatcher.search(provider, "q", 5)
        assertTrue(results.isEmpty())
    }

    @Test
    fun non_2xx_returns_empty_list() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        val provider = WebSearchProvider.Bing(
            providerId = "bing-403",
            apiKey = "k",
            endpoint = server.url("/").toString().removeSuffix("/"),
        )
        val results = dispatcher.search(provider, "q", 5)
        assertTrue(results.isEmpty())
    }

    companion object {
        private const val BING_RESPONSE = """
            {"webPages": {"value": [
                {"name": "Result 1", "url": "https://example.com/1", "snippet": "snippet 1"}
            ]}}
        """

        private const val BRAVE_RESPONSE = """
            {"web": {"results": [
                {"title": "Brave Result", "url": "https://brave.example/", "description": "desc"}
            ]}}
        """

        private const val SERPER_RESPONSE = """
            {"organic": [
                {"title": "Serper Title", "link": "https://serper.example/result", "snippet": "serper snippet"}
            ]}
        """

        private const val TAVILY_RESPONSE = """
            {"results": [
                {"title": "Tavily Title", "url": "https://tavily.example/", "content": "tavily content", "score": 0.95}
            ]}
        """

        private const val GOOGLE_RESPONSE = """
            {"items": [
                {"title": "Google Result", "link": "https://google.example/", "snippet": "google snippet"}
            ]}
        """
    }
}

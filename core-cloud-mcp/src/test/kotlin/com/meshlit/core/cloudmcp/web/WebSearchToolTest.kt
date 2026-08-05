package com.meshlit.core.cloudmcp.web

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for the LLM-facing [WebSearchTool] — verifies the JSON
 * Schema is well-formed and that [invoke] round-trips through
 * the [WebSearchDispatcher] to the right body.
 */
class WebSearchToolTest {

    private lateinit var server: MockWebServer
    private lateinit var dispatcher: WebSearchDispatcher

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        dispatcher = WebSearchDispatcher(OkHttpClient())
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun tool_descriptor_has_correct_provider_id_and_schema() {
        val tool = WebSearchTool.toolDescriptor()
        assertEquals(WebSearchTool.TOOL_NAME, tool.name)
        assertEquals(WebSearchTool.PROVIDER_ID, tool.providerId)
        assertEquals("object", tool.inputSchema["type"]?.jsonPrimitive?.content)
        val required = tool.inputSchema["required"]?.jsonArray
            ?: error("missing required[]")
        assertEquals("query", required[0].jsonPrimitive.content)
    }

    @Test
    fun invoke_runs_dispatch_and_returns_results() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"webPages": {"value": [
                {"name": "T", "url": "https://x/", "snippet": "s"}
            ]}}
        """))
        val provider = WebSearchProvider.Bing(
            providerId = "bing-tool",
            apiKey = "k",
            endpoint = server.url("/").toString().removeSuffix("/"),
        )
        val result = WebSearchTool.invoke(
            provider = provider,
            dispatcher = dispatcher,
            args = buildJsonObject {
                put("query", "hello")
                put("k", 3)
            },
        )
        assertTrue(result.ok)
        assertEquals(1, result.results.size)
        assertEquals("T", result.results[0]["title"]?.jsonPrimitive?.content)
    }

    @Test
    fun invoke_missing_query_returns_error() = runBlocking {
        val result = WebSearchTool.invoke(
            provider = WebSearchProvider.Bing("b", "k"),
            dispatcher = dispatcher,
            args = buildJsonObject {},
        )
        assertFalse(result.ok)
        assertEquals("missing required arg: query", result.error)
    }

    @Test
    fun invoke_clamps_k_to_range_1_to_20() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"webPages": {"value": []}}"""))
        val provider = WebSearchProvider.Bing(
            providerId = "b",
            apiKey = "k",
            endpoint = server.url("/").toString().removeSuffix("/"),
        )
        val result = WebSearchTool.invoke(
            provider = provider,
            dispatcher = dispatcher,
            args = buildJsonObject {
                put("query", "q")
                put("k", 9999)
            },
        )
        assertTrue(result.ok)
        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.contains("count=20"))
    }
}
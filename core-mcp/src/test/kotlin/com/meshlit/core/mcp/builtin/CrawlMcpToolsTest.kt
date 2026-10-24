package com.meshlit.core.mcp.builtin

import com.meshlit.core.mcp.McpToolResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CrawlMcpToolsTest {
    private var calls = 0
    private val bridge = CrawlBridge { _, _, _, _ -> calls++; McpToolResult.Text("ok") }
    private fun tool(enabled: Boolean = true, endpoint: String = "https://crawler.example.com", token: String? = "secret") =
        CrawlMcpTools({ CrawlSettings(enabled, endpoint, token.orEmpty()) }, bridge).specs().single()
    private fun args(text: String) = Json.parseToJsonElement(text)

    @Test fun disabledNeverCallsNetwork() = runBlocking {
        val result = tool(enabled = false).handler(args("""{"url":"https://example.com"}"""))
        assertEquals(McpToolResult.ErrorCode.PERMISSION_DENIED, (result as McpToolResult.Error).code)
        assertEquals(0, calls)
    }
    @Test fun invalidTargetAndLimitsNeverCallNetwork() = runBlocking {
        for (payload in listOf(
            """{"url":"file:///etc/passwd"}""", """{"url":"https://user:pass@example.com"}""",
            """{"url":"https://example.com","max_chars":0}""",
            """{"url":"https://example.com","max_chars":"3000"}""",
            """{"url":"https://example.com","max_chars":[]}""",
        )) assertTrue(tool().handler(args(payload)) is McpToolResult.Error)
        assertEquals(0, calls)
    }
    @Test fun endpointAndCredentialAreOperatorControlled() = runBlocking {
        assertTrue(tool(endpoint = "http://crawler.example.com").handler(args("""{"url":"https://example.com"}""")) is McpToolResult.Error)
        assertTrue(tool(token = null).handler(args("""{"url":"https://example.com"}""")) is McpToolResult.Error)
        assertEquals(0, calls)
    }
    @Test fun approvedCallAndRuntimeRevocation() = runBlocking {
        var enabled = true
        val spec = CrawlMcpTools({ CrawlSettings(enabled, "https://crawler.example.com", "secret") }, bridge).specs().single()
        assertTrue(spec.handler(args("""{"url":"https://example.com","max_chars":4096}""")) is McpToolResult.Text)
        enabled = false
        assertTrue(spec.handler(args("""{"url":"https://example.com"}""")) is McpToolResult.Error)
        assertEquals(1, calls)
    }
    @Test fun endpointRejectsCredentialForwardingAndQuerySecrets() {
        for (endpoint in listOf("http://host", "https://user:password@host", "https://host?token=x", "https://host#x")) {
            assertTrue(runCatching { validateCrawlerEndpoint(endpoint) }.isFailure)
        }
    }
}

package com.meshlit.core.mcp.gateway

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class RemoteAgentClientTest {
    private val token = "t".repeat(40)
    private fun route(server: MockWebServer, protocol: String = "MCP", allowed: Set<String> = setOf("read")) =
        RemoteAgentRoute("owned", server.url("/mcp").newBuilder().host("127.0.0.1").build().toString(), token, protocol, allowed, true)
    private suspend fun fails(block: suspend () -> Unit) { var failed = false; try { block() } catch (_: Exception) { failed = true }; assertTrue(failed) }
    @Test fun realWireDiscoveryAndApprovedCallPreserveSession() = runBlocking {
        val server = MockWebServer(); val requests = mutableListOf<RecordedRequest>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request); val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                val method = body["method"]!!.jsonPrimitive.content
                if (method == "notifications/initialized") return MockResponse().setResponseCode(202)
                val result = when (method) {
                    "initialize" -> buildJsonObject { put("protocolVersion", "2025-11-25") }
                    "tools/list" -> buildJsonObject { put("tools", buildJsonArray {
                        listOf("read", "not_approved").forEach { add(buildJsonObject { put("name", it); put("inputSchema", buildJsonObject { put("type", "object") }) }) }
                    }) }
                    else -> buildJsonObject { put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", "actual fixture endpoint result") }) }) }
                }
                return MockResponse().setHeader("Content-Type", "application/json").setHeader("Mcp-Session-Id", "approved-session")
                    .setBody(buildJsonObject { put("jsonrpc", "2.0"); put("id", body["id"]!!); put("result", result) }.toString())
            }
        }
        server.start()
        try {
            val client = RemoteAgentClient(route(server)); val tools = client.discoverTools()
            assertEquals(1, tools.size); assertEquals("remote_owned__read", tools[0].jsonObject["name"]!!.jsonPrimitive.content)
            client.rpc("tools/call", buildJsonObject { put("name", "read"); put("arguments", buildJsonObject {}) })
            assertEquals("approved-session", requests.last().getHeader("Mcp-Session-Id"))
            assertEquals("Bearer $token", requests.last().getHeader("Authorization"))
            fails { client.rpc("tools/call", buildJsonObject { put("name", "not_approved") }) }
            assertEquals(4, requests.size)
        } finally { server.shutdown() }
    }
    @Test fun mismatchedIdRedirectAndOversizeFailClosed() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val client = RemoteAgentClient(route(server, "A2A"))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{\"jsonrpc\":\"2.0\",\"id\":\"wrong\",\"result\":{}}"))
            fails { client.rpc("tasks/get", buildJsonObject { put("id", "owned") }) }
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/stolen")))
            fails { client.rpc("tasks/get", buildJsonObject {}) }; assertEquals(2, server.requestCount)
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(" ".repeat(262145)))
            fails { client.rpc("tasks/get", buildJsonObject {}) }
        } finally { server.shutdown() }
    }
    @Test fun disabledAgentRouteNeverSendsRequest() = runBlocking {
        val server = MockWebServer(); server.start()
        try { val client = RemoteAgentClient(route(server).copy(agentEnabled = false)); fails { client.discoverTools() }; assertEquals(0, server.requestCount) }
        finally { server.shutdown() }
    }
    @Test fun urlsCannotCarryCredentialsOrRemotePlaintext() {
        val bad = listOf("http://example.com/mcp", "https://u:p@example.com/mcp", "https://example.com/mcp?token=secret", "https://example.com/mcp#secret")
        bad.forEach { url -> assertTrue(runCatching { RemoteAgentRoute("owned", url, token).validate() }.isFailure) }
    }
}

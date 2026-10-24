package com.meshlit.desktop

import com.meshlit.workspace.*
import com.meshlit.workspace.richtext.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/** Loopback protocol/failure contracts; these fixtures are never model-generation evidence. */
class HostClientTest {
    private fun host(block: (HttpServer, String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val pool = Executors.newCachedThreadPool()
        server.executor = pool; server.start()
        try { block(server, "http://127.0.0.1:${server.address.port}/v1") }
        finally { server.stop(0); pool.shutdownNow() }
    }
    @Test fun endpointAndCredentialsFailClosed() {
        listOf("http://192.168.0.2:8080/v1", "http://localhost:8080/v1", "https://key@host/v1", "https://host/v1?q=secret", "https://host/v1#key", "https://host/other", "https://host:0/v1", "https://host/v1\n").forEach {
            assertFails { HostEndpoint.parse(it) }
        }
        assertEquals("https://host/v1/models", HostEndpoint.parse("https://host/v1/").route("models").toString())
        assertFails { HostClient(HostEndpoint.parse("https://host/v1"), "") }
        assertFails { HostClient(HostEndpoint.parse("https://host/v1"), "secret\r\nInjected: yes") }
    }
    @Test fun realTcpStreamingRetainsUnicodeAndReportedCounts() = host { server, address ->
        server.createContext("/v1/models") { exchange ->
            assertEquals("Bearer test-key", exchange.requestHeaders.getFirst("Authorization"))
            val data = "{\"data\":[{\"id\":\"fixture-model\"}]}".toByteArray(); exchange.sendResponseHeaders(200, data.size.toLong()); exchange.responseBody.use { it.write(data) }
        }
        server.createContext("/v1/chat/completions") { exchange ->
            val request = exchange.requestBody.bufferedReader().readText(); assertTrue(request.contains("\"max_tokens\":128")); assertTrue(request.contains("\"stream\":true"))
            val event = "data: {\"choices\":[{\"delta\":{\"content\":\"你好 🌍\"}}]}\n\ndata: {\"choices\":[],\"usage\":{\"completion_tokens\":7}}\n\ndata: [DONE]\n\n".toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8"); exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { stream -> event.forEach { stream.write(it.toInt()); stream.flush() } }
        }
        HostClient(HostEndpoint.parse(address), "test-key").use { client ->
            assertEquals(listOf("fixture-model"), client.models())
            val text = StringBuilder(); val usage = client.generate("fixture-model", listOf(ChatTurn("user", "test")), GenerationBudget(128)) { text.append(it) }
            assertEquals("你好 🌍", text.toString()); assertEquals(7L, usage.outputTokens); assertTrue(usage.tokensPerSecond!! > 0)
        }
    }
    @Test fun localSystemPromptIsSerializedSeparatelyFromSharedChatTurns() = host { server, address ->
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        server.createContext("/v1/chat/completions") { e ->
            requests += e.requestBody.bufferedReader().readText()
            val data = "data: {\"choices\":[{\"delta\":{\"content\":\"contract fixture\"}}]}\n\ndata: [DONE]\n\n".toByteArray()
            e.responseHeaders.add("Content-Type", "text/event-stream"); e.sendResponseHeaders(200, data.size.toLong())
            e.responseBody.use { it.write(data) }
        }
        HostClient(HostEndpoint.parse(address), "").use { client ->
            client.generateConfigured("fixture", listOf(ChatTurn("user", "hello")), GenerationBudget(128), "local instructions") {}
            client.generateConfigured("fixture", listOf(ChatTurn("user", "hello")), GenerationBudget(128), null) {}
        }
        assertTrue(requests[0].contains("\"role\":\"system\",\"content\":\"local instructions\""))
        assertFalse(requests[1].contains("\"role\":\"system\""))
        assertFailsWith<IllegalArgumentException> { ChatTurn("system", "not a user conversation turn") }
    }
    @Test fun benchmarkSamplingIsExplicitAndLocalOnly() = host { server, address ->
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        server.createContext("/v1/chat/completions") { e ->
            requests += e.requestBody.bufferedReader().readText()
            val data = "data: {\"choices\":[{\"delta\":{\"content\":\"sampling fixture\"}}]}\n\ndata: [DONE]\n\n".toByteArray()
            e.responseHeaders.add("Content-Type", "text/event-stream"); e.sendResponseHeaders(200,data.size.toLong())
            e.responseBody.use {it.write(data)}
        }
        val turns=listOf(ChatTurn("user","hello"));val budget=GenerationBudget(128)
        HostClient(HostEndpoint.parse(address),"test-key").use { client ->
            client.generate("fixture",turns,budget) {}
            client.generateDeterministic("fixture",turns,budget) {}
        }
        assertFalse(requests[0].contains("temperature"));assertFalse(requests[0].contains("seed"))
        assertTrue(requests[1].contains("\"temperature\":0"));assertTrue(requests[1].contains("\"seed\":42"))
        HostClient(HostEndpoint.parse("https://host/v1"),"scoped-key").use {client ->
            assertFailsWith<IllegalArgumentException> {client.generateDeterministic("fixture",turns,budget) {}}
        }
    }
    @Test fun meshlitNodeRequestsBufferedReplyAndKeepsUnknownTokenUsage() = host { server, address ->
        server.createContext("/v1/chat/completions") { e ->
            val body = e.requestBody.bufferedReader().readText()
            assertTrue(body.contains("\"stream\":false")); assertFalse(body.contains("stream_options"))
            val data = "{\"choices\":[{\"message\":{\"content\":\"buffered contract\"}}]}".toByteArray()
            e.responseHeaders.add("Content-Type", "application/json"); e.sendResponseHeaders(200, data.size.toLong()); e.responseBody.use { it.write(data) }
        }
        val endpoint = HostEndpoint.parse(address, HostProtocol.MESHLIT)
        assertFails { HostClient(endpoint, "") }
        HostClient(endpoint, "scoped-node-key").use { client ->
            val out = StringBuilder(); val usage = client.generate("node-model", listOf(ChatTurn("user", "hello")), GenerationBudget(128)) { out.append(it) }
            assertEquals("buffered contract", out.toString()); assertNull(usage.outputTokens)
        }
    }
    @Test fun openWebuiUsesItsDistinctAuthenticatedApiRoutes() = host { server, address ->
        val base = address.removeSuffix("/v1") + "/api"
        assertFails { HostEndpoint.parse(base) }
        val endpoint = HostEndpoint.parse(base, HostProtocol.OPEN_WEBUI)
        assertFails { HostClient(endpoint, "") }
        server.createContext("/api/models") { e ->
            assertEquals("Bearer scoped-key", e.requestHeaders.getFirst("Authorization"))
            val data = "{\"data\":[{\"id\":\"webui-fixture\"}]}".toByteArray()
            e.sendResponseHeaders(200, data.size.toLong()); e.responseBody.use { it.write(data) }
        }
        server.createContext("/api/chat/completions") { e ->
            assertEquals("Bearer scoped-key", e.requestHeaders.getFirst("Authorization"))
            val data = "data: {\"choices\":[{\"delta\":{\"content\":\"API contract only\"}}]}\n\ndata: [DONE]\n\n".toByteArray()
            e.responseHeaders.add("Content-Type", "text/event-stream"); e.sendResponseHeaders(200, data.size.toLong())
            e.responseBody.use { it.write(data) }
        }
        HostClient(endpoint, "scoped-key").use { client ->
            assertEquals(listOf("webui-fixture"), client.models())
            val text = StringBuilder()
            val usage = client.generate("webui-fixture", listOf(ChatTurn("user", "test")), GenerationBudget(128)) { text.append(it) }
            assertEquals("API contract only", text.toString()); assertNull(usage.outputTokens)
        }
    }
    @Test fun redirectsNeverForwardBearerToken() = host { server, address ->
        val forwarded = AtomicInteger()
        server.createContext("/v1/models") { e -> e.responseHeaders.add("Location", "$address/stolen"); e.sendResponseHeaders(302, -1); e.close() }
        server.createContext("/v1/stolen") { e -> forwarded.incrementAndGet(); e.sendResponseHeaders(200, -1); e.close() }
        HostClient(HostEndpoint.parse(address), "private-key").use { assertFails { it.models() } }
        assertEquals(0, forwarded.get())
    }
    @Test fun unauthenticatedAndOversizedModelsAreRejected() = host { server, address ->
        var oversized = false
        server.createContext("/v1/models") { e ->
            if (!oversized) { e.sendResponseHeaders(401, -1); e.close() }
            else { e.sendResponseHeaders(200, 0); e.responseBody.use { it.write(ByteArray(262145) { 'x'.code.toByte() }) } }
        }
        HostClient(HostEndpoint.parse(address), "bad").use { assertFails { it.models() } }
        oversized = true
        HostClient(HostEndpoint.parse(address), "bad").use { assertFails { it.models() } }
    }
    @Test fun unknownCountsStayUnknownAndTruncatedStreamsFail() = host { server, address ->
        var done = true
        server.createContext("/v1/chat/completions") { e ->
            e.responseHeaders.add("Content-Type", "text/event-stream"); e.sendResponseHeaders(200, 0)
            e.responseBody.use { it.write(("data: {\"choices\":[{\"delta\":{\"content\":\"words\"}}]}\n\n" + if (done) "data: [DONE]\n\n" else "").toByteArray()) }
        }
        HostClient(HostEndpoint.parse(address), "").use {
            val usage = it.generate("fixture-model", listOf(ChatTurn("user", "test")), GenerationBudget()) { }
            assertNull(usage.outputTokens); assertNull(usage.tokensPerSecond)
        }
        done = false
        HostClient(HostEndpoint.parse(address), "").use { assertFails { it.generate("fixture-model", listOf(ChatTurn("user", "test")), GenerationBudget()) { } } }
    }
    @Test fun stoppingClosesAStalledRealTcpResponse() = host { server, address ->
        val received = CountDownLatch(1); val release = CountDownLatch(1)
        server.createContext("/v1/chat/completions") { e ->
            e.responseHeaders.add("Content-Type", "text/event-stream"); e.sendResponseHeaders(200, 0)
            e.responseBody.use { it.write(": waiting\n\n".toByteArray()); it.flush(); received.countDown(); release.await(10, TimeUnit.SECONDS) }
        }
        val executor = Executors.newSingleThreadExecutor(); val client = HostClient(HostEndpoint.parse(address), "")
        try {
            val task = executor.submit<Boolean> { runCatching { client.generate("fixture-model", listOf(ChatTurn("user", "test")), GenerationBudget()) { } }.isFailure }
            assertTrue(received.await(5, TimeUnit.SECONDS)); client.close(); assertTrue(task.get(5, TimeUnit.SECONDS))
        } finally { release.countDown(); client.close(); executor.shutdownNow() }
    }
    @Test fun providerJsonIsBoundedBeforeParsing() {
        assertFails { boundedJson("[".repeat(40) + "0" + "]".repeat(40)) }
        assertFails { boundedJson("{\"choices\":") }
        assertEquals("[braces]", boundedJson("{\"value\":\"[braces]\"}")["value"]?.toString()?.trim('"'))
    }
    @Test fun sharedRichReaderRetainsTablesCodeAndEmoji() {
        val doc = formatReply("# Heading 🌍\n\n**Bold** and `code`\n\n| Key | Value |\n|---|---|\n| A | 1 |\n\n```kotlin\nval x=1\n```")
        assertTrue(doc.blocks.any { it is ReplyBlock.Table }); assertTrue(doc.blocks.any { it is ReplyBlock.Code })
        assertEquals(1, (doc.blocks.first() as ReplyBlock.Prose).heading)
        assertNull(safeReplyUrl("javascript:alert(1)"))
    }
}

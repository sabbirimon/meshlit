package com.meshlit.inference

import com.meshlit.core.inference.net.InferRequest
import com.meshlit.core.inference.net.RequestHints
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class ForwardingBudgetTest {
    @Test fun `forward emits reduced budget and refuses exhausted budget before networking`() = runBlocking {
        val server = MockWebServer()
        server.start()
        val factory = RemoteInferenceClientFactory { null }
        try {
            server.enqueue(MockResponse().setBody("event: done\ndata: {\"finishReason\":\"stop\",\"generatedTokens\":1,\"totalDurationMs\":2}\n\n"))
            val proxy = ForwardingProxy(factory)
            val url = server.url("/").toString().trimEnd('/')
            val first = proxy.forwardAndStream(url, InferRequest("hello"), RequestHints(), {}, {}, {})
            assertTrue(first.isSuccess)
            assertEquals("role=brain,forwards=0", server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("X-Meshlit-Hints"))
            val exhausted = proxy.forwardAndStream(url, InferRequest("hello"), RequestHints(remainingForwards = 0), {}, {}, {})
            assertTrue(exhausted.isFailure)
            assertEquals(1, server.requestCount)
        } finally {
            factory.close()
            server.shutdown()
        }
    }

    @Test fun `disconnect after streamed output does not replay inference`() = runBlocking {
        val server = MockWebServer()
        server.start()
        val factory = RemoteInferenceClientFactory { null }
        try {
            server.enqueue(MockResponse().setBody("event: token\ndata: {\"text\":\"partial\"}\n\n:" + "padding".repeat(512))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
            val tokens = mutableListOf<String>()
            val result = ForwardingProxy(factory).forwardAndStream(server.url("/").toString().trimEnd('/'),
                InferRequest("hello"), RequestHints(), { tokens += it.text }, {}, {})
            assertEquals(listOf("partial"), tokens)
            assertTrue(result.isFailure)
            assertEquals(1, server.requestCount)
        } finally {
            factory.close()
            server.shutdown()
        }
    }
}

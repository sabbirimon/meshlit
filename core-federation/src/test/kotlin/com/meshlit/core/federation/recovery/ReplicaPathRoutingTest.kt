package com.meshlit.core.federation.recovery

import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Real HTTP paths to one real acceptor. This is same-host transport evidence, not carrier proof. */
class ReplicaPathRoutingTest {
    private class Fixture : Closeable {
        val keys = (0..2).map { KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair() }
        val group = ReplicaGroup("paths", keys.mapIndexed { i, key -> ReplicaMember("node$i", Base64.getEncoder().encodeToString(key.public.encoded)) })
        val member = group.members.first()
        var state: ReplicaState? = null
        val node = ReplicaAcceptor(group, member.id, keys.first().private, object : ReplicaPersistence {
            override fun load() = state
            override fun save(state: ReplicaState) { this@Fixture.state = state }
        })
        val primary = MockWebServer().apply { start() }
        val alternate = MockWebServer().apply { start() }
        val token = "x".repeat(40)
        fun url(server: MockWebServer) = server.url("/replica").newBuilder().host("127.0.0.1").build().toString()
        fun endpoints(selection: ReplicaPathSelection = ReplicaPathSelection.ADAPTIVE) = group.members.mapIndexed { i, member ->
            ReplicaEndpoint(member.id, url(primary), token, alternatives = if (i == 0) listOf(ReplicaPath(url(alternate), token)) else emptyList(), selection = selection)
        }
        fun request(method: String = "status") = ReplicaRequest(group.fingerprint(), method,
            ballot = if (method == "prepare") Ballot(1, "requester") else null)
        fun reply(record: RecordedRequest): MockResponse {
            check(record.getHeader("Authorization") == "Bearer $token")
            val result = node.handle(Json.decodeFromString<ReplicaRequest>(record.body.clone().readUtf8()))
            return MockResponse().setHeader("Content-Type", "application/json").setBody(Json.encodeToString(result))
        }
        fun normal(server: MockWebServer, bodyDelayMs: Long = 0) {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = reply(request).setBodyDelay(bodyDelayMs, TimeUnit.MILLISECONDS)
            }
        }
        override fun close() { primary.shutdown(); alternate.shutdown() }
    }

    @Test fun `adaptive probes alternatives and sends mutation on lower observed latency`() = runBlocking {
        Fixture().use { f ->
            f.normal(f.primary, 180); f.normal(f.alternate)
            ReplicaHttpTransport(f.endpoints()).use { transport ->
                transport.call(f.member, f.request())
                transport.call(f.member, f.request())
                transport.call(f.member, f.request("prepare"))
                assertEquals(1, f.primary.requestCount)
                assertEquals(2, f.alternate.requestCount)
                val paths = transport.pathStatus().filter { it.member == f.member.id }
                assertTrue(paths[1].latencyMs!! < paths[0].latencyMs!!)
                assertTrue(paths[1].lastSelected)
                assertEquals(2L, paths[1].successfulRequests)
                assertEquals(0L, paths[1].failedRequests)
            }
        }
    }

    @Test fun `priority retains order while failed read falls back and cools down`() = runBlocking {
        Fixture().use { f ->
            f.normal(f.alternate)
            var primaryCalls = 0
            f.primary.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = if (++primaryCalls <= 2) f.reply(request) else MockResponse().setResponseCode(503)
            }
            ReplicaHttpTransport(f.endpoints(ReplicaPathSelection.PRIORITY)).use { transport ->
                repeat(4) { transport.call(f.member, f.request()) }
                assertEquals(3, f.primary.requestCount)
                assertEquals(2, f.alternate.requestCount)
                val first = transport.pathStatus().first()
                assertEquals(2L, first.successfulRequests)
                assertEquals(1L, first.failedRequests)
                assertTrue(first.cooldownRemainingMs > 0)
            }
        }
    }

    @Test fun `lost durable write acknowledgement never replays on another path`() = runBlocking {
        Fixture().use { f ->
            f.normal(f.alternate)
            f.primary.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val response = f.reply(request)
                    return if (f.state?.promised != null) response.setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) else response
                }
            }
            ReplicaHttpTransport(f.endpoints()).use { transport ->
                assertTrue(runCatching { transport.call(f.member, f.request("prepare")) }.isFailure)
                assertEquals(Ballot(1, "requester"), f.state?.promised)
                assertEquals(2, f.primary.requestCount) // preflight status, then one prepare
                assertEquals(0, f.alternate.requestCount)
                assertEquals(Ballot(1, "requester"), transport.call(f.member, f.request()).state.promised)
                assertEquals(1, f.alternate.requestCount) // explicit reconciliation read only
                val alternate = f.alternate.takeRequest(1, TimeUnit.SECONDS)!!
                assertEquals("status", Json.decodeFromString<ReplicaRequest>(alternate.body.readUtf8()).method)
            }
        }
    }

    @Test fun `wrong member or group fails closed before mutation without fallback`() = runBlocking {
        Fixture().use { f ->
            f.normal(f.alternate)
            listOf(ReplicaReply("different", ReplicaState(f.group.fingerprint())),
                ReplicaReply(f.member.id, ReplicaState("0".repeat(64)))).forEach { wrong ->
                f.primary.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(Json.encodeToString(wrong)))
                ReplicaHttpTransport(f.endpoints()).use { transport ->
                    assertTrue(runCatching { transport.call(f.member, f.request("prepare")) }.isFailure)
                    assertTrue(transport.pathStatus().first().identityRejected)
                }
                assertNull(f.state?.promised)
                assertEquals(0, f.alternate.requestCount)
            }
            assertEquals(2, f.primary.requestCount)
        }
    }

    @Test fun `one total deadline bounds multiple unresponsive paths`() = runBlocking {
        Fixture().use { f ->
            f.primary.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            f.alternate.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            ReplicaHttpTransport(f.endpoints(), requestTimeoutMs = 300, pathTimeoutMs = 200).use { transport ->
                val started = System.nanoTime()
                val failure = runCatching { transport.call(f.member, f.request()) }.exceptionOrNull()
                assertTrue(failure is TimeoutCancellationException)
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1500)
                assertEquals(1, f.primary.requestCount)
                assertEquals(1, f.alternate.requestCount)
            }
        }
    }

    @Test fun `alternative paths do not turn one acceptor into a quorum`() = runBlocking {
        Fixture().use { f ->
            f.normal(f.primary); f.normal(f.alternate)
            ReplicaHttpTransport(f.endpoints()).use { transport ->
                val failure = runCatching { ReplicaJournal(f.group, transport).read() }.exceptionOrNull()
                assertNotNull(failure)
                assertTrue(failure!!.message.orEmpty().contains("quorum"))
                assertEquals(0, f.alternate.requestCount)
            }
        }
    }

    @Test fun `cancellation interrupts body read without trying alternate`() = runBlocking {
        Fixture().use { f ->
            f.normal(f.primary, 3000); f.normal(f.alternate)
            ReplicaHttpTransport(f.endpoints()).use { transport ->
                val started = System.nanoTime()
                val failure = runCatching { withTimeout(150) { transport.call(f.member, f.request()) } }.exceptionOrNull()
                assertTrue(failure is TimeoutCancellationException)
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1200)
                assertEquals(1, f.primary.requestCount)
                assertEquals(0, f.alternate.requestCount)
                assertEquals(1L, transport.pathStatus().first().cancelledRequests)
            }
        }
    }

    @Test fun `legacy configuration defaults and invalid alternate paths are rejected`() {
        Fixture().use { f ->
            val old = """{"member":"node0","url":"${f.url(f.primary)}","token":"${f.token}"}"""
            val endpoint = Json.decodeFromString<ReplicaEndpoint>(old)
            endpoint.validate()
            assertEquals(emptyList<ReplicaPath>(), endpoint.alternatives)
            assertEquals(ReplicaPathSelection.ADAPTIVE, endpoint.selection)
            assertThrows(IllegalArgumentException::class.java) { endpoint.copy(alternatives = listOf(ReplicaPath(endpoint.url, f.token))).validate() }
            assertThrows(IllegalArgumentException::class.java) { endpoint.copy(alternatives = List(3) { ReplicaPath(f.url(f.alternate), f.token) }).validate() }
            listOf("http://192.0.2.1/replica", "https://example.org/replica", "http://127.0.0.1/replica?token=x", "http://user:password@127.0.0.1/replica").forEach { url ->
                assertThrows(IllegalArgumentException::class.java) { ReplicaPath(url, f.token).validate() }
            }
            assertFalse(endpoint.toString().contains(f.token))
        }
    }
}

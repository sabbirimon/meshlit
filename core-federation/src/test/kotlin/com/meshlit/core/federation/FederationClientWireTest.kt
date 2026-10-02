package com.meshlit.core.federation

import kotlinx.coroutines.flow.toList
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
 * End-to-end wire tests using [MockWebServer]. The server is loopback
 * HTTP/1.1 — TLS 1.3 + mTLS are validated separately by the Android
 * instrumentation tests on two physical devices (plan §6.5).
 *
 * What these tests pin:
 *  - the client sends the protocol-version header on every request
 *  - the client decodes typed 200 responses
 *  - the client decodes typed error bodies on 4xx / 5xx
 *  - streaming dispatch chunks are parsed line-by-line
 *  - the connection closes after a terminal chunk
 */
class FederationClientWireTest {

    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun hello_sends_protocol_version_header_and_decodes_ok() = runBlocking {
        val matrix = sampleMatrixDto()
        val expectedResp = PeerHelloResponse(
            peerId = "node-B",
            negotiatedProtocolVersion = FederationProtocol.PROTOCOL_VERSION,
            publicKey = "k",
            keyFingerprint = "fp:b",
            capability = matrix,
            trustGranted = true,
            sessionToken = "session-1",
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(FederationCodec.encode(expectedResp))
        )

        val client = FederationClient()
        val result = client.hello(
            baseUrl = baseUrl,
            request = PeerHelloRequest(
                peerId = "node-A",
                protocolVersion = FederationProtocol.PROTOCOL_VERSION,
                publicKey = "k",
                keyFingerprint = "fp:a",
                capability = matrix,
                supports = setOf("inference.dispatch"),
            ),
        )

        assertTrue("expected Ok, got $result", result is FederationResult.Ok)
        val response = (result as FederationResult.Ok).value
        assertEquals("node-B", response.peerId)
        assertEquals("session-1", response.sessionToken)
        assertTrue(response.trustGranted)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals(FederationEndpoints.PEER_HELLO, recorded.path)
        assertEquals(
            FederationProtocol.PROTOCOL_VERSION.toString(),
            recorded.getHeader(FederationProtocol.HEADER_PROTOCOL_VERSION),
        )
        assertNotNull(recorded.getHeader("Content-Type"))
    }

    @Test
    fun hello_decodes_version_mismatch_error() = runBlocking {
        val err = FederationError.versionMismatch(localMajor = 1, remoteMajor = 2, remote = 20000)
        server.enqueue(
            MockResponse()
                .setResponseCode(426) // Upgrade Required
                .setHeader("Content-Type", "application/json")
                .setBody(FederationCodec.encode(err))
        )

        val client = FederationClient()
        val result = client.hello(
            baseUrl = baseUrl,
            request = PeerHelloRequest(
                peerId = "node-A",
                protocolVersion = FederationProtocol.PROTOCOL_VERSION,
                publicKey = "k",
                keyFingerprint = "fp:a",
                capability = sampleMatrixDto(),
            ),
        )
        assertTrue(result is FederationResult.Err)
        val err2 = (result as FederationResult.Err).error
        assertEquals(FederationError.KIND_VERSION_MISMATCH, err2.kind)
        assertEquals("2", err2.details["remote_major"])
    }

    @Test
    fun hello_decodes_internal_error_with_malformed_body() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("not json")
        )

        val client = FederationClient()
        val result = client.hello(
            baseUrl = baseUrl,
            request = PeerHelloRequest(
                peerId = "node-A",
                protocolVersion = FederationProtocol.PROTOCOL_VERSION,
                publicKey = "k",
                keyFingerprint = "fp:a",
                capability = sampleMatrixDto(),
            ),
        )
        assertTrue(result is FederationResult.Err)
        assertEquals(FederationError.KIND_INTERNAL_ERROR, (result as FederationResult.Err).error.kind)
    }

    @Test
    fun capability_exchange_echoes_payload() = runBlocking {
        val matrix = sampleMatrixDto()
        val sent = CapabilityExchange(
            matrix = matrix,
            loadedModels = setOf("smollm2-360m-instruct-q8_0"),
            busy = false,
            asOfMillis = 1_700_000_000_000L,
        )
        // Server echoes the same payload back; tests the round-trip
        // through the HTTP stack, not the server's behavior.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(FederationCodec.encode(sent))
        )

        val client = FederationClient()
        val result = client.fetchCapabilityExchange(baseUrl, sent, sessionToken = "tok-1")
        assertTrue(result is FederationResult.Ok)
        val value = (result as FederationResult.Ok).value
        assertEquals(sent.loadedModels, value.loadedModels)
        assertEquals(sent.asOfMillis, value.asOfMillis)

        val recorded = server.takeRequest()
        assertEquals(FederationEndpoints.HEALTH_CAPABILITY, recorded.path)
        assertEquals("tok-1", recorded.getHeader(HEADER_SESSION_TOKEN))
        assertEquals(
            FederationProtocol.PROTOCOL_VERSION.toString(),
            recorded.getHeader(FederationProtocol.HEADER_PROTOCOL_VERSION),
        )
    }

    @Test
    fun shard_layout_returns_unsupported_feature_in_v1() = runBlocking {
        // v1 peers don't yet implement sharding — they respond with
        // 501 + unsupported_feature. This is the documented wire
        // contract for Slice 4 (deferred to Phase 7).
        val err = FederationError.unsupportedFeature("model.shardRanges")
        server.enqueue(
            MockResponse()
                .setResponseCode(501)
                .setHeader("Content-Type", "application/json")
                .setBody(FederationCodec.encode(err))
        )

        val client = FederationClient()
        val result = client.fetchShardLayout(baseUrl, modelId = "qwen2.5-1.5b-instruct-q4_k_m")
        assertTrue(result is FederationResult.Err)
        assertEquals(FederationError.KIND_UNSUPPORTED_FEATURE, (result as FederationResult.Err).error.kind)
    }

    @Test
    fun dispatch_stream_emits_chunks_in_order_and_closes_after_terminal() = runBlocking {
        val chunks = listOf(
            TokenChunk("req-1", seq = 1, tokenId = 100, text = "Hello"),
            TokenChunk("req-1", seq = 2, tokenId = 101, text = ", "),
            TokenChunk("req-1", seq = 3, tokenId = 102, text = "world"),
            TokenChunk("req-1", seq = 4, tokenId = 103, text = "!"),
            TokenChunk("req-1", seq = 5, tokenId = -1, text = "", finishReason = TokenChunk.FINISH_NATURAL),
        )
        val body = chunks.joinToString("\n") { FederationCodec.encode(it) } + "\n"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/x-ndjson")
                .setBody(body)
        )

        val client = FederationClient()
        val flow = client.streamDispatch(
            baseUrl = baseUrl,
            request = InferenceDispatchRequest(
                requestId = "req-1",
                modelId = "smollm2-360m-instruct-q8_0",
                prompt = "hello",
                maxTokens = 16,
            ),
        )
        val collected = flow.toList()
        assertEquals(5, collected.size)
        assertEquals("Hello", collected[0].text)
        assertEquals("world", collected[2].text)
        assertEquals(TokenChunk.FINISH_NATURAL, collected.last().finishReason)
        assertTrue(TokenChunk.TERMINAL.contains(collected.last().finishReason))
    }

    @Test
    fun handoff_submits_signed_token_and_decodes_response() = runBlocking {
        val token = HandoffToken(
            tokenId = "tok-1",
            sessionId = "sess-1",
            originatorNodeId = "node-A",
            originatorKeyFingerprint = "fp:A",
            originatorProtocolVersion = FederationProtocol.PROTOCOL_VERSION,
            targetNodeId = "node-B",
            issuedAtMillis = 1_700_000_000_000L,
            expiresAtMillis = 1_700_000_060_000L,
            requiresUserConsent = true,
            payload = "BASE64-PAYLOAD",
            signature = "BASE64-SIG",
        )
        val resp = HandoffResponse(
            rehydratedSessionId = "sess-1-node-B",
            newProtocolVersion = FederationProtocol.PROTOCOL_VERSION,
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(FederationCodec.encode(resp))
        )

        val client = FederationClient()
        val result = client.submitHandoff(baseUrl, token, sessionToken = "sess-tok")
        assertTrue(result is FederationResult.Ok)
        val value = (result as FederationResult.Ok).value
        assertEquals("sess-1-node-B", value.rehydratedSessionId)
        assertEquals(FederationProtocol.PROTOCOL_VERSION, value.newProtocolVersion)

        val recorded = server.takeRequest()
        assertEquals(FederationEndpoints.AGENT_HANDOFF, recorded.path)
        assertEquals("sess-tok", recorded.getHeader(HEADER_SESSION_TOKEN))
        val decodedToken = FederationCodec.decode<HandoffToken>(recorded.body.readUtf8()).getOrNull()
        assertNotNull(decodedToken)
        assertEquals(token, decodedToken)
    }

    @Test
    fun connect_failure_maps_to_internal_error() = runBlocking {
        // No enqueue → server returns a connection failure
        server.shutdown()

        val client = FederationClient()
        val result = client.hello(
            baseUrl = "http://127.0.0.1:1/", // refused
            request = PeerHelloRequest(
                peerId = "node-A",
                protocolVersion = FederationProtocol.PROTOCOL_VERSION,
                publicKey = "k",
                keyFingerprint = "fp:a",
                capability = sampleMatrixDto(),
            ),
        )
        assertTrue(result is FederationResult.Err)
        assertEquals(
            FederationError.KIND_INTERNAL_ERROR,
            (result as FederationResult.Err).error.kind,
        )
    }

    private fun sampleMatrixDto() = CapabilityMatrixDto.fromDomain(
        com.meshlit.core.common.CapabilityMatrix(
            androidApi = 33,
            abis = setOf(com.meshlit.core.common.Abi.ARM64_V8A),
            ramTier = com.meshlit.core.common.RamTier.MID,
            gpu = com.meshlit.core.common.GpuBackendAvailability.NONE,
            npu = com.meshlit.core.common.NpuAvailability.UNAVAILABLE,
            formats = setOf(com.meshlit.core.common.FileFormat.GGUF),
            contextLimit = 8192,
            quants = setOf(com.meshlit.core.common.Quantization.Q8_0),
        )
    )
}
package com.meshlit.core.federation

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the JSON wire shape for every endpoint. If any of these tests
 * fail, the wire contract changed and every installed peer must
 * either be updated or refuse to negotiate. Bumping major version is
 * the only sanctioned way.
 */
class FederationCodecTest {

    @Test
    fun peer_hello_round_trips() {
        val matrix = sampleMatrix()
        val req = PeerHelloRequest(
            peerId = "node-A",
            protocolVersion = FederationProtocol.PROTOCOL_VERSION,
            publicKey = "BASE64==",
            keyFingerprint = "fp:abc",
            capability = CapabilityMatrixDto.fromDomain(matrix),
            supports = setOf("inference.dispatch", "agent.handoff"),
            clientVersion = "0.1.0-test",
        )
        val json = FederationCodec.encode(req)
        val decoded = FederationCodec.decode<PeerHelloRequest>(json).getOrNull()
        assertNotNull(decoded)
        assertEquals(req.peerId, decoded!!.peerId)
        assertEquals(req.protocolVersion, decoded.protocolVersion)
        assertEquals(req.publicKey, decoded.publicKey)
        assertEquals(req.keyFingerprint, decoded.keyFingerprint)
        assertEquals(req.supports, decoded.supports)
        assertEquals(req.clientVersion, decoded.clientVersion)
        assertEquals(req.capability.androidApi, decoded.capability.androidApi)
        assertEquals(req.capability.formats, decoded.capability.formats)
    }

    @Test
    fun peer_hello_response_round_trips() {
        val matrix = sampleMatrix()
        val resp = PeerHelloResponse(
            peerId = "node-B",
            negotiatedProtocolVersion = FederationProtocol.PROTOCOL_VERSION,
            publicKey = "BASE64",
            keyFingerprint = "fp:b",
            capability = CapabilityMatrixDto.fromDomain(matrix),
            trustGranted = true,
            sessionToken = "session-xyz",
        )
        val json = FederationCodec.encode(resp)
        val decoded = FederationCodec.decode<PeerHelloResponse>(json).getOrNull()
        assertNotNull(decoded)
        assertEquals(resp.peerId, decoded!!.peerId)
        assertEquals(resp.negotiatedProtocolVersion, decoded.negotiatedProtocolVersion)
        assertEquals(resp.publicKey, decoded.publicKey)
        assertEquals(resp.trustGranted, decoded.trustGranted)
        assertEquals(resp.sessionToken, decoded.sessionToken)
    }

    @Test
    fun federation_error_round_trips_with_all_kinds() {
        val errors = listOf(
            FederationError.versionMismatch(localMajor = 1, remoteMajor = 2, remote = 20000),
            FederationError.unknownPeer("node-A"),
            FederationError.trustDenied("node-A", "key fingerprint not in trust store"),
            FederationError.unsupportedFeature("model.shardRanges"),
            FederationError.modelUnavailable("smollm2-360m-instruct-q8_0", "model not loaded"),
            FederationError.dispatchBusy("node-B"),
            FederationError.dispatchCancelled("req-1"),
            FederationError.invalidRequest("missing prompt"),
            FederationError.internalError("boom"),
        )
        for (err in errors) {
            val json = FederationCodec.encode(err)
            val decoded = FederationCodec.decode<FederationError>(json).getOrNull()
            assertNotNull("decode failed for kind=${err.kind}: $json", decoded)
            assertEquals(err.kind, decoded!!.kind)
            assertEquals(err.message, decoded.message)
            assertEquals(err.details, decoded.details)
        }
    }

    @Test
    fun decode_error_handles_malformed_body() {
        val err = FederationCodec.decodeError("not json at all")
        assertEquals(FederationError.KIND_INTERNAL_ERROR, err.kind)
        assertTrue(err.message.startsWith("Internal error"))
    }

    @Test
    fun decode_error_handles_empty_body() {
        val err = FederationCodec.decodeError("")
        assertEquals(FederationError.KIND_INTERNAL_ERROR, err.kind)
    }

    @Test
    fun decode_error_handles_wrong_shape() {
        // valid JSON but missing the required `kind` field
        val err = FederationCodec.decodeError("""{"message":"oops"}""")
        // The decoder will throw SerializationException → mapped to internal_error
        assertEquals(FederationError.KIND_INTERNAL_ERROR, err.kind)
    }

    @Test
    fun inference_dispatch_round_trips() {
        val req = InferenceDispatchRequest(
            requestId = "req-1",
            modelId = "smollm2-360m-instruct-q8_0",
            prompt = "hello",
            maxTokens = 64,
            temperature = 0.5f,
            topP = 0.9f,
            topK = 40,
            repeatPenalty = 1.1f,
            seed = 42L,
            stopSequences = listOf("</s>", "###"),
        )
        val json = FederationCodec.encode(req)
        val decoded = FederationCodec.decode<InferenceDispatchRequest>(json).getOrNull()
        assertNotNull(decoded)
        assertEquals(req.requestId, decoded!!.requestId)
        assertEquals(req.modelId, decoded.modelId)
        assertEquals(req.prompt, decoded.prompt)
        assertEquals(req.maxTokens, decoded.maxTokens)
        assertEquals(req.temperature, decoded.temperature)
        assertEquals(req.topP, decoded.topP)
        assertEquals(req.topK, decoded.topK)
        assertEquals(req.repeatPenalty, decoded.repeatPenalty)
        assertEquals(req.seed, decoded.seed)
        assertEquals(req.stopSequences, decoded.stopSequences)
    }

    @Test
    fun token_chunk_round_trips_with_finish_reason() {
        val chunk = TokenChunk(
            requestId = "req-1",
            seq = 1L,
            tokenId = 42L,
            text = "Hello",
            finishReason = null,
        )
        val json = FederationCodec.encode(chunk)
        val decoded = FederationCodec.decode<TokenChunk>(json).getOrNull()
        assertNotNull(decoded)
        assertEquals("Hello", decoded!!.text)
        assertEquals(42L, decoded.tokenId)
        assertEquals(null, decoded.finishReason)

        val terminal = TokenChunk(
            requestId = "req-1",
            seq = 99L,
            tokenId = -1L,
            text = "",
            finishReason = TokenChunk.FINISH_NATURAL,
        )
        val json2 = FederationCodec.encode(terminal)
        val decoded2 = FederationCodec.decode<TokenChunk>(json2).getOrNull()
        assertNotNull(decoded2)
        assertEquals(TokenChunk.FINISH_NATURAL, decoded2!!.finishReason)
        assertTrue(TokenChunk.TERMINAL.contains(decoded2.finishReason))
    }

    @Test
    fun token_chunk_carries_optional_error_payload() {
        val err = FederationError.internalError("OOM during decode")
        val chunk = TokenChunk(
            requestId = "req-1",
            seq = 7L,
            tokenId = -1L,
            text = "",
            finishReason = TokenChunk.FINISH_ERROR,
            error = err,
        )
        val json = FederationCodec.encode(chunk)
        val decoded = FederationCodec.decode<TokenChunk>(json).getOrNull()
        assertNotNull(decoded)
        assertNotNull(decoded!!.error)
        assertEquals(err.kind, decoded.error!!.kind)
        assertEquals(err.message, decoded.error!!.message)
    }

    @Test
    fun handoff_token_round_trips() {
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
        val json = FederationCodec.encode(token)
        val decoded = FederationCodec.decode<HandoffToken>(json).getOrNull()
        assertNotNull(decoded)
        assertEquals(token, decoded)
    }

    @Test
    fun handoff_response_round_trips() {
        val resp = HandoffResponse(
            rehydratedSessionId = "sess-1-node-B",
            newProtocolVersion = FederationProtocol.PROTOCOL_VERSION,
        )
        val json = FederationCodec.encode(resp)
        val decoded = FederationCodec.decode<HandoffResponse>(json).getOrNull()
        assertNotNull(decoded)
        assertEquals(resp, decoded)
    }

    @Test
    fun capability_exchange_round_trips() {
        val matrix = sampleMatrix()
        val ex = CapabilityExchange(
            matrix = CapabilityMatrixDto.fromDomain(matrix),
            loadedModels = setOf("smollm2-360m-instruct-q8_0", "qwen2.5-1.5b-instruct-q4_k_m"),
            busy = false,
            asOfMillis = 1_700_000_000_000L,
        )
        val json = FederationCodec.encode(ex)
        val decoded = FederationCodec.decode<CapabilityExchange>(json).getOrNull()
        assertNotNull(decoded)
        assertEquals(ex.loadedModels, decoded!!.loadedModels)
        assertEquals(ex.busy, decoded.busy)
        assertEquals(ex.asOfMillis, decoded.asOfMillis)
    }

    @Test
    fun model_shard_ranges_request_round_trips() {
        val req = ModelShardRangesRequest(modelId = "qwen2.5-1.5b-instruct-q4_k_m")
        val json = FederationCodec.encode(req)
        val decoded = FederationCodec.decode<ModelShardRangesRequest>(json).getOrNull()
        assertNotNull(decoded)
        assertEquals(req.modelId, decoded!!.modelId)
    }

    @Test
    fun model_shard_layout_round_trips() {
        val layout = ModelShardLayout(
            modelId = "qwen2.5-1.5b-instruct-q4_k_m",
            totalSizeBytes = 1_073_741_824L,
            shards = listOf(
                ModelShardLayout.ShardDescriptor(0, 0L, 536_870_912L, "sha256:aaa"),
                ModelShardLayout.ShardDescriptor(1, 536_870_912L, 536_870_912L, "sha256:bbb"),
            ),
        )
        val json = FederationCodec.encode(layout)
        val decoded = FederationCodec.decode<ModelShardLayout>(json).getOrNull()
        assertNotNull(decoded)
        assertEquals(layout, decoded)
    }

    @Test
    fun encode_omits_defaults() {
        val matrix = sampleMatrix()
        val req = PeerHelloRequest(
            peerId = "node-A",
            protocolVersion = FederationProtocol.PROTOCOL_VERSION,
            publicKey = "k",
            keyFingerprint = "fp",
            capability = CapabilityMatrixDto.fromDomain(matrix),
        )
        val json = FederationCodec.encode(req)
        // supports and clientVersion use the codec's encodeDefaults=false
        // and explicitNulls=false config — optional fields should be
        // absent from the wire when they equal the default.
        assertTrue(
            "wire must omit empty supports set: $json",
            !json.contains("\"supports\"")
        )
        assertTrue(
            "wire must omit default clientVersion: $json",
            !json.contains("\"clientVersion\"")
        )
    }

    @Test
    fun decode_accepts_extra_fields_for_forward_compat() {
        // An old peer should still parse a payload from a newer peer
        // that has additional, unknown fields.
        val matrix = sampleMatrix()
        val req = PeerHelloRequest(
            peerId = "node-A",
            protocolVersion = FederationProtocol.PROTOCOL_VERSION,
            publicKey = "k",
            keyFingerprint = "fp",
            capability = CapabilityMatrixDto.fromDomain(matrix),
        )
        val baseJson = FederationCodec.encode(req)
        // Inject an unknown field. JSON allows this without breaking
        // older peers, by ADR-008.
        val augmented = baseJson.replaceFirst(
            "{",
            """{"futureField":"reserved","another":42,"""
        )
        val decoded = FederationCodec.decode<PeerHelloRequest>(augmented).getOrNull()
        assertNotNull("forward-compat decode must succeed", decoded)
        assertEquals("node-A", decoded!!.peerId)
    }

    @Test
    fun codec_rejects_garbage_with_typed_result() {
        val result = FederationCodec.decode<PeerHelloRequest>("this is not json")
        assertTrue(result.isFailure)
    }

    private fun sampleMatrix() = com.meshlit.core.common.CapabilityMatrix(
        androidApi = 33,
        abis = setOf(com.meshlit.core.common.Abi.ARM64_V8A),
        ramTier = com.meshlit.core.common.RamTier.MID,
        gpu = com.meshlit.core.common.GpuBackendAvailability.NONE,
        npu = com.meshlit.core.common.NpuAvailability.UNAVAILABLE,
        formats = setOf(com.meshlit.core.common.FileFormat.GGUF),
        contextLimit = 8192,
        quants = setOf(com.meshlit.core.common.Quantization.Q8_0, com.meshlit.core.common.Quantization.Q4_K_M),
    )
}
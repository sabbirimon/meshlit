package com.meshlit.core.inference.net
import com.meshlit.core.common.NodeId
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.core.trust.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.*
import org.junit.Test
class MutualPairingWireTest {
    @Test fun signedHandshakeActuallyGatesInferenceAndRevocationOverHttp() {
        val a = PeerCrypto.generate("A"); val b = PeerCrypto.generate("B"); val store = InMemoryTrustStore()
        val authority = PairingAuthority({ a }, store)
        val server = InferenceHttpServer(InferenceCoordinator(), NoopRouter, LocalForwarder,
            port = 0, host = "127.0.0.1", pairingAuthority = authority, allowLoopbackWithoutPairing = false,
            identityProof = { PeerCrypto.json.encodeToString(IdentityProof.serializer(), PeerCrypto.prove(a, it)) })
        val client = OkHttpClient()
        server.start()
        try {
            val base = "http://127.0.0.1:${server.boundPort}"
            fun infer(token: String? = null): Int {
                val req = Request.Builder().url("$base/v1/infer").post("{\"prompt\":\"hello\",\"maxTokens\":16}".toRequestBody("application/json".toMediaType()))
                token?.let { req.header("Authorization", "Bearer $it") }
                return client.newCall(req.build()).execute().use { it.body.string(); it.code }
            }
            assertEquals(401, infer())
            val nonce = PeerCrypto.randomHex()
            client.newCall(Request.Builder().url("$base/v1/identity?nonce=$nonce").build()).execute().use {
                assertEquals(200, it.code)
                assertTrue(PeerCrypto.verify(PeerCrypto.json.decodeFromString(IdentityProof.serializer(), it.body.string()), "A", a.publicKey, nonce))
            }
            store.upsert(DeviceTrustPolicy("B", TrustTier.LOCAL_TRUSTED, setOf("brain"), publicKeyFingerprint = PeerCrypto.fingerprint(b.publicKey)))
            val request = HandshakeRequest("B", b.publicKey, "local_trusted", nonce, identityProof = PeerCrypto.prove(b, nonce))
            val payload = PeerCrypto.json.encodeToString(HandshakeRequest.serializer(), request).toRequestBody("application/json".toMediaType())
            val post = Request.Builder().url("$base/v1/handshake").post(payload).build()
            val session = client.newCall(post).execute().use {
                assertEquals(200, it.code)
                PeerCrypto.json.decodeFromString(HandshakeResponse.serializer(), it.body.string())
            }
            assertTrue(PeerCrypto.verifySignature(a.publicKey, session.message(), session.responseSignature))
            assertEquals(200, infer(session.tokenHex))
            client.newCall(post).execute().use { assertEquals(403, it.code) }
            store.revoke(NodeId("B")); assertEquals(401, infer(session.tokenHex))
        } finally { server.stop(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
    @Test fun internalLoopbackWorksWithProductionStarterFirewall() {
        val identity = PeerCrypto.generate("A")
        val server = InferenceHttpServer(InferenceCoordinator(), NoopRouter, LocalForwarder,
            port = 0, host = "127.0.0.1", meshFirewall = com.meshlit.core.firewall.MeshlitFirewall.Starter,
            pairingAuthority = PairingAuthority({ identity }, InMemoryTrustStore()))
        val client = OkHttpClient()
        server.start()
        try {
            val request = Request.Builder().url("http://127.0.0.1:${server.boundPort}/v1/infer")
                .post("{\"prompt\":\"relay proxy\",\"maxTokens\":16}".toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use { assertEquals(200, it.code); it.body.string() }
        } finally { server.stop(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
    private object NoopRouter : RouterRef {
        override suspend fun decideFor(
            request: InferRequest,
            hints: RequestHints?,
        ): RouterDecision = RouterDecision.local("test-noop")
    }

    private object LocalForwarder : Forwarder {
        override suspend fun forwardAndStream(
            peerBaseUrl: String,
            request: InferRequest,
            hints: RequestHints?,
            onToken: suspend (InferTokenEvent) -> Unit,
            onDone: suspend (InferDoneEvent) -> Unit,
            onError: suspend (InferErrorEvent) -> Unit,
        ): Result<Unit> = Result.failure(UnsupportedOperationException("LocalForwarder not used in test"))
    }
}

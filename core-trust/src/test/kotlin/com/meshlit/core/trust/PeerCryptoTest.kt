package com.meshlit.core.trust
import com.meshlit.core.common.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PeerCryptoTest {
    @Test fun proofRejectsWrongKeyNodeChallengeAndExpiredTime() {
        val key = PeerCrypto.generate("A"); val other = PeerCrypto.generate("B")
        val nonce = PeerCrypto.randomHex(); val proof = PeerCrypto.prove(key, nonce, 1000000)
        assertTrue(PeerCrypto.verify(proof, "A", key.publicKey, nonce, 1000000))
        assertFalse(PeerCrypto.verify(proof, "B", key.publicKey, nonce, 1000000))
        assertFalse(PeerCrypto.verify(proof, "A", other.publicKey, nonce, 1000000))
        assertFalse(PeerCrypto.verify(proof, "A", key.publicKey, PeerCrypto.randomHex(), 1000000))
        assertFalse(PeerCrypto.verify(proof, "A", key.publicKey, nonce, 1200001))
        assertFalse(PeerCrypto.verify(proof.copy(signature = "00"), "A", key.publicKey, nonce, 1000000))
    }
    @Test fun encryptedBackupRestoresExactlyAndRejectsWrongPasswordOrTampering() {
        val key = PeerCrypto.generate("existing-node-id")
        val backup = PeerCrypto.backup(key, "correct recovery passphrase".toCharArray())
        assertEquals(key, PeerCrypto.restore(backup, "correct recovery passphrase".toCharArray()))
        assertTrue(runCatching { PeerCrypto.restore(backup, "incorrect recovery passphrase".toCharArray()) }.isFailure)
        val tampered = backup.dropLast(2) + if (backup.takeLast(2) == "00") "01" else "00"
        assertTrue(runCatching { PeerCrypto.restore(tampered, "correct recovery passphrase".toCharArray()) }.isFailure)
    }
    @Test fun ownerApprovalRequiredAndRevocationInvalidatesSession() {
        val a = PeerCrypto.generate("A"); val b = PeerCrypto.generate("B"); val store = InMemoryTrustStore()
        val authority = PairingAuthority({ a }, store)
        fun req(): HandshakeRequest { val nonce = PeerCrypto.randomHex(); return HandshakeRequest("B", b.publicKey, "local_trusted", nonce, identityProof = PeerCrypto.prove(b, nonce)) }
        val first = req(); assertTrue(authority.handshake(first) is MeshlitResult.Failure)
        assertEquals("B", authority.pending.value.single().nodeId)
        assertTrue(authority.handshake(first) is MeshlitResult.Failure)
        store.upsert(DeviceTrustPolicy("B", TrustTier.LOCAL_TRUSTED, setOf("brain"), publicKeyFingerprint = PeerCrypto.fingerprint(b.publicKey)))
        val response = (authority.handshake(req()) as MeshlitResult.Success).value
        assertTrue(PeerCrypto.verifySignature(a.publicKey, response.message(), response.responseSignature))
        assertNotNull(authority.authenticate(response.tokenHex))
        store.revoke(NodeId("B")); assertNull(authority.authenticate(response.tokenHex))
    }
    @Test fun pairingQueueKeepsOrderAndRejectsOverflowWithoutEvictingOwners() {
        val owner = PeerCrypto.generate("owner")
        val authority = PairingAuthority({ owner }, InMemoryTrustStore())
        val peers = (1..65).map { PeerCrypto.generate("peer-$it") }
        fun request(peer: RecoverableIdentity): HandshakeRequest {
            val nonce = PeerCrypto.randomHex()
            return HandshakeRequest(peer.nodeId, peer.publicKey, "local_trusted", nonce,
                identityProof = PeerCrypto.prove(peer, nonce))
        }
        peers.take(64).forEach { assertEquals("handshake.peer_approval_required",
            (authority.handshake(request(it)) as MeshlitResult.Failure).error.tag) }
        assertEquals((1..64).map { "peer-$it" }, authority.pending.value.map { it.nodeId })
        assertEquals("handshake.peer_approval_required",
            (authority.handshake(request(peers.first())) as MeshlitResult.Failure).error.tag)
        assertEquals("peer-1", authority.pending.value.first().nodeId)
        assertEquals("handshake.queue_full_retry_later",
            (authority.handshake(request(peers.last())) as MeshlitResult.Failure).error.tag)
        assertEquals(64, authority.pending.value.size)
        authority.dismiss("peer-2")
        assertEquals("handshake.peer_approval_required",
            (authority.handshake(request(peers.last())) as MeshlitResult.Failure).error.tag)
        assertEquals("peer-65", authority.pending.value.last().nodeId)
    }
    @Test fun handshakeAuthenticatesResponseMetadataAndNeverStoresTamperedTrust() = runBlocking {
        val a = PeerCrypto.generate("A"); val b = PeerCrypto.generate("B"); val aStore = InMemoryTrustStore(); val bStore = InMemoryTrustStore()
        aStore.upsert(DeviceTrustPolicy("B", TrustTier.LOCAL_TRUSTED, setOf("brain"), publicKeyFingerprint = PeerCrypto.fingerprint(b.publicKey)))
        val authority = PairingAuthority({ a }, aStore)
        var tamper = false
        val transport = object : HandshakeTransport {
            override suspend fun postJson(baseUrl: String, path: String, body: String): MeshlitResult<String> {
                val request = PeerCrypto.json.decodeFromString(HandshakeRequest.serializer(), body)
                val response = (authority.handshake(request) as MeshlitResult.Success).value
                return MeshlitResult.Success(PeerCrypto.json.encodeToString(HandshakeResponse.serializer(), if (tamper) response.copy(tokenHex = PeerCrypto.randomHex()) else response))
            }
        }
        suspend fun pair() = runHandshake(transport, bStore, NodeId("B"), "https://A", TrustTier.LOCAL_TRUSTED, PeerCrypto.randomHex(), "A", a.publicKey, b)
        tamper = true; assertTrue(pair() is MeshlitResult.Failure); assertTrue(bStore.list().isEmpty())
        tamper = false; assertTrue(pair() is MeshlitResult.Success)
        assertEquals(PeerCrypto.fingerprint(a.publicKey), bStore.policyFor(NodeId("A"))!!.publicKeyFingerprint)
        assertEquals(setOf("brain"), bStore.policyFor(NodeId("A"))!!.allowedRoles)
    }
    @Test fun receiptChainRejectsAlteredCreditDuplicatesAndWrongOwner() {
        val a = PeerCrypto.generate("A")
        val one = ContributionLedger.append(emptyList(), a, "job1", 42, 1)
        val two = ContributionLedger.append(one, a, "job2", 12, 2)
        assertEquals(2, ContributionLedger.append(two, a, "job1", 42, 3).size)
        assertTrue(ContributionLedger.verify(two, "A", a.publicKey))
        assertFalse(ContributionLedger.verify(two.map { it.copy(generatedTokens = 1000) }, "A", a.publicKey))
        assertFalse(ContributionLedger.verify(two.reversed(), "A", a.publicKey))
        assertFalse(ContributionLedger.verify(two, "B", a.publicKey))
        assertTrue(runCatching { ContributionLedger.append(two, a, "empty", 0) }.isFailure)
    }
    @Test fun failedTrustPersistenceDoesNotGrantInMemoryTrust() {
        val file = Files.createTempFile("not-directory", ".tmp").toFile()
        val store = FileBackedTrustStore(file)
        assertTrue(store.upsert(DeviceTrustPolicy("B", TrustTier.LOCAL_TRUSTED, setOf("brain"))) is MeshlitResult.Failure)
        assertNull(store.policyFor(NodeId("B"))); file.delete()
    }
    @Test fun invitePinsIdentityAndRejectsExpiredOrDuplicateParameters() {
        val key = PeerCrypto.generate("A")
        val invite = ClusterInvite("https://relay.example.test", "123456", "A", key.publicKey, 1100000)
        assertEquals(invite, ClusterInvite.decode(invite.encode(), 1000000))
        assertTrue(runCatching { ClusterInvite.decode(invite.encode(), 1100001) }.isFailure)
        assertTrue(runCatching { ClusterInvite.decode(invite.encode() + "&code=000000", 1000000) }.isFailure)
    }
}

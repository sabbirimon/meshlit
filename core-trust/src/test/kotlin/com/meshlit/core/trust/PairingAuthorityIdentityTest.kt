package com.meshlit.core.trust

import com.meshlit.core.common.MeshlitResult
import org.junit.Assert.*
import org.junit.Test

class PairingAuthorityIdentityTest {
    private fun approve(store: TrustStore, peer: RecoverableIdentity) {
        store.upsert(DeviceTrustPolicy(peer.nodeId, TrustTier.LOCAL_TRUSTED, setOf(ClusterRoleTag.BRAIN),
            publicKeyFingerprint = PeerCrypto.fingerprint(peer.publicKey)))
    }

    private fun request(peer: RecoverableIdentity, time: Long): HandshakeRequest {
        val nonce = PeerCrypto.randomHex()
        return HandshakeRequest(peer.nodeId, peer.publicKey, TrustTier.LOCAL_TRUSTED.tag, nonce,
            identityProof = PeerCrypto.prove(peer, nonce, time))
    }

    @Test fun futureDatedProofCannotReplayAfterReceiptWindowExpires() {
        var now = 1_000_000L
        val owner = PeerCrypto.generate("owner")
        val peer = PeerCrypto.generate("peer")
        val store = InMemoryTrustStore().also { approve(it, peer) }
        val authority = PairingAuthority({ owner }, store, now = { now })
        val proofRequest = request(peer, now + 30_000)
        assertTrue(authority.handshake(proofRequest) is MeshlitResult.Success)

        now += 120_001
        assertTrue(PeerCrypto.verify(proofRequest.identityProof!!, peer.nodeId, peer.publicKey, proofRequest.nonce, now))
        assertEquals("handshake.replay_or_busy", (authority.handshake(proofRequest) as MeshlitResult.Failure).error.tag)
        now = 1_150_000L
        assertEquals("handshake.replay_or_busy", (authority.handshake(proofRequest) as MeshlitResult.Failure).error.tag)
        now++
        assertEquals("handshake.bad_signature", (authority.handshake(proofRequest) as MeshlitResult.Failure).error.tag)
        assertTrue(authority.handshake(request(peer, now)) is MeshlitResult.Success)
    }

    @Test fun restoringDifferentNodeOrKeyInvalidatesAllExistingSessionsAndPendingApprovals() {
        val original = PeerCrypto.generate("owner")
        val peer = PeerCrypto.generate("peer")
        val waiting = PeerCrypto.generate("waiting")
        val now = 1_000_000L
        for (replacement in listOf(PeerCrypto.generate(original.nodeId), original.copy(nodeId = "restored-owner"))) {
            var owner = original
            val store = InMemoryTrustStore().also { approve(it, peer) }
            val authority = PairingAuthority({ owner }, store, now = { now })
            val session = (authority.handshake(request(peer, now)) as MeshlitResult.Success).value
            assertNotNull(authority.authenticate(session.tokenHex))
            authority.handshake(request(waiting, now))
            assertEquals(1, authority.pending.value.size)

            owner = replacement
            assertNull(authority.authenticate(session.tokenHex))
            assertEquals(0, authority.activeSessionCount.value)
            assertTrue(authority.pending.value.isEmpty())
            owner = original
            assertNull(authority.authenticate(session.tokenHex))
            val renewed = (authority.handshake(request(peer, now)) as MeshlitResult.Success).value
            assertNotNull(authority.authenticate(renewed.tokenHex))
        }
    }

    @Test fun selfPairingNeverEntersOwnerApprovalQueue() {
        val owner = PeerCrypto.generate("owner")
        val now = 1_000_000L
        val authority = PairingAuthority({ owner }, InMemoryTrustStore(), now = { now })
        assertEquals("handshake.self", (authority.handshake(request(owner, now)) as MeshlitResult.Failure).error.tag)
        assertTrue(authority.pending.value.isEmpty())
        assertEquals(0, authority.activeSessionCount.value)
    }
}

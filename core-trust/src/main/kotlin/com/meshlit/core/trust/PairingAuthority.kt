package com.meshlit.core.trust

import com.meshlit.core.common.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class PairingHistoryEvent(val nodeId: String, val fingerprint: String, val requestedTier: String,
    val status: String, val atMs: Long)

/** Signed handshake with owner approval, bounded replay cache and revocable scoped sessions. */
class PairingAuthority(private val self: () -> RecoverableIdentity, private val store: TrustStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val onEvent: (PairingHistoryEvent) -> Unit = {}) {
    private data class Session(val node: String, val fingerprint: String, val expires: Long, val tier: TrustTier)
    private val sessions = ConcurrentHashMap<String, Session>()
    private val seen = mutableMapOf<String, Long>()
    private val requests = MutableStateFlow<List<HandshakeRequest>>(emptyList())
    private val pendingSince = mutableMapOf<String, Long>()
    private val sessionCount = MutableStateFlow(0)
    val pending = requests.asStateFlow()
    val activeSessionCount = sessionCount.asStateFlow()
    private fun publish(event: PairingHistoryEvent) { runCatching { onEvent(event) } }
    @Synchronized fun queuedAt(node: String): Long = pendingSince[node] ?: 0L
    @Synchronized fun prunePending() {
        val cutoff = now() - 300_000
        requests.value.filter { (pendingSince[it.nodeId] ?: Long.MAX_VALUE) < cutoff }
            .forEach { publish(PairingHistoryEvent(it.nodeId, PeerCrypto.fingerprint(it.publicKey), it.requestedTier, "expired", now())) }
        pendingSince.entries.removeAll { it.value < cutoff }
        requests.value = requests.value.filter { pendingSince.containsKey(it.nodeId) }
    }
    @Synchronized fun handshake(req: HandshakeRequest): MeshlitResult<HandshakeResponse> {
        val time = now(); seen.entries.removeAll { it.value < time - 120_000 };prunePending()
        val proof = req.identityProof ?: return fail("handshake.unsigned_request")
        if (req.nodeId.length !in 1..128 || req.publicKey.length > 1024 ||
            !PeerCrypto.verify(proof, req.nodeId, req.publicKey, req.nonce, time)) return fail("handshake.bad_signature")
        val replay = "${req.nodeId}:${req.nonce}"
        if (seen.containsKey(replay) || seen.size >= 512) return fail("handshake.replay_or_busy")
        seen[replay] = time
        val requested = TrustTier.fromTag(req.requestedTier) ?: return fail("handshake.invalid_tier")
        val policy = store.policyFor(NodeId(req.nodeId))
        val fingerprint = PeerCrypto.fingerprint(req.publicKey)
        if (policy?.publicKeyFingerprint != fingerprint || policy.isTokenExpired(time) || ClusterRoleTag.BRAIN !in policy.allowedRoles) {
            val existing = requests.value.firstOrNull { it.nodeId == req.nodeId }
            if (existing != null && existing.publicKey != req.publicKey) return fail("handshake.pending_identity_conflict")
            if (existing == null && requests.value.size >= 64) return fail("handshake.queue_full_retry_later")
            if (existing == null) {
                pendingSince[req.nodeId] = time
                requests.value = requests.value + req
                publish(PairingHistoryEvent(req.nodeId, fingerprint, req.requestedTier, "waiting", time))
            } else requests.value = requests.value.map { if (it.nodeId == req.nodeId) req else it }
            return fail("handshake.peer_approval_required")
        }
        val accepted = effectiveTier(policy, policy.copy(trustTier = requested))
        if (accepted != requested) return fail("handshake.scope_mismatch")
        val identity = self()
        if (identity.nodeId == req.nodeId) return fail("handshake.self")
        sessions.entries.removeAll { it.value.expires <= time }
        sessionCount.value = sessions.size
        if (sessions.size >= 256) return fail("handshake.session_limit")
        val token = PeerCrypto.randomHex(); val expiry = time + 3_600_000
        sessions[tokenHash(token)] = Session(req.nodeId, fingerprint, expiry, accepted)
        sessionCount.value = sessions.size
        val response = HandshakeResponse(accepted.tag, token, expiry, identity.publicKey, identity.nodeId,
            PeerCrypto.prove(identity, req.nonce, time))
        requests.value = requests.value.filter { it.nodeId != req.nodeId }
        pendingSince.remove(req.nodeId)
        publish(PairingHistoryEvent(req.nodeId, fingerprint, req.requestedTier, "connected", time))
        return MeshlitResult.Success(response.copy(responseSignature = PeerCrypto.sign(identity, response.message())))
    }
    fun authenticate(token: String): DeviceTrustPolicy? {
        if (!token.matches(Regex("[a-f0-9]{64}"))) return null
        val session = sessions[tokenHash(token)] ?: return null
        if (session.expires <= now()) { sessions.remove(tokenHash(token));sessionCount.value=sessions.size; return null }
        return store.policyFor(NodeId(session.node))?.takeIf {
            !it.isTokenExpired(now()) && it.publicKeyFingerprint == session.fingerprint && ClusterRoleTag.BRAIN in it.allowedRoles
        }?.let { policy -> policy.copy(trustTier = effectiveTier(policy, policy.copy(trustTier = session.tier))) }
    }
    @Synchronized fun dismiss(node: String) {
        requests.value.firstOrNull { it.nodeId == node }?.let {
            publish(PairingHistoryEvent(it.nodeId, PeerCrypto.fingerprint(it.publicKey), it.requestedTier, "dismissed", now()))
        }
        requests.value = requests.value.filter { it.nodeId != node };pendingSince.remove(node)
    }
    @Synchronized fun revoke(node: String) {
        sessions.entries.removeAll { it.value.node == node }
        sessionCount.value = sessions.size
        requests.value.firstOrNull { it.nodeId == node }?.let {
            publish(PairingHistoryEvent(it.nodeId, PeerCrypto.fingerprint(it.publicKey), it.requestedTier, "revoked", now()))
        }
        requests.value = requests.value.filter { it.nodeId != node };pendingSince.remove(node)
    }
    private fun tokenHash(token: String) = PeerCrypto.hex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))
    private fun fail(tag: String) = MeshlitResult.Failure(MeshlitError.Auth(tag))
}

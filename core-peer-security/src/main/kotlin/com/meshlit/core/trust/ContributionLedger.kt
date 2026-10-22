package com.meshlit.core.trust

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/** Local, non-transferable contribution receipts. This is not a blockchain balance. */
@Serializable
data class ContributionReceipt(val jobId: String, val nodeId: String, val generatedTokens: Int,
    val awardedAtMs: Long, val previousHash: String, val publicKey: String, val signature: String) {
    fun message() = "meshlit.contribution.v1\n$jobId\n$nodeId\n$generatedTokens\n$awardedAtMs\n$previousHash\n$publicKey".toByteArray(Charsets.UTF_8)
    fun hash() = PeerCrypto.hex(MessageDigest.getInstance("SHA-256").digest(message() + PeerCrypto.bytes(signature)))
}

object ContributionLedger {
    const val MAX_TOKENS_PER_JOB = 32768
    fun append(existing: List<ContributionReceipt>, identity: RecoverableIdentity, jobId: String,
        tokens: Int, now: Long = System.currentTimeMillis()): List<ContributionReceipt> {
        require(verify(existing, identity.nodeId, identity.publicKey)) { "Contribution ledger verification failed" }
        if (existing.any { it.jobId == jobId }) return existing
        require(jobId.isNotBlank() && jobId.length <= 128 && tokens in 1..MAX_TOKENS_PER_JOB)
        val unsigned = ContributionReceipt(jobId, identity.nodeId, tokens, now,
            existing.lastOrNull()?.hash().orEmpty(), identity.publicKey, "")
        return existing + unsigned.copy(signature = PeerCrypto.sign(identity, unsigned.message()))
    }
    fun verify(receipts: List<ContributionReceipt>, nodeId: String, publicKey: String): Boolean {
        if (receipts.isEmpty()) return true
        val key = runCatching { PeerCrypto.verifiedPublicKey(publicKey) }.getOrNull() ?: return false
        var previous = ""; val ids = mutableSetOf<String>()
        for (r in receipts) {
            if (r.nodeId != nodeId || r.publicKey != publicKey || r.previousHash != previous ||
                r.jobId.isBlank() || r.jobId.length > 128 ||
                r.generatedTokens !in 1..MAX_TOKENS_PER_JOB || !ids.add(r.jobId) ||
                !PeerCrypto.verifySignature(key, r.message(), r.signature)) return false
            previous = r.hash()
        }
        return true
    }
}

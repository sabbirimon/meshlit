package com.meshlit.core.training.durability

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * Opaque resume token persisted on every checkpoint. Carries the
 * job id, latest step, the peer id that owned the coordinator role
 * at flush time, and a sha256 signature over (jobId, step, peerId,
 * schemaVersion).
 *
 * The token is intentionally NOT cryptographic — it's a corruption /
 * version-drift guard, not an authenticity proof. Authentication
 * lives one layer up in `core-trust` (the existing AES256/GCM store
 * from the cloud-mcp work).
 */
@Serializable
data class ResumeToken(
    val resumeVersion: Int = CURRENT_VERSION,
    val jobId: String,
    val step: Long,
    val peerId: String,
    val schemaVersion: Int = SCHEMA_VERSION,
    val signature: String,
) {
    /** True iff this token matches the supplied inputs (catch
     *  version-drift and corruption). */
    fun isValid(): Boolean =
        resumeVersion == CURRENT_VERSION &&
        schemaVersion == SCHEMA_VERSION &&
        computeSignature(jobId, step, peerId) == signature

    companion object {
        const val CURRENT_VERSION: Int = 1
        const val SCHEMA_VERSION: Int = 1

        fun create(jobId: String, step: Long, peerId: String): ResumeToken =
            ResumeToken(
                resumeVersion = CURRENT_VERSION,
                jobId = jobId,
                step = step,
                peerId = peerId,
                schemaVersion = SCHEMA_VERSION,
                signature = computeSignature(jobId, step, peerId),
            )

        private fun computeSignature(jobId: String, step: Long, peerId: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            md.update("$jobId\n$step\n$peerId\n$SCHEMA_VERSION".toByteArray(Charsets.UTF_8))
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

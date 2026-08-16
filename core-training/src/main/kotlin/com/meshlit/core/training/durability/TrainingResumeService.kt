package com.meshlit.core.training.durability

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import java.io.File

/**
 * Crash-safe recovery for a cooperative LoRA run.
 *
 * On every successful step the trainer writes a [ResumeToken] to
 * `filesDir/training/<jobId>/resume.token`. On process restart the
 * [TrainingResumeService] reads the latest token and offers it back
 * to the caller so the trainer can resume from the right step.
 *
 * File layout (matches the existing `LocalLoraTrainer.checkpoint`):
 *   filesDir/training/<jobId>/resume.token    <- JSON-encoded ResumeToken
 *   filesDir/training/<jobId>/step-<n>.bin    <- the actual gradient data
 *
 * The service is intentionally tiny: read + validate + return. The
 * caller decides whether to resume (per `DistributedConfig.Durability.onCrash`).
 *
 * Idempotent: calling `read` twice returns the same result.
 */
class TrainingResumeService(
    private val baseDir: File,
) {
    private val log = logger("TrainingResumeService")
    private val json = Json { ignoreUnknownKeys = false }

    /** Read the latest resume token for [jobId]. Returns
     *  [MeshlitResult.Failure] with `Invalid` if the file is missing
     *  or the signature doesn't match. */
    fun read(jobId: String): MeshlitResult<ResumeToken> {
        val f = File(File(baseDir, jobId), "resume.token")
        if (!f.exists()) {
            return MeshlitResult.Failure(
                MeshlitError.Invalid("cluster.trainer.resume.missing:${f.absolutePath}")
            )
        }
        return try {
            val tok = json.decodeFromString(ResumeToken.serializer(), f.readText(Charsets.UTF_8))
            if (!tok.isValid()) {
                MeshlitResult.Failure(
                    MeshlitError.Invalid("cluster.trainer.resume.signature_mismatch")
                )
            } else {
                MeshlitResult.Success(tok)
            }
        } catch (e: SerializationException) {
            log.warn(
                "cluster.trainer.resume.parse_failed",
                "could not parse resume token",
                mapOf("err" to (e.message ?: "?")),
            )
            MeshlitResult.Failure(
                MeshlitError.Invalid(
                    tag = "cluster.trainer.resume.parse:${e.message?.take(120)}",
                    cause = e,
                )
            )
        }
    }

    /** Persist the resume token. Atomically replaces the existing file. */
    fun write(token: ResumeToken): MeshlitResult<Unit> {
        val dir = File(baseDir, token.jobId)
        val ok = dir.mkdirs()
        if (!ok && !dir.isDirectory) {
            return MeshlitResult.Failure(
                MeshlitError.Invalid("cluster.trainer.resume.mkdirs_failed:${dir.absolutePath}")
            )
        }
        val tmp = File(dir, "resume.token.tmp")
        val final = File(dir, "resume.token")
        return try {
            tmp.writeText(json.encodeToString(ResumeToken.serializer(), token), Charsets.UTF_8)
            // Atomic rename — partial writes never become visible.
            if (!tmp.renameTo(final)) {
                MeshlitResult.Failure(
                    MeshlitError.Invalid("cluster.trainer.resume.rename_failed")
                )
            } else {
                MeshlitResult.Success(Unit)
            }
        } catch (e: Throwable) {
            MeshlitResult.Failure(
                MeshlitError.Resource(
                    tag = "cluster.trainer.resume.write:${e.message?.take(120)}",
                    cause = e,
                )
            )
        }
    }

    /** Remove the resume token (called on graceful completion). */
    fun clear(jobId: String) {
        val f = File(File(baseDir, jobId), "resume.token")
        if (f.exists()) f.delete()
    }

    /** List all resumable jobIds — used by the UI to show "you can
     *  resume these jobs after a restart". */
    fun listResumable(): List<String> {
        if (!baseDir.isDirectory) return emptyList()
        return baseDir.listFiles()
            ?.filter { it.isDirectory && File(it, "resume.token").exists() }
            ?.map { it.name }
            ?: emptyList()
    }
}

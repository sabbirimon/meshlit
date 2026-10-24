package com.meshlit.core.sandbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.coroutines.coroutineContext

data class CommandResult(
    val exitCode: Int?, val stdout: String, val stderr: String,
    val timedOut: Boolean = false, val truncated: Boolean = false,
)

/** Batch user commands. No interactive stdin or unrestricted AI tool exposure. */
class ProcessRunner {
    suspend fun execute(plan: RuntimePlan, timeoutMs: Long = 15000, maxBytes: Int = 262144): CommandResult {
        require(timeoutMs in 100..60000 && maxBytes in 256..1048576)
        return withContext(Dispatchers.IO) {
            val process = ProcessBuilder(plan.argv).directory(plan.workingDirectory).apply {
                environment().putAll(plan.environment)
            }.start()
            val out = BoundedOutput(maxBytes)
            val err = BoundedOutput(maxBytes)
            try {
                process.outputStream.close()
                // Poll available bytes instead of blocking on EOF: grandchildren
                // can retain pipes after the direct process exits or is killed.
                val exitCode: Int? = withTimeoutOrNull(timeoutMs) {
                    while (true) {
                        coroutineContext.ensureActive()
                        out.drain(process.inputStream)
                        err.drain(process.errorStream)
                        val code = runCatching { process.exitValue() }.getOrNull()
                        if (code != null) {
                            out.drain(process.inputStream)
                            err.drain(process.errorStream)
                            return@withTimeoutOrNull code
                        }
                        delay(10)
                    }
                    @Suppress("UNREACHABLE_CODE")
                    null
                }
                CommandResult(exitCode, out.text(), err.text(), exitCode == null, out.truncated || err.truncated)
            } finally {
                // Direct process only; rooted grandchildren need a process-group
                // supervisor. This API does not claim complete job containment.
                process.destroy()
                runCatching { process.inputStream.close() }
                runCatching { process.errorStream.close() }
                runCatching { process.outputStream.close() }
            }
        }
    }

    private class BoundedOutput(private val limit: Int) {
        private val bytes = ByteArrayOutputStream()
        var truncated = false
            private set
        fun text(): String = bytes.toString("UTF-8")
        fun drain(stream: InputStream) {
            val chunk = ByteArray(8192)
            // Bound per-tick work so cancellation/timeouts remain observable.
            repeat(16) {
                val ready = runCatching { stream.available() }.getOrDefault(0)
                if (ready <= 0) return
                val count = stream.read(chunk, 0, minOf(chunk.size, ready))
                if (count <= 0) return
                val accepted = minOf(count, (limit - bytes.size()).coerceAtLeast(0))
                bytes.write(chunk, 0, accepted)
                if (accepted < count) truncated = true
            }
        }
    }
}

package com.meshlit.agent.hooks

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Append-only audit sink for hook invocations. Mirrors the shape
 * and threading model of [com.meshlit.agent.TermuxAuditSink]:
 *
 *  - One JSON object per line, written to `filesDir/agent/hooks-audit.jsonl`.
 *  - Single [ReentrantLock] around the open/write/close path; per-call
 *    latency is microseconds, so we deliberately do not buffer.
 *  - Variable bag values are redacted before write (see [redact]).
 *
 * The directory `filesDir/agent/` is created lazily on first write
 * so callers don't have to touch the file system before invoking
 * the engine.
 *
 * **No secrets in audit:** every value is run through [redact] before
 * serialization. The redaction is conservative — anything that looks
 * like a bearer token, API key, password, or JWT is replaced with
 * `"<redacted>"`. Long values are capped at [AUDIT_MAX_VAR_LEN].
 *
 * The sink is intentionally **interface-free** for v1: there's only
 * one implementation. Generalize to an `AuditSink` interface when
 * the second implementation lands.
 */
class HookAuditSink(
    private val auditFile: File,
) {

    /**
     * Convenience: build against the on-device default location.
     * The `path: String` parameter is the app's `filesDir.absolutePath`
     * — the sink writes to `<path>/agent/hooks-audit.jsonl`. The
     * primary constructor takes `File`; this one takes `String` so
     * there's no overload ambiguity when callers pass either type.
     */
    constructor(path: String) : this(
        auditFile = File(File(path, "agent"), "hooks-audit.jsonl")
    )

    private val lock = ReentrantLock()
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)

    /** Outcome vocabulary. Mirrored in tests. */
    object Outcome {
        const val SUCCESS = "SUCCESS"
        const val TIMEOUT = "TIMEOUT"
        const val ERROR = "ERROR"
        const val DENIED = "DENIED"
        const val SKIPPED = "SKIPPED" // master kill or per-hook disabled
    }

    fun append(
        hookId: String,
        hookName: String,
        triggerTag: String,
        outcome: String,
        durationMs: Long,
        vars: Map<String, String>,
        error: String? = null,
    ) {
        val ts = iso.format(Date())
        val safeVars = redact(vars)
        val varsSummary = summarize(safeVars)
        val line = buildString {
            append("{\"ts\":\""); append(jsonEscape(ts)); append("\",")
            append("\"hook_id\":\""); append(jsonEscape(hookId)); append("\",")
            append("\"hook_name\":\""); append(jsonEscape(hookName)); append("\",")
            append("\"trigger\":\""); append(jsonEscape(triggerTag)); append("\",")
            append("\"outcome\":\""); append(jsonEscape(outcome)); append("\",")
            append("\"duration_ms\":"); append(durationMs); append(",")
            append("\"vars_summary\":\""); append(jsonEscape(varsSummary)); append("\",")
            append("\"error\":")
            if (error == null) append("null")
            else {
                append("\""); append(jsonEscape(error)); append("\"")
            }
            append("}\n")
        }
        lock.withLock {
            runCatching {
                auditFile.parentFile?.mkdirs()
                auditFile.appendText(line, Charsets.UTF_8)
            }
        }
    }

    /** Test-only: read all lines. Not for production use. */
    fun readAllLines(): List<String> = lock.withLock {
        if (auditFile.exists()) auditFile.readLines(Charsets.UTF_8) else emptyList()
    }

    /**
     * Replace any value that looks like a secret with `"<redacted>"`.
     * Conservative — false positives are fine, false negatives are not.
     */
    internal fun redact(vars: Map<String, String>): Map<String, String> {
        if (vars.isEmpty()) return vars
        val out = LinkedHashMap<String, String>(vars.size)
        for ((k, v) in vars) {
            val rekey = if (SECRET_KEY_REGEX.containsMatchIn(k)) "<redacted>" else v
            out[k] = if (SECRET_VALUE_REGEX.containsMatchIn(rekey)) "<redacted>" else rekey
        }
        return out
    }

    private fun summarize(vars: Map<String, String>): String {
        if (vars.isEmpty()) return ""
        val parts = ArrayList<String>(vars.size)
        var totalChars = 0
        for ((k, v) in vars) {
            if (totalChars >= AUDIT_MAX_VARS_TOTAL) break
            val part = "$k=$v"
            parts.add(part)
            totalChars += part.length + 1
        }
        val joined = parts.joinToString(",")
        return if (joined.length > AUDIT_MAX_VARS_TOTAL) {
            joined.take(AUDIT_MAX_VARS_TOTAL) + "…(truncated)"
        } else joined
    }

    /** JSON escape. Conservative — never emit raw control characters. */
    private fun jsonEscape(s: String): String = buildString(s.length + 2) {
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        }
    }

    companion object {
        /** Cap on a single variable's length after redaction. */
        const val AUDIT_MAX_VAR_LEN = 1024

        /** Cap on the total `vars_summary` string. */
        const val AUDIT_MAX_VARS_TOTAL = 8 * 1024

        private val SECRET_KEY_REGEX = Regex(
            "(?i)(bearer|access[_-]?token|api[_-]?key|secret|passwd?|jwt|authorization)"
        )

        // Conservative: a high-entropy hex / base64 / JWT-ish substring.
        // Length-bounded so we don't false-positive on long natural text.
        private val SECRET_VALUE_REGEX = Regex(
            "(?i)(bearer\\s+[A-Za-z0-9._\\-]{16,}|" +
                "eyJ[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}|" +
                "\\b[A-Fa-f0-9]{32,}\\b)"
        )
    }
}
package com.meshlit.agent

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Append-only [AuditSink] for `agent_termux_run_command`. Writes one
 * JSON object per line to `filesDir/agent/termux-audit.jsonl` so the
 * Activity timeline can render the full chain without re-deriving it
 * from logcat.
 *
 * **Why a real sink is required:**
 * The agent dispatcher refused to silently swallow events. A no-op
 * sink makes the per-invocation audit line invisible to the user;
 * `Activity → Agent` would show "Termux run at unknown time" with no
 * command, exit code, or duration. This sink records every call —
 * whether the bridge succeeded, returned a typed failure, was
 * denylisted, or was approval-denied.
 *
 * **Threading:** the sink is append-only and uses a single
 * [ReentrantLock] around the open/write/close. Per-call latency is
 * microseconds; we deliberately do not buffer because every audit
 * line is read-only and small.
 *
 * **File location:** `filesDir/agent/termux-audit.jsonl`. The
 * directory is created on first write so callers don't have to
 * touch the file system before invoking the dispatcher.
 *
 * **No secrets in audit:** the dispatcher passes redacted stdout /
 * stderr. This sink writes the already-redacted strings verbatim.
 * Do not feed it untrusted output.
 */
class TermuxAuditSink(private val appContext: Context) : AuditSink {

    private val lock = ReentrantLock()
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)

    private fun auditFile(): File {
        val dir = File(appContext.filesDir, "agent")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "termux-audit.jsonl")
    }

    override fun append(
        toolName: String,
        arguments: List<String>,
        exitCode: Int?,
        durationMs: Long,
        outcome: String,
        redactedStdout: String,
        redactedStderr: String,
    ) {
        val line = buildString {
            append("{\"ts\":\""); append(iso.format(Date())); append("\",")
            append("\"tool\":\""); append(jsonEscape(toolName)); append("\",")
            append("\"args\":["); append(arguments.joinToString(",") { "\"${jsonEscape(it)}\"" }); append("],")
            append("\"exit_code\":"); append(exitCode?.toString() ?: "null"); append(",")
            append("\"duration_ms\":"); append(durationMs); append(",")
            append("\"outcome\":\""); append(jsonEscape(outcome)); append("\",")
            append("\"stdout\":\""); append(jsonEscape(redactedStdout.take(AUDIT_MAX_LEN))); append("\",")
            append("\"stderr\":\""); append(jsonEscape(redactedStderr.take(AUDIT_MAX_LEN))); append("\"")
            append("}\n")
        }
        lock.withLock {
            runCatching {
                auditFile().appendText(line, Charsets.UTF_8)
            }
        }
    }

    /** Replace unsafe chars for safe JSON. Conservative — we never emit raw newlines. */
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

    private companion object {
        // Hard cap on stdout / stderr we persist. Keeps the audit file
        // small even when a command emits megabytes of output. The
        // agent dispatcher passes already-redacted strings; we slice
        // here as a defense-in-depth.
        const val AUDIT_MAX_LEN = 8 * 1024
    }
}

/**
 * Approval sink used in production until the in-app approval sheet
 * is wired (Phase 5.5+). Defaults to **deny** so the agent can never
 * run a non-Allowlisted command without a real user gesture — the
 * Termux allowlist of 20 read-only commands still runs through
 * without approval, but anything outside the allowlist is refused
 * until the user opens an approval sheet.
 *
 * The [allowListBypass] flag exists only for tests; production code
 * never sets it. Tests can flip it to verify the agent sees the
 * "approval denied" result for non-allowlisted commands.
 */
class DenyByDefaultApprovalSink(
    private val allowListBypass: Boolean = false,
) : ApprovalSink {
    override suspend fun requestApproval(
        toolName: String,
        description: String,
        details: Map<String, Any>,
    ): ApprovalVerdict {
        if (allowListBypass) return ApprovalVerdict.Granted
        return ApprovalVerdict.Denied
    }
}
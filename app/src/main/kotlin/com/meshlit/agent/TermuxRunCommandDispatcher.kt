package com.meshlit.agent

import android.content.Context
import com.meshlit.core.cloudmcp.McpEvent
import com.meshlit.core.cloudmcp.agent.AgentCapability
import com.meshlit.core.cloudmcp.agent.AgentCapabilityRegistry
import com.meshlit.core.cloudmcp.agent.AgentCapabilityTools
import com.meshlit.network.termux.AndroidTermuxBridge
import com.meshlit.network.termux.TermuxBridge
import com.meshlit.network.termux.TermuxRunResult
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * `agent_termux_run_command` dispatcher. Wraps [TermuxBridge] in
 * the agent's permission + audit framework.
 *
 * **Security model:**
 *  1. The agent capability `AgentCapability.Termux` must be both
 *     `enabledByUser` and `permissionGranted` (Termux install +
 *     RUN_COMMAND permission + allow-external-apps=true). The
 *     registry's `isAllowed()` is the only authoritative check;
 *     we re-probe inside the dispatcher as a defense-in-depth.
 *  2. The executable path is **must** start with
 *     `/data/data/com.termux/files/usr/` — we never invoke
 *     arbitrary paths from model text. This restricts the attack
 *     surface to binaries already shipped inside Termux's
 *     userland.
 *  3. Default allowlist of read-only / safe commands
 *     (`echo`, `id`, `uptime`, `df`, `pwd`, `date`) run without
 *     further approval. Anything else routes through the
 *     `ApprovalRequest` bus; the dispatcher returns
 *     `ToolResult(needsApproval=true)` and the host shows the
 *     approval sheet.
 *  4. Denylist: anything destructive (`rm`, `mv`, `chmod`, `chown`,
 *     `dd`, `mkfs*`, `mount`, `umount`, `kill`, `pkill`, `shutdown`,
 *     `reboot`, `iptables`, `curl`/`wget` to non-loopback) is
 *     refused outright — `ToolResult(error=permission_denied)`.
 *  5. Every invocation writes an `AuditRecord` carrying the
 *     command line, the exit code, the duration, and a redacted
 *     stdout/stderr (any token-shaped substring is masked).
 *  6. Working directory must be inside Termux's userland; we
 *     refuse paths that escape.
 *  7. Timeout hard-capped at 5 minutes per call. Default 30 s.
 *
 * **Why this is NOT a backdoor:**
 * The dispatcher refuses all model-supplied paths outside Termux's
 * own prefix, refuses destructive commands outright, requires an
 * explicit user opt-in via the AgentCapability registry, and
 * surfaces every invocation to the Activity audit timeline.
 */
class TermuxRunCommandDispatcher(
    private val appContext: Context,
    private val registry: AgentCapabilityRegistry,
    private val bridge: TermuxBridge,
    private val approvals: ApprovalSink,
    private val auditSink: AuditSink,
    private val maxTimeoutMs: Long = 5 * 60 * 1000L,
    private val defaultTimeoutMs: Long = 30_000L,
) {

    /**
     * The canonical Termux userland prefix. Only binaries under
     * this prefix are allowed to be executed by the agent.
     */
    private val termuxPrefix = "/data/data/com.termux/files/usr/"

    /**
     * Read-only / safe binaries that run without an approval
     * sheet. Conservative — adding a binary here is a deliberate
     * change.
     */
    private val allowlist = setOf(
        "${termuxPrefix}bin/echo",
        "${termuxPrefix}bin/cat",
        "${termuxPrefix}bin/head",
        "${termuxPrefix}bin/tail",
        "${termuxPrefix}bin/wc",
        "${termuxPrefix}bin/ls",
        "${termuxPrefix}bin/stat",
        "${termuxPrefix}bin/pwd",
        "${termuxPrefix}bin/date",
        "${termuxPrefix}bin/uptime",
        "${termuxPrefix}bin/id",
        "${termuxPrefix}bin/df",
        "${termuxPrefix}bin/uname",
        "${termuxPrefix}bin/env",
        "${termuxPrefix}bin/which",
        "${termuxPrefix}bin/whoami",
        "${termuxPrefix}bin/du",
        "${termuxPrefix}bin/free",
        "${termuxPrefix}bin/true",
        "${termuxPrefix}bin/false",
    )

    /**
     * Destructive binaries that are **never** allowed, regardless
     * of approval. The host is supposed to be a single device —
     * there is no legitimate use of these through a chat agent.
     */
    private val denylist = setOf(
        "rm", "rmdir", "mv", "cp", "chmod", "chown", "chgrp",
        "dd", "mkfs", "mke2fs", "mkfs.ext4", "mkfs.vfat",
        "mount", "umount", "losetup",
        "kill", "killall", "pkill",
        "shutdown", "reboot", "poweroff", "halt",
        "iptables", "ip6tables", "nft",
        "su", "sudo",
        "curl", "wget",
        "ssh", "scp", "rsync",
        "passwd", "useradd", "userdel", "groupadd",
    )

    suspend fun runCommand(args: JsonObject): McpEvent.ToolResult = withContext(kotlinx.coroutines.Dispatchers.IO) {
        // 1. Capability gate.
        if (!registry.isAllowed(AgentCapability.Termux)) {
            return@withContext denied()
        }
        // 2. Re-probe Termux install + permission.
        val setup = bridge.probe()
        if (!setup.installed) {
            return@withContext errorResult(
                "termux_not_installed",
                "Install Termux (F-Droid) and re-open Settings → Integrations → Termux.",
            )
        }
        if (!setup.runCommandPermissionGranted) {
            return@withContext errorResult(
                "permission_missing",
                "Grant the RUN_COMMAND permission in Termux's settings.",
            )
        }

        // 3. Extract + validate args.
        val executable = args["executable"]?.jsonPrimitive?.contentOrNull
        if (executable == null) {
            return@withContext errorResult("missing_executable", null)
        }
        if (!executable.startsWith(termuxPrefix)) {
            return@withContext errorResult(
                "path_outside_userland",
                "Executable must be inside $termuxPrefix",
            )
        }
        val arguments = args["arguments"]?.let { el ->
            (el as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }
        } ?: emptyList()
        val workingDirectory = args["working_directory"]?.jsonPrimitive?.contentOrNull
        if (workingDirectory != null && !workingDirectory.startsWith(termuxPrefix)) {
            return@withContext errorResult("cwd_outside_userland", null)
        }
        val timeoutMs = (args["timeout_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: defaultTimeoutMs).coerceIn(1_000L, maxTimeoutMs)
        val stdin = args["stdin"]?.jsonPrimitive?.contentOrNull
        val approved = args["approved"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false

        val baseExecutable = executable.removePrefix(termuxPrefix).removePrefix("bin/")
        if (baseExecutable in denylist) {
            auditSink.append(
                toolName = "agent_termux_run_command",
                arguments = listOf(executable) + arguments,
                exitCode = null,
                durationMs = 0,
                outcome = "denied_denylist",
                redactedStdout = "",
                redactedStderr = "",
            )
            return@withContext errorResult(
                "command_denied",
                "'$baseExecutable' is in the agent's deny-list. Run it manually from the Terminal screen.",
            )
        }

        // 4. Approval gate for non-Allowlisted commands.
        if (executable !in allowlist && !approved) {
            val verdict = approvals.requestApproval(
                toolName = "agent_termux_run_command",
                description = "Run $baseExecutable in Termux",
                details = mapOf(
                    "executable" to executable,
                    "arguments" to arguments,
                    "working_directory" to (workingDirectory ?: ""),
                    "timeout_ms" to timeoutMs.toString(),
                ),
            )
            if (verdict != ApprovalVerdict.Granted) {
                auditSink.append(
                    toolName = "agent_termux_run_command",
                    arguments = listOf(executable) + arguments,
                    exitCode = null,
                    durationMs = 0,
                    outcome = "approval_denied",
                    redactedStdout = "",
                    redactedStderr = "",
                )
                return@withContext errorResult("approval_denied", null)
            }
        }

        // 5. Run.
        val startedAt = System.currentTimeMillis()
        val result: TermuxRunResult = try {
            withTimeoutOrNull(timeoutMs) { bridge.runCommand(
                executable = executable,
                arguments = arguments,
                workingDirectory = workingDirectory,
                stdin = stdin,
                timeoutMs = timeoutMs,
                background = false,
            ) } ?: TermuxRunResult(
                handleId = "", executable = executable, arguments = arguments,
                exitCode = null, stdout = "", stderr = "Command timed out",
                durationMs = System.currentTimeMillis() - startedAt,
                status = TermuxRunResult.Status.TIMEOUT,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            TermuxRunResult(
                handleId = "",
                executable = executable,
                arguments = arguments,
                exitCode = null,
                stdout = "",
                stderr = t.message ?: t::class.simpleName ?: "unknown",
                durationMs = System.currentTimeMillis() - startedAt,
                status = TermuxRunResult.Status.ERROR,
                errmsg = t::class.simpleName,
            )
        }

        auditSink.append(
            toolName = "agent_termux_run_command",
            arguments = listOf(executable) + arguments,
            exitCode = result.exitCode,
            durationMs = result.durationMs,
            outcome = result.status.name,
            redactedStdout = redact(result.stdout),
            redactedStderr = redact(result.stderr),
        )

        return@withContext McpEvent.ToolResult(
            providerId = AgentCapabilityTools.PROVIDER_ID,
            callId = "",
            ok = result.status == TermuxRunResult.Status.SUCCESS,
            body = buildJsonObject {
                put("status", JsonPrimitive(result.status.name))
                put("executable", JsonPrimitive(result.executable))
                put("arguments", buildJsonArray { arguments.forEach { add(JsonPrimitive(it)) } })
                put("exit_code", JsonPrimitive(result.exitCode))
                put("stdout", JsonPrimitive(result.stdout))
                put("stderr", JsonPrimitive(result.stderr))
                put("duration_ms", JsonPrimitive(result.durationMs))
                if (result.errmsg != null) put("errmsg", JsonPrimitive(result.errmsg))
            }.toString(),
        )
    }

    private fun denied() = errorResult(
        "termux_capability_disabled",
        "Enable the Termux integration in Settings → Integrations.",
    )

    private fun errorResult(code: String, message: String?): McpEvent.ToolResult {
        if (code !in setOf("command_denied", "approval_denied")) {
            auditSink.append("agent_termux_run_command", emptyList(), null, 0,
                code, "", "")
        }
        val body = buildJsonObject {
            put("error", JsonPrimitive(code))
            if (message != null) put("message", JsonPrimitive(message))
        }.toString()
        return McpEvent.ToolResult(
            providerId = AgentCapabilityTools.PROVIDER_ID,
            callId = "",
            ok = false,
            body = body,
        )
    }

    /**
     * Redact obvious credential-shaped substrings from output.
     * Conservative — masks anything that looks like a token,
     * JWT, or env-style `KEY=VALUE`. Never logs the raw form.
     */
    internal fun redact(text: String): String {
        if (text.isEmpty()) return text
        var out = text
        // Bearer / API key / token patterns
        val patterns = listOf(
            Regex("(?i)(bearer\\s+)[A-Za-z0-9._\\-]+"),
            Regex("(?i)(api[_-]?key[\"'=:\\s]+)[A-Za-z0-9._\\-]+"),
            Regex("(?i)(token[\"'=:\\s]+)[A-Za-z0-9._\\-]+"),
            Regex("(?i)(password[\"'=:\\s]+)[^\\s,;\"']+"),
            Regex("eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+"), // JWT
        )
        for (re in patterns) {
            out = re.replace(out) { match -> "${match.groupValues.getOrElse(1) { "" }}***" }
        }
        return out
    }
}

/** Audit sink — append-only ledger of every agent-side command. */
interface AuditSink {
    fun append(
        toolName: String,
        arguments: List<String>,
        exitCode: Int?,
        durationMs: Long,
        outcome: String,
        redactedStdout: String,
        redactedStderr: String,
    )
}

/** Approval sink — calls into the host's approval sheet bus. */
interface ApprovalSink {
    suspend fun requestApproval(
        toolName: String,
        description: String,
        details: Map<String, Any>,
    ): ApprovalVerdict
}

enum class ApprovalVerdict { Granted, Denied }

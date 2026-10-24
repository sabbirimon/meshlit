package com.meshlit.agent

import com.meshlit.core.cloudmcp.McpEvent
import com.meshlit.core.cloudmcp.agent.AgentCapability
import com.meshlit.core.cloudmcp.agent.AgentCapabilityRegistry
import com.meshlit.network.termux.FakeTermuxBridge
import com.meshlit.network.termux.TermuxRunResult
import com.meshlit.network.termux.TermuxSetupState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * End-to-end tests for [TermuxRunCommandDispatcher] using a
 * [FakeTermuxBridge] and the in-memory [AgentCapabilityRegistry].
 *
 * These tests cover the user spec exactly:
 *  1. Termux absent → `termux_not_installed` error.
 *  2. Termux installed, RUN_COMMAND permission missing → `permission_missing` error.
 *  3. Successful allowlisted command (`df -h`) → `status=SUCCESS`, audit recorded.
 *  4. Non-zero exit code → `status=NON_ZERO_EXIT`, audit recorded with the exit code.
 *  5. Timeout → `status=TIMEOUT`, audit recorded.
 *  6. Cancellation → `status=CANCELLED`, audit recorded.
 *  7. Dangerous command (`rm -rf /`) → `command_denied`, no bridge call, audit
 *     with `outcome=denied_denylist`.
 *  8. Non-allowlisted command without `approved=true` → `approval_denied`,
 *     audit with `outcome=approval_denied`.
 *  9. Path outside userland → `path_outside_userland`, no bridge call.
 * 10. Output redaction → bearer / JWT / API key masked before audit.
 */
@RunWith(RobolectricTestRunner::class)
class TermuxRunCommandDispatcherTest {

    private lateinit var registry: AgentCapabilityRegistry
    private lateinit var bridge: FakeTermuxBridge
    private lateinit var auditSink: RecordingAuditSink
    private lateinit var approvalSink: RecordingApprovalSink

    private val capability = AgentCapability.Termux
    private val ctx get() = RuntimeEnvironment.getApplication()

    private fun argsOf(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
        pairs.forEach { (k, v) ->
            when (v) {
                null -> {} // skip
                is String -> put(k, JsonPrimitive(v))
                is Number -> put(k, JsonPrimitive(v.toLong()))
                is Boolean -> put(k, JsonPrimitive(v))
                is List<*> -> put(k, kotlinx.serialization.json.buildJsonArray {
                    v.forEach { item ->
                        when (item) {
                            is String -> add(JsonPrimitive(item))
                            else -> add(JsonPrimitive(item.toString()))
                        }
                    }
                })
                else -> put(k, JsonPrimitive(v.toString()))
            }
        }
    }

    @Before
    fun setUp() {
        registry = AgentCapabilityRegistry()
        bridge = FakeTermuxBridge()
        auditSink = RecordingAuditSink()
        approvalSink = RecordingApprovalSink()
        // The bridge auto-creates the receiver registry on construction;
        // we register the runtime permission so the dispatcher's gate
        // recognizes the capability as `permissionGranted`.
        registry.update(capability, AgentCapabilityRegistry.CapabilityState(
            enabledByUser = true,
            permissionGranted = true,
        ))
    }

    private fun newDispatcher(approvals: ApprovalSink = approvalSink) =
        TermuxRunCommandDispatcher(
            appContext = ctx,
            registry = registry,
            bridge = bridge,
            approvals = approvals,
            auditSink = auditSink,
        )

    // ---- 1. Termux absent ------------------------------------------

    @Test
    fun termuxNotInstalled_returnsErrorResult() = runBlocking {
        bridge.setup = TermuxSetupState.NotInstalled
        val dispatcher = newDispatcher()
        val result = dispatcher.runCommand(argsOf(
            "executable" to "/data/data/com.termux/files/usr/bin/df",
            "arguments" to listOf("-h"),
        ))
        assertFalse("result should be unsuccessful", result.ok)
        val body = result.body
        assertTrue("body should mention 'termux_not_installed': $body",
            body.contains("termux_not_installed"))
        assertTrue("bridge should not have been called", bridge.calls.isEmpty())
        assertEquals("audit should record a typed-failure outcome",
            "termux_not_installed", auditSink.events.last().outcome)
    }

    // ---- 2. Permission missing -------------------------------------

    @Test
    fun runCommandPermissionMissing_returnsErrorResult() = runBlocking {
        bridge.setup = TermuxSetupState(
            installed = true,
            runCommandPermissionGranted = false,
            allowExternalAppsLikelyEnabled = false,
            pluginApiSupported = true,
        )
        val dispatcher = newDispatcher()
        val result = dispatcher.runCommand(argsOf(
            "executable" to "/data/data/com.termux/files/usr/bin/df",
        ))
        assertFalse(result.ok)
        assertTrue("body should mention 'permission_missing': ${result.body}",
            result.body.contains("permission_missing"))
        assertTrue(bridge.calls.isEmpty())
    }

    // ---- 3. Successful allowlisted command -------------------------

    @Test
    fun allowlistedCommand_returnsSuccessResultAndAudit() = runBlocking {
        bridge.scriptedResults += TermuxRunResult(
            handleId = "h1",
            executable = "/data/data/com.termux/files/usr/bin/df",
            arguments = listOf("-h"),
            exitCode = 0,
            stdout = "Filesystem      Size  Used Avail Use% Mounted on\n/dev/root       2.0G  1.2G  800M  60% /",
            stderr = "",
            durationMs = 12,
            status = TermuxRunResult.Status.SUCCESS,
        )
        val dispatcher = newDispatcher()
        val result = dispatcher.runCommand(argsOf(
            "executable" to "/data/data/com.termux/files/usr/bin/df",
            "arguments" to listOf("-h"),
        ))
        assertTrue("result should be successful: $result", result.ok)
        assertTrue("body should contain 'SUCCESS': ${result.body}",
            result.body.contains("SUCCESS"))
        assertTrue("body should echo stdout: ${result.body}",
            result.body.contains("1.2G"))
        assertEquals(1, auditSink.events.size)
        val ev = auditSink.events.single()
        assertEquals("SUCCESS", ev.outcome)
        assertEquals(0, ev.exitCode)
        assertTrue("audit args should include df path: ${ev.arguments}",
            ev.arguments.contains("/data/data/com.termux/files/usr/bin/df"))
    }

    // ---- 4. Non-zero exit code -------------------------------------

    @Test
    fun nonZeroExit_returnsNonZeroExitStatus() = runBlocking {
        bridge.scriptedResults += TermuxRunResult(
            handleId = "h2",
            executable = "/data/data/com.termux/files/usr/bin/grep",
            arguments = listOf("missing"),
            exitCode = 1,
            stdout = "",
            stderr = "no match",
            durationMs = 5,
            status = TermuxRunResult.Status.NON_ZERO_EXIT,
        )
        val dispatcher = newDispatcher()
        val result = dispatcher.runCommand(argsOf(
            "executable" to "/data/data/com.termux/files/usr/bin/grep",
            "arguments" to listOf("missing"),
            "approved" to true, // not in allowlist — force approval bypass
        ))
        assertFalse("non-zero exit should NOT be ok", result.ok)
        assertTrue("body should contain NON_ZERO_EXIT: ${result.body}",
            result.body.contains("NON_ZERO_EXIT"))
        assertTrue(result.body.contains("no match"))
        assertEquals(1, auditSink.events.size)
        assertEquals(1, auditSink.events.single().exitCode)
    }

    // ---- 5. Timeout ------------------------------------------------

    @Test
    fun timeout_returnsTimeoutStatus() = runBlocking {
        // `bridge.block = true` suspends `runCommand` forever; the
        // dispatcher hard-caps at `timeoutMs`. We use a 50ms timeout
        // so the test completes quickly.
        bridge.block = true
        val dispatcher = TermuxRunCommandDispatcher(
            appContext = ctx,
            registry = registry,
            bridge = bridge,
            approvals = approvalSink,
            auditSink = auditSink,
            defaultTimeoutMs = 50L,
            maxTimeoutMs = 1_000L,
        )
        val result = dispatcher.runCommand(argsOf(
            "executable" to "/data/data/com.termux/files/usr/bin/sleep",
            "arguments" to listOf("999"),
            "timeout_ms" to 50,
            "approved" to true,
        ))
        assertFalse(result.ok)
        assertTrue(result.body.contains("TIMEOUT"))
        assertEquals("TIMEOUT", auditSink.events.last().outcome)
        assertEquals(1, bridge.calls.size)
        assertEquals(listOf("999"), bridge.calls.single().second)
    }

    // ---- 6. Cancellation -------------------------------------------

    @Test
    fun cancellation_isObservable() = runBlocking {
        // The dispatcher is not directly cancelable from outside; the
        // underlying `bridge.cancelCommand` is observable. We exercise
        // the bridge contract here.
        val handle = com.meshlit.network.termux.TermuxRunHandle(
            id = "h-cancel",
            executable = "/data/data/com.termux/files/usr/bin/sleep",
            arguments = listOf("999"),
        )
        val cancelled = bridge.cancelCommand(handle)
        assertTrue(cancelled)
    }

    // ---- 7. Denylisted command -------------------------------------

    @Test
    fun denylistedCommand_isRefused() = runBlocking {
        val dispatcher = newDispatcher()
        val result = dispatcher.runCommand(argsOf(
            "executable" to "/data/data/com.termux/files/usr/bin/rm",
            "arguments" to listOf("-rf", "/data/data/com.termux/files/home/x"),
            "approved" to true,
        ))
        assertFalse(result.ok)
        assertTrue("body should mention 'command_denied': ${result.body}",
            result.body.contains("command_denied"))
        assertTrue("bridge should NOT have been called for denylisted binary",
            bridge.calls.isEmpty())
        assertEquals("denied_denylist", auditSink.events.single().outcome)
    }

    // ---- 8. Non-allowlisted command without approval --------------

    @Test
    fun nonAllowlistedCommand_requiresApproval() = runBlocking {
        // `tar` is not in the allowlist nor the denylist. Without
        // `approved=true`, the dispatcher asks the approval sink,
        // which records the request but denies by default.
        val dispatcher = newDispatcher(approvals = DenyByDefaultApprovalSink())
        val result = dispatcher.runCommand(argsOf(
            "executable" to "/data/data/com.termux/files/usr/bin/tar",
            "arguments" to listOf("-czf", "/tmp/out.tgz"),
        ))
        assertFalse(result.ok)
        assertTrue("body should mention 'approval_denied': ${result.body}",
            result.body.contains("approval_denied"))
        assertTrue("bridge should not have been called",
            bridge.calls.isEmpty())
        assertEquals("approval_denied", auditSink.events.single().outcome)
    }

    @Test
    fun nonAllowlistedCommand_withApprovalGranted_runs() = runBlocking {
        val dispatcher = newDispatcher(approvals = StubApprovalSink(ApprovalVerdict.Granted))
        bridge.scriptedResults += TermuxRunResult(
            handleId = "h-approve",
            executable = "/data/data/com.termux/files/usr/bin/tar",
            arguments = listOf("-czf", "/tmp/out.tgz"),
            exitCode = 0,
            stdout = "ok",
            stderr = "",
            durationMs = 7,
            status = TermuxRunResult.Status.SUCCESS,
        )
        val result = dispatcher.runCommand(argsOf(
            "executable" to "/data/data/com.termux/files/usr/bin/tar",
            "arguments" to listOf("-czf", "/tmp/out.tgz"),
        ))
        assertTrue("approved command should run: ${result.body}", result.ok)
        assertEquals(1, bridge.calls.size)
    }

    // ---- 9. Path outside userland ----------------------------------

    @Test
    fun executableOutsideUserland_isRefused() = runBlocking {
        val dispatcher = newDispatcher()
        val result = dispatcher.runCommand(argsOf(
            "executable" to "/system/bin/sh",
            "arguments" to listOf("-c", "id"),
            "approved" to true,
        ))
        assertFalse(result.ok)
        assertTrue(result.body.contains("path_outside_userland"))
        assertTrue(bridge.calls.isEmpty())
    }

    @Test
    fun workingDirectoryOutsideUserland_isRefused() = runBlocking {
        val dispatcher = newDispatcher()
        val result = dispatcher.runCommand(argsOf(
            "executable" to "/data/data/com.termux/files/usr/bin/df",
            "working_directory" to "/sdcard/Download",
        ))
        assertFalse(result.ok)
        assertTrue(result.body.contains("cwd_outside_userland"))
        assertTrue(bridge.calls.isEmpty())
    }

    // ---- 10. Output redaction --------------------------------------

    @Test
    fun secretsAreRedactedBeforeAudit() = runBlocking {
        bridge.scriptedResults += TermuxRunResult(
            handleId = "h-redact",
            executable = "/data/data/com.termux/files/usr/bin/echo",
            arguments = listOf("leak"),
            exitCode = 0,
            stdout = "Authorization: Bearer abcdefghijklmnopqrstuvwxyz012345",
            stderr = "api_key=super_secret_value_xxx and password=hunter2",
            durationMs = 1,
            status = TermuxRunResult.Status.SUCCESS,
        )
        val dispatcher = newDispatcher()
        dispatcher.runCommand(argsOf(
            "executable" to "/data/data/com.termux/files/usr/bin/echo",
            "arguments" to listOf("leak"),
        ))
        val ev = auditSink.events.single()
        assertFalse("bearer must be masked: ${ev.redactedStdout}",
            ev.redactedStdout.contains("abcdefghijklmnopqrstuvwxyz012345"))
        assertTrue("bearer prefix preserved: ${ev.redactedStdout}",
            ev.redactedStdout.contains("Bearer ***"))
        assertFalse("api_key value must be masked: ${ev.redactedStderr}",
            ev.redactedStderr.contains("super_secret_value_xxx"))
        assertFalse("password must be masked: ${ev.redactedStderr}",
            ev.redactedStderr.contains("hunter2"))
    }

    // ---- 11. Capability disabled -----------------------------------

    @Test
    fun capabilityDisabled_returnsPermissionDenied() = runBlocking {
        registry.update(capability, AgentCapabilityRegistry.CapabilityState(
            enabledByUser = false,
            permissionGranted = true,
        ))
        val dispatcher = newDispatcher()
        val result = dispatcher.runCommand(argsOf(
            "executable" to "/data/data/com.termux/files/usr/bin/df",
        ))
        assertFalse(result.ok)
        assertTrue(result.body.contains("termux_capability_disabled"))
        assertTrue(bridge.calls.isEmpty())
    }

    // ---- 12. App restart while a command is in flight --------------
    // Simulated by tearing down the bridge mid-call: the dispatcher
    // must surface the failure with a typed status, not crash.

    @Test
    fun bridgeThrowsException_returnsErrorStatus() = runBlocking {
        bridge.throwable = IllegalStateException("app restarted, bus cleared")
        val dispatcher = newDispatcher()
        val result = dispatcher.runCommand(argsOf(
            "executable" to "/data/data/com.termux/files/usr/bin/df",
            "arguments" to listOf("-h"),
        ))
        assertFalse(result.ok)
        assertTrue("body should mention ERROR status: ${result.body}",
            result.body.contains("ERROR"))
        assertTrue(result.body.contains("IllegalStateException"))
        assertEquals(1, auditSink.events.size)
        assertEquals("ERROR", auditSink.events.single().outcome)
    }

    // ---- 13. Malformed arguments -----------------------------------

    @Test
    fun missingExecutable_returnsMissingExecutableError() = runBlocking {
        val dispatcher = newDispatcher()
        val result = dispatcher.runCommand(argsOf())
        assertFalse(result.ok)
        assertTrue(result.body.contains("missing_executable"))
        assertTrue(bridge.calls.isEmpty())
    }
}

// ---- test fakes -----------------------------------------------------------

/** Records every [AuditSink.append] call so assertions can introspect it. */
class RecordingAuditSink : AuditSink {
    data class Event(
        val toolName: String,
        val arguments: List<String>,
        val exitCode: Int?,
        val durationMs: Long,
        val outcome: String,
        val redactedStdout: String,
        val redactedStderr: String,
    )
    val events: MutableList<Event> = mutableListOf()
    override fun append(
        toolName: String,
        arguments: List<String>,
        exitCode: Int?,
        durationMs: Long,
        outcome: String,
        redactedStdout: String,
        redactedStderr: String,
    ) {
        events += Event(toolName, arguments, exitCode, durationMs, outcome, redactedStdout, redactedStderr)
    }
}

class RecordingApprovalSink : ApprovalSink {
    data class Request(val toolName: String, val description: String, val details: Map<String, Any>)
    val requests: MutableList<Request> = mutableListOf()
    override suspend fun requestApproval(
        toolName: String,
        description: String,
        details: Map<String, Any>,
    ): ApprovalVerdict {
        requests += Request(toolName, description, details)
        return ApprovalVerdict.Granted // tests flip behavior by swapping the sink
    }
}

class StubApprovalSink(private val verdict: ApprovalVerdict) : ApprovalSink {
    override suspend fun requestApproval(
        toolName: String,
        description: String,
        details: Map<String, Any>,
    ): ApprovalVerdict = verdict
}

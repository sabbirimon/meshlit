package com.meshlit.agent.hooks

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.meshlit.core.common.ConfigScript
import com.meshlit.core.common.ConfigScriptStep
import com.meshlit.core.common.HookDefinition
import com.meshlit.core.common.HookResult
import com.meshlit.core.common.HookTrigger
import com.meshlit.inference.PeerRegistry
import com.meshlit.scripts.ConfigScriptRunner
import com.meshlit.scripts.ScriptLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * JVM tests for [HookEngine]. We exercise the trigger filter, the
 * master kill switch, the per-hook kill switch, the timeout path,
 * and the audit sink integration. No Robolectric — everything the
 * engine touches is in-process.
 */
class HookEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun scriptWithSet(name: String, key: String, value: String): ConfigScript =
        ConfigScript(
            name = name,
            description = "test",
            steps = listOf(ConfigScriptStep.Set(key = key, value = value)),
        )

    private fun scriptWithWait(name: String, durationMs: Long): ConfigScript =
        ConfigScript(
            name = name,
            description = "wait",
            steps = listOf(ConfigScriptStep.Wait(label = "w", durationMs = durationMs)),
        )

    private fun scriptWithAssert(name: String, expression: String): ConfigScript =
        ConfigScript(
            name = name,
            description = "assert",
            steps = listOf(ConfigScriptStep.Assert(label = "a", expression = expression)),
        )

    private fun hook(
        id: String,
        name: String,
        trigger: HookTrigger,
        script: ConfigScript,
        enabled: Boolean = true,
        timeoutMs: Long = 5_000L,
    ): HookDefinition = HookDefinition(
        id = id, name = name, enabled = enabled,
        trigger = trigger, script = script, timeoutMs = timeoutMs,
    )

    private class Harness(val sink: HookAuditSink, val engine: HookEngine) {
        val auditLines: List<String> get() = sink.readAllLines()
    }

    private fun build(hooks: List<HookDefinition>, master: Boolean = true): Harness {
        val sink = HookAuditSink(
            auditFile = File(tempFolder.root, "hooks-audit.jsonl"),
        )
        val hooksFlow = MutableStateFlow(hooks)
        val masterFlow = MutableStateFlow(master)
        val library = ScriptLibrary()
        // The runner needs a PeerRegistry for the (unused in these
        // tests) `Peer` target resolution. We stand up a real one
        // with a temp-file DataStore — same approach PeerRegistryTest
        // uses — so the test stays free of Android dependencies.
        val dsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = dsScope,
            produceFile = { File(tempFolder.root, "peers.preferences_pb") },
        )
        val registry = PeerRegistry(ds)
        val runner = ConfigScriptRunner(library, registry)
        val engine = HookEngine(
            hooksFlow = hooksFlow,
            masterEnabledFlow = masterFlow,
            scriptLibrary = library,
            runner = runner,
            auditSink = sink,
        )
        return Harness(sink, engine)
    }

    @Test
    fun `fire only runs hooks whose trigger matches`() = runBlocking {
        val h = build(
            listOf(
                hook("a", "pre", HookTrigger.PreToolCall, scriptWithSet("a", "k", "v")),
                hook("b", "post", HookTrigger.PostToolCall, scriptWithSet("b", "k", "v")),
            ),
        )
        h.engine.fire(HookTrigger.PreToolCall, emptyMap())
        // The PreToolCall hook ran; the PostToolCall hook did not.
        // We assert via the line count in the sink (one line per
        // successful fire). PostToolCall's hook leaves no trace.
        // Allow the dispatcher some time to flush.
        withTimeout(2_000) {
            while (h.auditLines.isEmpty()) kotlinx.coroutines.delay(50)
        }
        assertEquals(1, h.auditLines.size)
        assertTrue(h.auditLines.single().contains("\"hook_id\":\"a\""))
    }

    @Test
    fun `fire is a no-op when master kill switch is off`() = runBlocking {
        val h = build(
            listOf(hook("a", "pre", HookTrigger.PreToolCall, scriptWithSet("a", "k", "v"))),
            master = false,
        )
        h.engine.fire(HookTrigger.PreToolCall, emptyMap())
        // Wait a beat to confirm no audit line ever lands.
        kotlinx.coroutines.delay(200)
        assertEquals(0, h.auditLines.size)
    }

    @Test
    fun `per-hook enabled false skips that hook only`() = runBlocking {
        val h = build(
            listOf(
                hook("a", "on", HookTrigger.PreToolCall, scriptWithSet("a", "k", "v"), enabled = true),
                hook("b", "off", HookTrigger.PreToolCall, scriptWithSet("b", "k", "v"), enabled = false),
            ),
        )
        h.engine.fire(HookTrigger.PreToolCall, emptyMap())
        withTimeout(2_000) {
            while (h.auditLines.isEmpty()) kotlinx.coroutines.delay(50)
        }
        // The second hook must NOT fire (enabled = false).
        assertEquals(1, h.auditLines.size)
        assertTrue(h.auditLines.single().contains("\"hook_id\":\"a\""))
        assertTrue(!h.auditLines.single().contains("\"hook_id\":\"b\""))
    }

    @Test
    fun `timeout is coerced to TIMEOUT outcome`() = runBlocking {
        val h = build(
            listOf(
                hook(
                    "slow", "slow-wait",
                    HookTrigger.PreToolCall,
                    scriptWithWait("slow", durationMs = 10_000L),
                    timeoutMs = 100L,
                ),
            ),
        )
        val result = h.engine.firePre(HookTrigger.PreToolCall, emptyMap())
        assertNotNull(result)
        assertTrue("expected failure result, got $result", result.lastError == "timeout")
        assertEquals(1, h.auditLines.size)
        assertTrue(h.auditLines.single().contains("\"outcome\":\"TIMEOUT\""))
    }

    @Test
    fun `failing hook does not break the host`() = runBlocking {
        // expression = "false" makes the runner emit a StepFail +
        // Done(success=false). The engine's HookResult must reflect
        // that without throwing.
        val h = build(
            listOf(
                hook(
                    "bad", "bad-assert",
                    HookTrigger.PreToolCall,
                    scriptWithAssert("bad", "false"),
                ),
            ),
        )
        val result = h.engine.firePre(HookTrigger.PreToolCall, emptyMap())
        assertNotNull(result)
        assertFalse(result.success)
        assertTrue(h.auditLines.single().contains("\"outcome\":\"ERROR\""))
    }

    @Test
    fun `successful hook records SUCCESS outcome`() = runBlocking {
        val h = build(
            listOf(
                hook("ok", "ok", HookTrigger.OnTurnEnd, scriptWithSet("ok", "k", "v")),
            ),
        )
        h.engine.fire(HookTrigger.OnTurnEnd, mapOf("final_chars" to "100"))
        withTimeout(2_000) {
            while (h.auditLines.isEmpty()) kotlinx.coroutines.delay(50)
        }
        assertEquals(1, h.auditLines.size)
        val line = h.auditLines.single()
        assertTrue(line.contains("\"outcome\":\"SUCCESS\""))
        assertTrue(line.contains("\"trigger\":\"on_turn_end\""))
        assertTrue(line.contains("vars_summary"))
    }

    @Test
    fun `firePre with no matching hooks returns NoOp`() = runBlocking {
        val h = build(emptyList())
        val result = h.engine.firePre(HookTrigger.OnError, mapOf("phase" to "x"))
        assertEquals(HookResult.NoOp, result)
        assertEquals(0, h.auditLines.size)
    }
}
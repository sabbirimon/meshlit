package com.meshlit.agent.hooks

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.meshlit.core.common.ConfigScript
import com.meshlit.core.common.ConfigScriptStep
import com.meshlit.core.common.ConfigScriptTarget
import com.meshlit.core.common.ScriptEvent
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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import org.junit.After
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

    private class Harness(val sink: HookAuditSink, val engine: HookEngine, val runner: ConfigScriptRunner,
        val hooksFlow: MutableStateFlow<List<HookDefinition>>, val masterFlow: MutableStateFlow<Boolean>, val storeScope: CoroutineScope) {
        val auditLines: List<String> get() = sink.readAllLines()
    }
    private val harnesses = mutableListOf<Harness>()
    @After fun closeHarnesses() { harnesses.forEach { it.engine.shutdown(); it.runner.shutdown(); it.storeScope.cancel() } }

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
        return Harness(sink, engine, runner, hooksFlow, masterFlow, dsScope).also { harnesses += it }
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

    @Test fun `timeout cancels the actual script and never emits late success`() = runBlocking {
        val script = ConfigScript(name = "bounded", steps = listOf(ConfigScriptStep.Wait(durationMs = 5000), ConfigScriptStep.Set(key = "late", value = "bad")))
        val h = build(listOf(hook("bounded", "bounded", HookTrigger.PreToolCall, script, timeoutMs = 40)))
        assertFalse(h.engine.firePre(HookTrigger.PreToolCall, emptyMap()).success)
        val terminal = h.runner.events.value
        assertTrue(terminal is ScriptEvent.Done && !terminal.success)
        delay(80)
        assertEquals(terminal, h.runner.events.value)
        assertFalse(h.auditLines.any { it.contains("\"outcome\":\"SUCCESS\"") })
    }

    @Test fun `master and per-hook revocation stop an in-flight wait`() = runBlocking {
        val original = hook("wait", "wait", HookTrigger.PreToolCall, scriptWithWait("wait", 5000))
        val h = build(listOf(original))
        val first = async { h.engine.firePre(HookTrigger.PreToolCall, emptyMap()) }
        withTimeout(2000) { while (h.runner.events.value !is ScriptEvent.Start) delay(5) }
        h.masterFlow.value = false
        assertEquals("permission revoked", withTimeout(1000) { first.await() }.lastError)
        h.masterFlow.value = true
        val second = async { h.engine.firePre(HookTrigger.PreToolCall, emptyMap()) }
        withTimeout(2000) { while (h.runner.events.value !is ScriptEvent.Start) delay(5) }
        h.hooksFlow.value = listOf(original.copy(enabled = false))
        assertEquals("permission revoked", withTimeout(1000) { second.await() }.lastError)
        assertTrue(h.auditLines.all { it.contains("\"outcome\":\"DENIED\"") })
    }

    @Test fun `caller cancellation remains cancellation and stops the runner`() = runBlocking {
        val h = build(listOf(hook("wait", "wait", HookTrigger.PreToolCall, scriptWithWait("wait", 5000))))
        val call = async { h.engine.firePre(HookTrigger.PreToolCall, emptyMap()) }
        withTimeout(2000) { while (h.runner.events.value !is ScriptEvent.Start) delay(5) }
        call.cancelAndJoin()
        assertTrue(call.isCancelled)
        assertTrue((h.runner.events.value as ScriptEvent.Done).success.not())
    }

    @Test fun `concurrent scripts have isolated results and remote execution is unavailable`() = runBlocking {
        val h = build(emptyList())
        val bad = async { h.runner.execute(ConfigScript(name = "bad", steps = listOf(ConfigScriptStep.Wait(durationMs = 30), ConfigScriptStep.Assert(expression = "false")))) }
        val good = async { h.runner.execute(scriptWithSet("good", "onlyGood", "yes")) }
        assertTrue(good.await().success)
        assertFalse(bad.await().success)
        assertFalse(bad.await().variables.containsKey("onlyGood"))
        val remote = h.runner.execute(ConfigScript(name = "remote", steps = listOf(ConfigScriptStep.Set(target = ConfigScriptTarget.Peer("127.0.0.1"), key = "notRun", value = "yes"))))
        assertFalse(remote.success); assertFalse(remote.variables.containsKey("notRun"))
        assertFalse(remote.events.any { it is ScriptEvent.StepOk })
        val nested = h.runner.execute(ConfigScript(name = "recursive", steps = listOf(ConfigScriptStep.Step(scriptName = "recursive", continueOnError = true))))
        assertFalse(nested.success); assertTrue(nested.events.size < 40)
    }

    @Test fun `hook variables reach the script and unmet wait conditions fail honestly`() = runBlocking {
        val script = ConfigScript(name = "condition", steps = listOf(ConfigScriptStep.Wait(untilVar = "ready", expectedValue = "yes", timeoutMs = 30)))
        val h = build(listOf(hook("condition", "condition", HookTrigger.PreToolCall, script)))
        assertTrue(h.engine.firePre(HookTrigger.PreToolCall, mapOf("ready" to "yes")).success)
        assertFalse(h.engine.firePre(HookTrigger.PreToolCall, mapOf("ready" to "no")).success)
    }
}

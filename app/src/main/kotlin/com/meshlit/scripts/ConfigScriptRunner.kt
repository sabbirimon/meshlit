package com.meshlit.scripts

import com.meshlit.core.common.*
import com.meshlit.inference.PeerRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicInteger

class ScriptExecutionRevoked : CancellationException("Script permission revoked")
private class ScriptLimitExceeded : IllegalStateException("Script execution limit exceeded")
data class ScriptRunResult(val success: Boolean, val variables: Map<String, String>, val events: List<ScriptEvent>)

/** Bounded local DSL execution. Remote dispatch is unavailable, never a successful no-op. */
class ConfigScriptRunner(private val scriptLibrary: ScriptLibrary, private val peerRegistry: PeerRegistry) {
    private val _events = MutableStateFlow<ScriptEvent?>(null)
    val events: StateFlow<ScriptEvent?> = _events.asStateFlow()
    private val _stream = MutableSharedFlow<ScriptEvent>(extraBufferCapacity = 256)
    val eventStream: Flow<ScriptEvent> = _stream.asSharedFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Compatibility launcher; lifecycle-aware callers use execute in their own scope. */
    fun run(script: ConfigScript): Deferred<ScriptRunResult> = scope.async { execute(script) }
    fun shutdown() { scope.cancel() }

    /** Caller-owned cancellation and a result belonging only to this invocation. */
    suspend fun execute(script: ConfigScript, initialVars: Map<String, String> = emptyMap(),
        authorized: () -> Boolean = { true }, onEvent: (ScriptEvent) -> Unit = {}): ScriptRunResult {
        val history = mutableListOf<ScriptEvent>()
        val variables = HashMap(initialVars)
        val count = AtomicInteger()
        val library = scriptLibrary.scripts.value.associateBy { it.name } + (script.name to script)
        fun emit(event: ScriptEvent) {
            synchronized(history) { if (history.size < 1030) history += event }
            _events.value = event; _stream.tryEmit(event); onEvent(event)
        }
        suspend fun checkAllowed() { currentCoroutineContext().ensureActive(); if (!authorized()) throw ScriptExecutionRevoked() }
        fun validVariables(vars: Map<String, String>) {
            require(vars.size <= 128 && vars.all { (key, value) -> key.length in 1..128 && value.length <= 4096 }) { "Variable limits exceeded" }
        }
        suspend fun sequence(steps: List<ConfigScriptStep>, vars: MutableMap<String, String>, depth: Int,
            runStep: suspend (ConfigScriptStep, Int, MutableMap<String, String>, Int) -> Boolean): Boolean {
            require(steps.size <= 256) { "Too many script steps" }
            var ok = true
            for ((index, step) in steps.withIndex()) {
                checkAllowed()
                if (!runStep(step, index, vars, depth)) { ok = false; if (!step.continueOnError) break }
            }
            return ok
        }
        lateinit var runStep: suspend (ConfigScriptStep, Int, MutableMap<String, String>, Int) -> Boolean
        runStep = { step, index, vars, depth ->
            checkAllowed()
            var success = false
            var failure = "Nested step failed"
            try {
                if (depth > 16 || count.incrementAndGet() > 512) throw ScriptLimitExceeded()
                require(step.timeoutMs in 1..600_000) { "Invalid step deadline" }
                val completed = withTimeoutOrNull(step.timeoutMs) {
                    when (step.target) {
                        ConfigScriptTarget.Local -> Unit
                        ConfigScriptTarget.All -> check(peerRegistry.snapshot().isEmpty()) { "Remote script execution is unavailable" }
                        is ConfigScriptTarget.Peer -> error("Remote script execution is unavailable")
                    }
                    checkAllowed()
                    val ok = when (step) {
                        is ConfigScriptStep.Set -> { require(step.key.length in 1..128 && step.value.length <= 4096 && (step.key in vars || vars.size < 128)) { "Variable limits exceeded" }; vars[step.key] = step.value; true }
                        is ConfigScriptStep.Add -> { val next = listOfNotNull(vars[step.list], step.item).joinToString("|"); require(step.list.length in 1..128 && next.length <= 4096 && (step.list in vars || vars.size < 128)) { "Variable limits exceeded" }; vars[step.list] = next; true }
                        is ConfigScriptStep.Wait -> {
                            require(step.durationMs in 0..600_000) { "Invalid wait duration" }
                            var remaining = step.durationMs
                            while (remaining > 0) { val part = minOf(remaining, 25); delay(part); remaining -= part; checkAllowed() }
                            while (step.untilVar != null && if (step.expectedValue == null) vars[step.untilVar].isNullOrEmpty() else vars[step.untilVar] != step.expectedValue) { delay(25); checkAllowed() }
                            true
                        }
                        is ConfigScriptStep.Assert -> { check(step.expression.equals("true", ignoreCase = true)) { "Assertion is not true" }; true }
                        is ConfigScriptStep.Parallel -> coroutineScope {
                            require(step.children.size <= 32) { "Too many parallel steps" }
                            step.children.mapIndexed { i, child -> async { runStep(child, i, HashMap(vars), depth + 1) } }.awaitAll().all { it }
                        }
                        is ConfigScriptStep.Repeat -> {
                            require(step.count in 0..512) { "Invalid repeat count" }
                            var ok = true
                            for (iteration in 0 until step.count) {
                                checkAllowed(); vars["index"] = iteration.toString()
                                if (!sequence(step.children, vars, depth + 1, runStep)) { ok = false; if (!step.continueOnError) break }
                            }
                            ok
                        }
                        is ConfigScriptStep.Step -> {
                            val nested = library[step.scriptName] ?: error("Referenced script is unavailable")
                            check(nested.schemaVersion == 1) { "Unsupported script version" }
                            sequence(nested.steps, vars, depth + 1, runStep)
                        }
                    }
                    checkAllowed(); yield(); ok
                }
                success = completed == true
                if (completed == null) failure = "Step deadline exceeded"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (limit: ScriptLimitExceeded) { throw limit }
            catch (error: Exception) { failure = error.message?.take(160) ?: "Step failed" }
            emit(if (success) ScriptEvent.StepOk(index, System.currentTimeMillis(), step.kind())
                else ScriptEvent.StepFail(index, System.currentTimeMillis(), step.kind(), "script.step.fail", failure))
            success
        }
        emit(ScriptEvent.Start(0, System.currentTimeMillis(), script.name))
        var success = false
        try {
            require(script.schemaVersion == 1 && script.name.length in 1..160) { "Unsupported script" }
            validVariables(variables)
            success = withTimeoutOrNull(600_000) { sequence(script.steps, variables, 0, runStep) } == true
            checkAllowed()
        } catch (cancelled: CancellationException) {
            success = false
            emit(ScriptEvent.StepFail(0, System.currentTimeMillis(), "cancelled", "script.cancelled", "Script stopped before completion"))
            throw cancelled
        } catch (_: Exception) {
            success = false
            emit(ScriptEvent.StepFail(0, System.currentTimeMillis(), "script", "script.invalid", "Script validation or execution failed"))
        } finally { emit(ScriptEvent.Done((script.steps.size - 1).coerceAtLeast(0), System.currentTimeMillis(), success)) }
        return ScriptRunResult(success, variables.toMap(), synchronized(history) { history.toList() })
    }
}

private fun ConfigScriptStep.kind(): String = when (this) {
    is ConfigScriptStep.Set -> "set"; is ConfigScriptStep.Add -> "add"; is ConfigScriptStep.Wait -> "wait"
    is ConfigScriptStep.Assert -> "assert"; is ConfigScriptStep.Parallel -> "parallel"
    is ConfigScriptStep.Repeat -> "repeat"; is ConfigScriptStep.Step -> "step"
}

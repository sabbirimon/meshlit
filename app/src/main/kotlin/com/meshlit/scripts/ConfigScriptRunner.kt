package com.meshlit.scripts

import com.meshlit.core.common.ConfigScript
import com.meshlit.core.common.ConfigScriptStep
import com.meshlit.core.common.ConfigScriptTarget
import com.meshlit.core.common.ScriptEvent
import com.meshlit.inference.PeerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Walks a [ConfigScript] in a single coroutine.
 *
 * v1 scope: **local execution only**. Remote dispatch is plumbed
 * (we resolve step targets against the live [PeerRegistry] and log
 * which peer each step *would* target) but the network call is
 * deliberately a no-op until the `/v1/scripts/run` HTTP route lands
 * in a follow-up — the type system makes it easy to add later.
 *
 * The runner emits [ScriptEvent]s on a hot [eventStream] and a
 * StateFlow convenience [events]. Both can drive the `ScriptsScreen`
 * "Run" tab.
 *
 * Concurrency:
 *  - Top-level [run] launches on a single SupervisorJob so failures
 *    don't tear the whole scope down.
 *  - `Parallel` step children launch as siblings within their own
 *    structured coroutine scope and are awaited together.
 */
class ConfigScriptRunner(
    private val scriptLibrary: ScriptLibrary,
    private val peerRegistry: PeerRegistry,
) {

    private val _events = MutableStateFlow<ScriptEvent?>(null)
    val events: StateFlow<ScriptEvent?> = _events.asStateFlow()

    private val _stream = MutableSharedFlow<ScriptEvent>(
        replay = 0,
        extraBufferCapacity = 256,
    )
    val eventStream: Flow<ScriptEvent> = _stream.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Execute [script] end-to-end. Returns a [Job] the caller can
     * cancel.
     */
    fun run(script: ConfigScript): Job {
        return scope.launch {
            val startedAt = System.currentTimeMillis()
            emit(ScriptEvent.Start(stepIndex = 0, unixMs = startedAt, scriptName = script.name))
            val vars = HashMap<String, String>()
            var allOk = true
            script.steps.forEachIndexed { idx, step ->
                try {
                    runStep(script, step, idx, vars)
                } catch (t: Throwable) {
                    allOk = false
                    emit(ScriptEvent.StepFail(
                        stepIndex = idx,
                        unixMs = System.currentTimeMillis(),
                        label = step.kind(),
                        tag = "script.step.fail",
                        message = t.message ?: t::class.simpleName ?: "unknown",
                    ))
                    if (!step.continueOnError) {
                        emit(ScriptEvent.Done(
                            stepIndex = idx,
                            unixMs = System.currentTimeMillis(),
                            success = false,
                        ))
                        return@launch
                    }
                }
            }
            emit(ScriptEvent.Done(
                stepIndex = script.steps.size - 1,
                unixMs = System.currentTimeMillis(),
                success = allOk,
            ))
        }
    }

    private suspend fun runStep(
        script: ConfigScript,
        step: ConfigScriptStep,
        idx: Int,
        vars: MutableMap<String, String>,
    ) {
        // Resolve the target list and "execute" each leaf shape —
        // local is real, remote is logged for now.
        val targets = resolveTarget(step.target)
        for (t in targets) {
            when (t) {
                Target.Local -> executeLocal(script, step, vars)
                is Target.Remote -> {
                    // v1: log the intent; no network call. Phase 2
                    // wires `/v1/scripts/run` once it's a fully
                    // tested code path.
                    val known = peerRegistry.snapshot()
                    if (!known.contains(t.host)) {
                        throw IllegalStateException("peer ${t.host} not in registry")
                    }
                }
            }
        }
        emit(ScriptEvent.StepOk(
            stepIndex = idx,
            unixMs = System.currentTimeMillis(),
            label = step.kind(),
        ))
    }

    private suspend fun resolveTarget(target: ConfigScriptTarget): List<Target> = when (target) {
        ConfigScriptTarget.Local -> listOf(Target.Local)
        ConfigScriptTarget.All -> {
            val peers = peerRegistry.snapshot().map { Target.Remote(it) }
            listOf(Target.Local) + peers
        }
        is ConfigScriptTarget.Peer -> listOf(Target.Remote(target.host))
    }

    private suspend fun executeLocal(
        script: ConfigScript,
        step: ConfigScriptStep,
        vars: MutableMap<String, String>,
    ) {
        when (step) {
            is ConfigScriptStep.Set -> { vars[step.key] = step.value }
            is ConfigScriptStep.Add -> {
                val list = vars[step.list]?.split('|')?.toMutableList() ?: mutableListOf()
                list.add(step.item)
                vars[step.list] = list.joinToString("|")
            }
            is ConfigScriptStep.Wait -> {
                if (step.durationMs > 0) delay(step.durationMs)
            }
            is ConfigScriptStep.Assert -> {
                val expected = step.expression.equals("true", ignoreCase = true)
                if (!expected) error("assertion failed: ${step.expression}")
            }
            is ConfigScriptStep.Parallel -> coroutineScope {
                val jobs: List<Deferred<Unit>> = step.children.mapIndexed { i, child ->
                    async {
                        try { runStep(script, child, i, HashMap(vars)) }
                        catch (t: Throwable) { if (!step.continueOnError) throw t }
                    }
                }
                jobs.awaitAll()
            }
            is ConfigScriptStep.Repeat -> {
                for (i in 0 until step.count) {
                    vars["index"] = i.toString()
                    step.children.forEachIndexed { ci, child ->
                        runStep(script, child, ci, vars)
                    }
                }
            }
            is ConfigScriptStep.Step -> {
                val sub = scriptLibrary.load(step.scriptName)
                    ?: throw IllegalStateException("script ${step.scriptName} not in library")
                sub.steps.forEachIndexed { ci, child ->
                    runStep(script, child, ci, vars)
                }
            }
        }
    }

    private fun emit(ev: ScriptEvent) {
        _events.value = ev
        _stream.tryEmit(ev)
    }

    private sealed class Target {
        data object Local : Target()
        data class Remote(val host: String) : Target()
    }
}

private fun ConfigScriptStep.kind(): String = when (this) {
    is ConfigScriptStep.Set -> "set"
    is ConfigScriptStep.Add -> "add"
    is ConfigScriptStep.Wait -> "wait"
    is ConfigScriptStep.Assert -> "assert"
    is ConfigScriptStep.Parallel -> "parallel"
    is ConfigScriptStep.Repeat -> "repeat"
    is ConfigScriptStep.Step -> "step"
}

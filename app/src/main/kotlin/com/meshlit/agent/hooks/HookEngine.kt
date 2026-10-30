package com.meshlit.agent.hooks

import com.meshlit.core.common.ConfigScript
import com.meshlit.core.common.HookContext
import com.meshlit.core.common.HookDefinition
import com.meshlit.core.common.HookResult
import com.meshlit.core.common.HookTrigger
import com.meshlit.core.common.logger
import com.meshlit.scripts.ConfigScriptRunner
import com.meshlit.scripts.ScriptLibrary
import com.meshlit.scripts.ScriptExecutionRevoked
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The runtime side of the hooks feature.
 *
 * Responsibilities:
 *  - Hold the live [HookDefinition] list, sourced from
 *    [com.meshlit.settings.SettingsRepository] via the
 *    `hooksRegistryFlow` (a `Flow<List<HookDefinition>>`).
 *  - Honor the master kill switch (`hooksEnabled`).
 *  - Filter the hook list by [HookTrigger] when [fire] / [firePre]
 *    are called.
 *  - Run each matching hook through [ConfigScriptRunner] with the
 *    trigger's variable bag seeded into the script's `vars` map.
 *  - Write one audit record per invocation via [HookAuditSink].
 *
 * Threading:
 *  - The [fire] path is fire-and-forget on a [SupervisorJob] so a
 *    single failing hook does not tear down the dispatcher's scope.
 *  - The [firePre] path is **suspending** and uses [withTimeoutOrNull]
 *    per-hook. It exists so `pre-tool-call` can run synchronously
 *    enough for a future "deny on assert failure" semantic. v1 ignores
 *    the failure and just logs.
 *
 * Failure model:
 *  - Script failures are audited and surfaced as [HookResult.failure].
 *    Caller cancellation propagates; permission revocation returns a denied result.
 *  - Timeouts are coerced to [HookAuditSink.Outcome.TIMEOUT] with
 *    `lastError = "timeout"`.
 */
class HookEngine(
    private val hooksFlow: StateFlow<List<HookDefinition>>,
    private val masterEnabledFlow: StateFlow<Boolean>,
    private val scriptLibrary: ScriptLibrary,
    private val runner: ConfigScriptRunner,
    private val auditSink: HookAuditSink,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val log = logger("HookEngine")

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    @Volatile private var closed = false

    /**
     * Snapshot of the current hook list. The engine never mutates
     * this — it is fed by the host (typically `MeshlitApplication`)
     * via the [hooksFlow] constructor parameter, re-exposed here so
     * UI code can read it without holding its own collection.
     */
    val hooks: StateFlow<List<HookDefinition>> = hooksFlow

    /**
     * Fire-and-forget dispatch. Each matching enabled hook runs in
     * its own coroutine on the engine's dispatcher. Returns
     * immediately.
     */
    fun fire(trigger: HookTrigger, vars: Map<String, String>) {
        if (closed || !masterEnabledFlow.value) return
        val ctx = HookContext.now(trigger, vars)
        val matching = hooksFlow.value.filter { it.enabled && it.trigger == trigger }
        if (matching.isEmpty()) return
        for (hook in matching) {
            scope.launch { runOneSuspending(hook, ctx) }
        }
    }

    /**
     * Suspending variant for `pre-tool-call`. Awaits the per-hook
     * run up to [HookDefinition.timeoutMs]. Failures are surfaced
     * via [HookResult] but never thrown to the caller.
     */
    suspend fun firePre(trigger: HookTrigger, vars: Map<String, String>): HookResult {
        if (closed || !masterEnabledFlow.value) return HookResult.NoOp
        val ctx = HookContext.now(trigger, vars)
        val matching = hooksFlow.value.filter { it.enabled && it.trigger == trigger }
        if (matching.isEmpty()) return HookResult.NoOp
        var lastResult: HookResult = HookResult.NoOp
        for (hook in matching) {
            lastResult = runOneSuspending(hook, ctx)
        }
        return lastResult
    }

    /** Stop new hooks and cancel owned runs; suspending callers recheck this grant. */
    fun shutdown() {
        closed = true
        scope.coroutineContext[Job]?.cancel()
    }

    // ---- internals ----

    private suspend fun runOneSuspending(hook: HookDefinition, ctx: HookContext): HookResult {
        val started = System.currentTimeMillis()
        return try {
            val ok = withTimeoutOrNull(hook.timeoutMs) {
                executeScript(hook, ctx)
            }
            val duration = System.currentTimeMillis() - started
            if (ok == null) {
                auditSink.append(
                    hookId = hook.id, hookName = hook.name,
                    triggerTag = ctx.trigger.tag(),
                    outcome = HookAuditSink.Outcome.TIMEOUT,
                    durationMs = duration, vars = ctx.vars, error = "timeout",
                )
                HookResult.failure("timeout")
            } else if (ok) {
                auditSink.append(
                    hookId = hook.id, hookName = hook.name,
                    triggerTag = ctx.trigger.tag(),
                    outcome = HookAuditSink.Outcome.SUCCESS,
                    durationMs = duration, vars = ctx.vars, error = null,
                )
                HookResult.success()
            } else {
                auditSink.append(
                    hookId = hook.id, hookName = hook.name,
                    triggerTag = ctx.trigger.tag(),
                    outcome = HookAuditSink.Outcome.ERROR,
                    durationMs = duration, vars = ctx.vars, error = "step failed",
                )
                HookResult.failure("step failed")
            }
        } catch (revoked: ScriptExecutionRevoked) {
            auditSink.append(hook.id, hook.name, ctx.trigger.tag(), HookAuditSink.Outcome.DENIED,
                System.currentTimeMillis() - started, ctx.vars, "permission revoked")
            HookResult.failure("permission revoked")
        } catch (cancelled: CancellationException) {
            auditSink.append(hook.id, hook.name, ctx.trigger.tag(), HookAuditSink.Outcome.ERROR,
                System.currentTimeMillis() - started, ctx.vars, "cancelled")
            throw cancelled
        } catch (t: Exception) {
            val duration = System.currentTimeMillis() - started
            log.warn("hook.fail", "hook ${hook.name} failed: ${t.message}",
                mapOf("err" to (t.message ?: "")))
            auditSink.append(
                hookId = hook.id, hookName = hook.name,
                triggerTag = ctx.trigger.tag(),
                outcome = HookAuditSink.Outcome.ERROR,
                durationMs = duration, vars = ctx.vars,
                error = t.message ?: t::class.simpleName,
            )
            HookResult.failure(t.message ?: t::class.simpleName ?: "unknown")
        }
    }

    /**
     * Run in the caller's coroutine, with a live permission monitor. Results are
     * invocation-local; the UI event stream is never used to infer completion.
     */
    private suspend fun executeScript(hook: HookDefinition, ctx: HookContext): Boolean = coroutineScope {
        fun allowed() = !closed && masterEnabledFlow.value && hooksFlow.value.any { it == hook && it.enabled }
        if (!allowed()) throw ScriptExecutionRevoked()
        val owner = currentCoroutineContext()[Job]!!
        val revocation = launch(start = CoroutineStart.UNDISPATCHED) {
            combine(masterEnabledFlow, hooksFlow) { master, hooks -> master && hooks.any { it == hook && it.enabled } }.first { !it }
            owner.cancel(ScriptExecutionRevoked())
        }
        try { runner.execute(hook.script, ctx.vars, ::allowed).success }
        finally { revocation.cancel() }
    }

    private fun HookTrigger.tag(): String = when (this) {
        HookTrigger.PreToolCall -> "pre_tool_call"
        HookTrigger.PostToolCall -> "post_tool_call"
        HookTrigger.OnInferenceStart -> "on_inference_start"
        HookTrigger.OnInferenceEnd -> "on_inference_end"
        HookTrigger.OnError -> "on_error"
        HookTrigger.OnTurnEnd -> "on_turn_end"
    }
}

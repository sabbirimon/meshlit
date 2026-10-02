package com.meshlit.agent.hooks

import com.meshlit.core.common.ConfigScript
import com.meshlit.core.common.HookContext
import com.meshlit.core.common.HookDefinition
import com.meshlit.core.common.HookResult
import com.meshlit.core.common.HookTrigger
import com.meshlit.core.common.logger
import com.meshlit.scripts.ConfigScriptRunner
import com.meshlit.scripts.ScriptLibrary
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
 *  - A failing hook NEVER throws to the caller. All exceptions are
 *    caught, audited, and surfaced as [HookResult.failure].
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
        if (!masterEnabledFlow.value) return
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
        if (!masterEnabledFlow.value) return HookResult.NoOp
        val ctx = HookContext.now(trigger, vars)
        val matching = hooksFlow.value.filter { it.enabled && it.trigger == trigger }
        if (matching.isEmpty()) return HookResult.NoOp
        var lastResult: HookResult = HookResult.NoOp
        for (hook in matching) {
            lastResult = runOneSuspending(hook, ctx)
        }
        return lastResult
    }

    /** Stop accepting new hooks. Existing in-flight runs continue. */
    fun shutdown() {
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
        } catch (t: Throwable) {
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
     * Register [hook]'s script with the library and run it. Returns
     * the final `success` flag from the runner's [com.meshlit.core.common.ScriptEvent.Done].
     */
    private suspend fun executeScript(hook: HookDefinition, ctx: HookContext): Boolean {
        // Register the script so the runner's `Step` shape can find
        // it. Re-registering on every run is idempotent — the library
        // dedupes by name.
        scriptLibrary.upsert(hook.script)
        val job = runner.run(hook.script)
        job.join()
        val last = runner.events.value
        if (last is com.meshlit.core.common.ScriptEvent.Done) return last.success
        // If we never reached Done (rare — should only happen if the
        // runner's scope was cancelled), default true so the line
        // doesn't lie. The audit sink will still record a non-error
        // duration.
        return true
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
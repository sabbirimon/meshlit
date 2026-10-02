package com.meshlit.core.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One user-defined hook.
 *
 * A hook is a small [ConfigScript] (the existing Kotlin DSL — see
 * [ConfigScript] for the step vocabulary) that the agent runtime
 * invokes at a specific lifecycle point. Hooks fire as fire-and-forget
 * by default; only `pre-tool-call` is allowed to block the host via
 * [HookEngine.firePre] for the v1 deny semantics work.
 *
 * Stability:
 *  - The wire shape is "v1". The [id] is generated client-side on
 *    create and is the only stable identifier across edits.
 *  - The [script] payload reuses [ConfigScript] wire verbatim — the
 *    same JSON you see in `ScriptsScreen` round-trips here without
 *    modification.
 *  - Removing a [HookTrigger] arm is a breaking change. Adding a new
 *    arm is additive and safe.
 */
@Serializable
data class HookDefinition(
    /** Stable UUID. Generated client-side on first save. */
    val id: String,
    /** Human label shown in the registry UI. */
    val name: String,
    /** Per-hook kill switch. Master kill is `SettingsRepository.hooksEnabled`. */
    val enabled: Boolean = true,
    /** Which lifecycle event the hook subscribes to. */
    val trigger: HookTrigger,
    /** The script to run. Reuses [ConfigScript]. */
    val script: ConfigScript,
    /** Per-hook timeout. Server uses it as the deadline on the whole
     *  run. Defaults to 10s. */
    val timeoutMs: Long = 10_000L,
) {
    init {
        require(id.isNotBlank()) { "HookDefinition.id must not be blank" }
        require(name.isNotBlank()) { "HookDefinition.name must not be blank" }
        require(timeoutMs in 1..600_000) { "HookDefinition.timeoutMs out of range: $timeoutMs" }
    }
}

/**
 * Lifecycle events the agent runtime exposes to hooks. The closed
 * set of triggers is deliberate: anything not listed here cannot be
 * subscribed to (yet). Adding a new arm is additive.
 *
 * Wire tagging uses snake_case to match the rest of the agent wire
 * (cf. [McpEvent]). The [label] is the human-readable description
 * shown in the trigger picker UI.
 *
 * Why these six:
 *  - **pre_tool_call / post_tool_call** — wrap every dispatch through
 *    `AgentCapabilityRouter` (in-app) and `CloudMcpCoordinator` (cloud).
 *  - **on_inference_start / on_inference_end** — bracket each turn's
 *    `InferenceCoordinator.infer(...)` call.
 *  - **on_error** — fires from every `catch (t: Throwable)` in the
 *    agentic loop (local + cloud).
 *  - **on_turn_end** — fires after the inference has finished and the
 *    final assistant text is committed.
 *
 * `on_handoff` is intentionally **absent** today. The durable-kernel
 * handoff emitter isn't wired in `main` yet, so subscribing to such a
 * trigger would silently never fire. When the emitter lands we add
 * the arm in a follow-up.
 */
@Serializable
sealed class HookTrigger {
    /** Human-readable description. Local-only — not part of the wire. */
    abstract val label: String
    /** Short tag for compact UI (chips, logs). Local-only. */
    abstract val chipLabel: String

    @Serializable
    @SerialName("pre_tool_call")
    object PreToolCall : HookTrigger() {
        override val label = "Before a tool runs"
        override val chipLabel = "Pre tool"
    }

    @Serializable
    @SerialName("post_tool_call")
    object PostToolCall : HookTrigger() {
        override val label = "After a tool runs"
        override val chipLabel = "Post tool"
    }

    @Serializable
    @SerialName("on_inference_start")
    object OnInferenceStart : HookTrigger() {
        override val label = "When inference starts"
        override val chipLabel = "Inf start"
    }

    @Serializable
    @SerialName("on_inference_end")
    object OnInferenceEnd : HookTrigger() {
        override val label = "When inference ends"
        override val chipLabel = "Inf end"
    }

    @Serializable
    @SerialName("on_error")
    object OnError : HookTrigger() {
        override val label = "When the loop hits an error"
        override val chipLabel = "On error"
    }

    @Serializable
    @SerialName("on_turn_end")
    object OnTurnEnd : HookTrigger() {
        override val label = "When a turn ends"
        override val chipLabel = "Turn end"
    }

    companion object {
        /**
         * All triggers, in display order. Used by the trigger picker.
         *
         * We deliberately construct the list via individual `listOf(...)`
         * reads rather than a `listOf(PreToolCall, ...)` initializer.
         * Some Kotlin compiler / R8 combinations leave the `INSTANCE`
         * field of `data object` subclasses uninitialized at the moment
         * the enclosing `companion object` runs (a Kotlin issue tracked
         * upstream as KT-57692). Plain `object`s initialize eagerly and
         * reliably on all Kotlin versions, so we also avoid `data`
         * here. Each call also resolves the singleton field directly,
         * so the list cannot contain a null.
         */
        val all: List<HookTrigger> by lazy {
            listOf(
                PreToolCall,
                PostToolCall,
                OnInferenceStart,
                OnInferenceEnd,
                OnError,
                OnTurnEnd,
            )
        }
    }
}
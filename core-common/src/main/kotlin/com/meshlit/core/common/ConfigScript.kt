package com.meshlit.core.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ansible-inspired configuration script DSL.
 *
 * Why Kotlin sealed types rather than YAML or a DSL parser:
 *  - serde round-trips cleanly without writing a grammar.
 *  - The same tree can be executed on this device or shipped to a
 *    peer via `POST /v1/scripts/run` over the JSON wire format.
 *  - Compose can render the tree by mapping over `when` arms.
 *
 * Target model:
 *  - `Local`      — runs on the device executing the script.
 *  - `All`        — fan-out: every peer in the forwarding registry
 *                   plus local.
 *  - `Peer(ip)`   — a single peer by IP; resolved against the
 *                   `PeerRegistry` before dispatch.
 *
 * Stability:
 *  - Wire format is "v1" — fields can be added but not removed
 *    without bumping a new route prefix.
 *  - See `ScriptsScreen` in `:app` for the editor + runner.
 */
@Serializable
data class ConfigScript(
    val schemaVersion: Int = 1,
    val name: String,
    val description: String = "",
    val steps: List<ConfigScriptStep>,
)

/**
 * One block in the script. Sealed so the runner and the editor can
 * both `when` over arms and be exhaustive.
 *
 * Steps can target a single peer (`Peer(ip)`) or run locally
 * (`Local`, `All`). The runner resolves the target each step.
 */
@Serializable
sealed class ConfigScriptStep {
    abstract val label: String
    abstract val target: ConfigScriptTarget
    abstract val continueOnError: Boolean
    abstract val timeoutMs: Long

    /**
     * Block step: assign a key/value into the script's variable
     * bag. Future steps read these via `vars["key"]`.
     */
    @Serializable
    @SerialName("set")
    data class Set(
        override val label: String = "set",
        override val target: ConfigScriptTarget = ConfigScriptTarget.Local,
        override val continueOnError: Boolean = false,
        override val timeoutMs: Long = 5_000L,
        val key: String,
        val value: String,
    ) : ConfigScriptStep()

    /**
     * Append an item to a list-typed variable. Useful for collecting
     * peer ids, file paths, etc. across a `repeat` block.
     */
    @Serializable
    @SerialName("add")
    data class Add(
        override val label: String = "add",
        override val target: ConfigScriptTarget = ConfigScriptTarget.Local,
        override val continueOnError: Boolean = false,
        override val timeoutMs: Long = 5_000L,
        val list: String,
        val item: String,
    ) : ConfigScriptStep()

    /**
     * Block step: pause for `durationMs` or until a `var[key]` is
     * non-empty / equals `expected`.
     */
    @Serializable
    @SerialName("wait")
    data class Wait(
        override val label: String = "wait",
        override val target: ConfigScriptTarget = ConfigScriptTarget.Local,
        override val continueOnError: Boolean = false,
        override val timeoutMs: Long = 30_000L,
        val durationMs: Long = 0,
        val untilVar: String? = null,
        val expectedValue: String? = null,
    ) : ConfigScriptStep()

    /**
     * Block step: assert a boolean condition. Failure throws
     * unless `continueOnError=true`; the runner records the error
     * in the `lastErrors` step field.
     */
    @Serializable
    @SerialName("assert")
    data class Assert(
        override val label: String = "assert",
        override val target: ConfigScriptTarget = ConfigScriptTarget.Local,
        override val continueOnError: Boolean = true,
        override val timeoutMs: Long = 5_000L,
        val expression: String,
    ) : ConfigScriptStep()

    /**
     * Block step: run sub-steps in parallel. Each sub-step is
     * dispatched to the same target concurrently; the runner waits
     * for all to settle before moving on.
     */
    @Serializable
    @SerialName("parallel")
    data class Parallel(
        override val label: String = "parallel",
        override val target: ConfigScriptTarget = ConfigScriptTarget.Local,
        override val continueOnError: Boolean = false,
        override val timeoutMs: Long = 60_000L,
        val children: List<ConfigScriptStep>,
    ) : ConfigScriptStep()

    /**
     * Block step: run sub-steps `count` times. The loop variable
     * `index` (0..count-1) is added to the variable bag each
     * iteration.
     */
    @Serializable
    @SerialName("repeat")
    data class Repeat(
        override val label: String = "repeat",
        override val target: ConfigScriptTarget = ConfigScriptTarget.Local,
        override val continueOnError: Boolean = false,
        override val timeoutMs: Long = 60_000L,
        val count: Int,
        val children: List<ConfigScriptStep>,
    ) : ConfigScriptStep()

    /**
     * Block step: invoke another script by name. The referred
     * script must be present in the local library or fetched from
     * a peer via `/v1/scripts/{name}`.
     */
    @Serializable
    @SerialName("step")
    data class Step(
        override val label: String = "step",
        override val target: ConfigScriptTarget = ConfigScriptTarget.Local,
        override val continueOnError: Boolean = false,
        override val timeoutMs: Long = 60_000L,
        val scriptName: String,
    ) : ConfigScriptStep()
}

/**
 * Where a step runs. Resolved by the runner against the live peer
 * registry at dispatch time. We never bake IPs into the script —
 * the same script can run on different clusters.
 */
@Serializable
sealed class ConfigScriptTarget {
    @Serializable
    @SerialName("local")
    data object Local : ConfigScriptTarget()

    @Serializable
    @SerialName("all")
    data object All : ConfigScriptTarget()

    @Serializable
    @SerialName("peer")
    data class Peer(val host: String) : ConfigScriptTarget()
}

/**
 * Events emitted by the runner. Streamed to the UI via
 * `StateFlow<ScriptEvent>`; also shipped to the requester through
 * `POST /v1/scripts/run`'s SSE reply.
 */
@Serializable
sealed class ScriptEvent {
    abstract val stepIndex: Int
    abstract val unixMs: Long

    @Serializable
    @SerialName("start")
    data class Start(
        override val stepIndex: Int,
        override val unixMs: Long,
        val scriptName: String,
    ) : ScriptEvent()

    @Serializable
    @SerialName("step_ok")
    data class StepOk(
        override val stepIndex: Int,
        override val unixMs: Long,
        val label: String,
    ) : ScriptEvent()

    @Serializable
    @SerialName("step_fail")
    data class StepFail(
        override val stepIndex: Int,
        override val unixMs: Long,
        val label: String,
        val tag: String,
        val message: String,
    ) : ScriptEvent()

    @Serializable
    @SerialName("done")
    data class Done(
        override val stepIndex: Int,
        override val unixMs: Long,
        val success: Boolean,
    ) : ScriptEvent()
}

package com.meshlit.core.common

/**
 * Snapshot of the variables the runtime exposes to a hook when it
 * fires. The shape is intentionally a flat `Map<String, String>` so
 * `ConfigScriptRunner` can read it without custom adapters — every
 * value passes through [ConfigScript]'s `vars["key"]` lookup.
 *
 * Variable bag keys per trigger:
 *
 *  | Trigger             | Required keys                              | Optional keys       |
 *  |---------------------|--------------------------------------------|----------------------|
 *  | `PreToolCall`       | `tool_name`, `call_id`, `provider_id`      | `args_preview`       |
 *  | `PostToolCall`      | `tool_name`, `call_id`, `provider_id`, `ok` | `args_preview`      |
 *  | `OnInferenceStart`  | `mode` (`chat`/`code`/`plan`), `max_tokens`| —                    |
 *  | `OnInferenceEnd`    | `token_count`, `elapsed_ms`                | `final_chars`        |
 *  | `OnError`           | `phase`, `error` (class name + message)    | `provider_id`        |
 *  | `OnTurnEnd`         | `final_chars`, `autopilot` (`true`/`false`)| `token_count`        |
 *
 * Secret-redaction contract:
 *  - Values that look like bearer tokens, API keys, passwords, or
 *    JWTs are replaced with `"<redacted>"` by the audit sink.
 *  - Values longer than 1 KB are truncated with a `"…(truncated)"`
 *    suffix.
 *  - The runtime DOES NOT mutate values before the hook sees them —
 *    the runner reads the raw map. The redaction is only on the
 *    audit-sink side. Documented in `docs/architecture/hooks.md`.
 */
data class HookContext(
    val trigger: HookTrigger,
    val vars: Map<String, String>,
    val occurredAtMillis: Long,
) {
    init {
        require(occurredAtMillis > 0) { "occurredAtMillis must be positive" }
    }

    companion object {
        fun now(trigger: HookTrigger, vars: Map<String, String>): HookContext =
            HookContext(
                trigger = trigger,
                vars = vars,
                occurredAtMillis = System.currentTimeMillis(),
            )
    }
}

/**
 * Outcome of a single hook invocation. Returned by [HookEngine.firePre]
 * so the host can decide what to do (today: log + continue).
 */
data class HookResult(
    /** Whether any hook actually ran (master + per-hook enabled, matching trigger). */
    val fired: Boolean,
    /** Whether the hook completed without an uncaught error. */
    val success: Boolean,
    /** Last error message if any. */
    val lastError: String? = null,
) {
    companion object {
        val NoOp = HookResult(fired = false, success = true, lastError = null)
        fun success() = HookResult(fired = true, success = true, lastError = null)
        fun failure(error: String) = HookResult(fired = true, success = false, lastError = error)
    }
}
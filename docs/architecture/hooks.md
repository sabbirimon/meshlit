# Phase 8 — Hooks

Hooks let users author small Kotlin-DSL scripts (`ConfigScript`)
that the agent runtime invokes at well-defined lifecycle points.
A hook is a named subscription: it picks one trigger, runs when
that trigger fires, and writes its outcome to an append-only
audit log on disk.

## What this feature is

A hook is a `HookDefinition`:

```kotlin
@Serializable
data class HookDefinition(
    val id: String,                              // stable UUID
    val name: String,                            // human label
    val enabled: Boolean = true,                 // per-hook kill switch
    val trigger: HookTrigger,                    // one of six
    val script: ConfigScript,                    // the Kotlin DSL body
    val timeoutMs: Long = 10_000L,               // 1..600_000 ms
)
```

The runtime reads the persisted list via
`SettingsRepository.hooksRegistryFlow`, dispatches matching hooks
through `ConfigScriptRunner`, and writes one JSONL line per
invocation to `filesDir/agent/hooks-audit.jsonl`.

There is no new runtime dependency — hooks reuse the existing
`ConfigScript` step set (Set / Assert / Wait / Parallel / Repeat
/ Step) and the existing `ConfigScriptRunner` engine. No JS, no
Lua, no WASM.

## The six triggers (v1)

| Trigger | When it fires | Variable bag |
|---|---|---|
| `PreToolCall` | Before `agent_*` runs | `tool_name`, `call_id`, `provider_id`, `args_preview` |
| `PostToolCall` | After `agent_*` returns | `tool_name`, `call_id`, `provider_id`, `ok`, `args_preview` |
| `OnInferenceStart` | Before `InferenceCoordinator.infer(...)` | `mode` (`chat`/`code`/`plan`), `max_tokens` |
| `OnInferenceEnd` | After the model stream completes | `token_count`, `elapsed_ms`, `final_chars` |
| `OnError` | Inside any agent-loop `catch (t: Throwable)` | `phase`, `error`, optional `provider_id` |
| `OnTurnEnd` | After the assistant text is committed | `final_chars`, `autopilot`, `token_count` |

`on-handoff` is intentionally **not** wired in v1. The
durable-kernel handoff emitter isn't on `main` yet — adding the
trigger would silently never fire. The enum arm lands in a
follow-up once the emitter ships.

## Master kill switch

`SettingsRepository.hooksEnabledFlow` defaults to `true`. Flip it
off via the Settings → Hooks master toggle to silence every hook
without removing the definitions. The kill switch is consulted on
every `fire` and `firePre` call so a flip takes effect immediately
with no restart.

## Audit log

`filesDir/agent/hooks-audit.jsonl` — append-only JSON Lines, one
record per invocation:

```json
{"ts":"2026-09-30T12:34:56.789-08:00","hook_id":"…","hook_name":"log-tool","trigger":"pre_tool_call","outcome":"SUCCESS","duration_ms":42,"vars_summary":"tool_name=fs.read,call_id=abc","error":null}
```

Outcome vocabulary: `SUCCESS`, `TIMEOUT`, `ERROR`, `DENIED`,
`SKIPPED`.

Secret redaction: any variable whose key matches
`bearer|access[_-]?token|api[_-]?key|secret|passwd?|jwt|authorization`
or whose value matches a JWT / bearer / long-hex regex is replaced
with `"<redacted>"` before write. The `vars_summary` field is
capped at 8 KB with a `…(truncated)` suffix.

The directory is created lazily on first write — callers don't
have to pre-touch the filesystem.

## Security model

- Hooks run **in-process** via the existing `ConfigScriptRunner`.
  No `Runtime.exec`, no `ProcessBuilder`, no shell-out.
- Hooks cannot call out to external HTTP / arbitrary MCP servers.
  The runner permits the same `agent_*` tool surface the agent
  loop already enforces.
- Variables carrying secrets are redacted at the audit-sink
  boundary — values the hook itself reads are not mutated.
- The audit file lives under `filesDir/agent/`, which the OS
  sandbox treats as app-private. Other apps cannot read it
  without root.
- Per-hook `timeoutMs` (default 10 s, max 10 min) is enforced by
  the engine with `withTimeoutOrNull`. A slow hook does not block
  the host.
- A failing hook NEVER throws to the caller. All exceptions are
  caught and audited.

## Worked example

A `PostToolCall` hook that logs every `agent_storage_write` to
the local audit file:

```json
{
  "schemaVersion": 1,
  "name": "log-writes",
  "description": "audit every storage write",
  "steps": [
    {
      "type": "set",
      "label": "stamp",
      "key": "saw_write_at",
      "value": "now"
    },
    {
      "type": "assert",
      "label": "storage-only",
      "expression": "true"
    }
  ]
}
```

The hook's audit line lands in `filesDir/agent/hooks-audit.jsonl`
with `trigger=post_tool_call`, `outcome=SUCCESS`, and a
`vars_summary` carrying the redacted `args_preview`.

## Limitations (v1)

- `on-handoff` trigger is deferred — see "The six triggers" above.
- Hooks run **locally** on the device that owns the agent loop.
  No federation hook execution yet (no `/v1/hooks/*` endpoint).
- `PreToolCall` result is currently logged-only. v1 does NOT
  honor a deny-on-assert-failure semantic; that lands in a
  follow-up once we see real hook usage and know what users want
  to block.
- No migration tooling for users who have ad-hoc `ConfigScript`s
  in `ScriptLibrary`. Auto-import deferred.
- Script body is edited as raw JSON in v1 — same shape as
  `ScriptsScreen`. A schema-aware editor is a follow-up.

## Files

| Path | What |
|---|---|
| `core-common/.../HookDefinition.kt` | `HookDefinition` + `HookTrigger` enum |
| `core-common/.../HookContext.kt` | `HookContext` value type + `HookResult` |
| `app/.../agent/hooks/HookEngine.kt` | runtime dispatcher |
| `app/.../agent/hooks/HookAuditSink.kt` | JSONL audit sink |
| `app/.../ui/screens/settings/HooksScreen.kt` | registry UI |
| `app/.../ui/screens/settings/HookEditorScreen.kt` | editor UI |
| `app/.../ui/screens/settings/HooksViewModel.kt` | VM |
| `app/src/test/.../hooks/HookEngineTest.kt` | JVM tests |
| `app/src/test/.../hooks/HookAuditSinkTest.kt` | JVM tests |
| `app/src/main/.../settings/SettingsRepository.kt` | `hooksRegistryJson` + `hooksEnabled` keys |
| `app/src/main/.../di/CoreModule.kt` | Koin wiring |
| `app/src/main/.../MeshlitApplication.kt` | `startHooksFeed(...)` bootstrap |
| `app/src/main/.../agent/AgentSession.kt` | wires OnInferenceStart/End/TurnEnd/OnError |
| `app/src/main/.../agent/AgentCapabilityDispatchers.kt` | wraps each agent_* tool in Pre/Post/OnError |
| `app/src/main/.../MeshlitRuntime.kt` | wraps prompt-runner in OnError |
| `app/src/main/.../ui/MeshlitApp.kt` | `settings/hooks` and `settings/hooks/{id}` routes |
| `app/src/main/.../ui/screens/settings/SettingsScreen.kt` | `HOOKS` category enum arm |
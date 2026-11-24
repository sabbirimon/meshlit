# Typed agent backend, version 1

Human and agent operations share SettingsRepository, ModelLibrary, InferenceCoordinator
and PipelineHost. AgentCommand is a serializable DTO, not a UI action. Its controller
persists admission, runs at most two commands concurrently, bounds admitted jobs to eight
and retains at most 100 journal entries. Parameters/journal are encrypted on Android.

## Discovery and operations

Call `agent_command_schema`, then `agent_command_submit` with a `command` object:

```json
{"command":{"requestId":"load-360m-001","operation":"MODEL_LOAD","modelId":"installed-model-id"}}
```

`requestId` is a stable client-generated identifier, 1–80 ASCII letters/digits/_/-.
Reusing it with identical parameters returns the original job. Reusing it with changed
parameters is rejected. Query `agent_job_status` with that requestId until terminal.
`agent_job_cancel` cancels queued/running work. `agent_job_retry` requires saved recovery
scope, a failed/interrupted/cancelled sourceId and a fresh requestId; the original scope
is also checked again when the retry executes.

| Command | Additional fields | Mutation scope |
|---|---|---|
| SETTINGS_READ | none | Read only |
| SETTINGS_PATCH | appearance: themeMode/accentHue enum names, dynamicColors/animationsEnabled booleans, fontScale 0.85–1.5 | SETTINGS |
| MODELS_LIST | none | Read only |
| MODEL_DOWNLOAD / MODEL_LOAD / MODEL_DELETE | modelId | MODELS |
| MODEL_ADD_URL | url HTTPS direct GGUF; optional name | MODELS |
| MODEL_IMPORT | importUri, a previously granted persisted Android content URI | MODELS |
| MODEL_GENERATE | loaded modelId, prompt; optional maxTokens 1–2048, temperature 0–2 | MODELS |
| MODEL_IMPORT_SOURCES | none | Read only |
| MODEL_UNLOAD | none | MODELS |
| CLUSTER_STATUS | none | Read only |
| CLUSTER_PLAN / CLUSTER_START | installed modelId | CLUSTER |
| CLUSTER_WORKER_START / CLUSTER_STOP | none | CLUSTER |
| RECOVERY_STATUS | none | Read only |

Saved scopes are human controlled in OpenClaw and autonomy settings. Commands cannot
grant root, pairing, Android permissions or their own delegation. Revoking a scope
cancels nonterminal jobs in it. Cancelled downloads started by that command pause;
joining an existing human transfer does not cancel that unrelated transfer.

## Observable results and recovery

Job phases: QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED, INTERRUPTED. Poll returns
requestId, operation, timestamps, result and typed errorCode/error; submitted URLs,
model paths and credentials are omitted. MODELS_LIST reports live bytes/transfer phases.
CLUSTER_PLAN returns memory-weighted worker assignments and coordinator identity.
Read and short runtime commands have 180-second deadlines; transfers have a two-hour
command deadline and the download engine's own tighter deadline.

Admission is saved before execution. Process restart marks formerly nonterminal jobs
INTERRUPTED with an uncertain-outcome error. No automatic replay occurs. Inspect live
model/cluster/settings state before issuing an explicit retry. This local journal is
not a replicated cluster database, fencing consensus or native KV checkpoint recovery.
RECOVERY_STATUS exposes those missing capabilities as false.

## OpenClaw bridge

The paired node exposes the same methods with a `meshlit.` prefix. Gateway custom-node
command policy must explicitly authorize them. Pairing does not confer backend mutation
scopes. Existing MCP clients can invoke these tools without navigating screens.

## Verification

Controller tests cover persisted admission before execution, idempotency, changed-ID
payload rejection, restart interruption, cancellation, typed permission failures and
storage failures. Real model load/download and physical worker tests remain separate
acceptance criteria. Do not use fake-engine tests as device inference proof.

MODEL_GENERATE checks the expected model path inside the inference coordinator lock, so a concurrent UI load cannot silently switch the model used by an admitted generation. Prompts stay in the encrypted journal; generated text is returned as the authorized job result.

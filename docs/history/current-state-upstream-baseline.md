> Historical upstream baseline. For the current checkout, read `../../PROGRESS.md` and
> `../../FEATURE_MAP.md`. The original phase/model-bundling assertions below are not current evidence.

# Current-state baseline

> **Status:** Slice 1 of the improvement plan. Single source of truth for what the project actually ships today, what works, what doesn't, and where the gaps are.
>
> **Audience:** any agent picking up a new slice. Read this before touching `:core-inference` or any other engine path.
>
> **Update rule:** update on every phase boundary or material decision. Not on every commit.

---

## 1. Top-level shape

**27 Gradle modules** (per `settings.gradle.kts`). One Android application module (`:app`) + 26 `core-*` / `feature-*` libraries. Module dependency graph is layered leaves-first; the application is the only module allowed to depend on every leaf.

Three runtime paths the user can drive:

1. **Local on-device inference** — bundled `smollm2-360m-instruct-q8_0.gguf` (`~368 MB`) extracted on first launch into `filesDir/bundled-models/`. The asset basename matches the RunAnywhere SDK's `DEFAULT_MODEL_ID`, so the FGS auto-loads without a rename step.
2. **Cluster / remote inference** — embedded NanoHTTPD server on `:8080`; `RemoteInferenceClient` (Ktor 3) opens SSE to a peer; partial tokens flow into the same `CoordinatorState` as local jobs.
3. **Cloud-hosted MCP** — `:core-cloud-mcp` provides SSE-over-HTTPS transport + JSON-RPC 2.0 envelopes + an `OpenApiSpecParser` for custom providers. NaraRouterClient streams OpenAI-compatible chat completions.

The screen surface has **14 bottom-bar destinations + drawer-only entries** (Help, Power, Cloud). Total: 17+ screens.

## 2. Module inventory (what each layer owns)

### Leaves — no dependency on other core modules

| Module | Owns |
|---|---|
| `core-common` | `MeshlitError`, `MeshlitResult`, `CapabilityTier`, `HostOS` detection |
| `core-config` | DataStore-backed configuration repository |
| `core-flags` | Feature flag registry |
| `core-trust` | TrustTier enum, attestation helpers, encrypted credential store |
| `core-discovery` | NSD / Wi-Fi Aware / Tailscale / WireGuard / relay transports |
| `core-files` | SAF-backed file vault |
| `core-ssh` | LAN/Tailscale SSH server + client (Apache MINA SSHD) |
| `core-firewall` | Per-port application-level accept-list + rate limit |
| `core-guardrails` | Prompt injection / jailbreak / PII filters (local) |
| `core-tunnel` | Tailscale / WireGuard plumbing |
| `core-users` | Local profiles, biometric unlock, per-user quotas |
| `core-advanced-engines` | Alternate engine slots (scaffold) |
| `core-gpu` | GPU / NPU detection + priority helpers |
| `core-training` | On-device LoRA / QLoRA scaffold + dataset loaders |

### Mid-tier — depends on leaves, not on `:app`

| Module | Owns |
|---|---|
| `core-inference` | `InferenceEngine` interface + 5 implementations + `InferenceCoordinator` + FGS + NanoHTTPD + RemoteInferenceClient |
| `core-mcp` | On-device MCP tool surface (registerTool wrappers) |
| `core-cloud-mcp` | Cloud-hosted MCP agent + capability registry |
| `core-orchestration` | Job queue, retries, timeouts (scaffold) |
| `core-net` | `NetworkObserver` (OkHttp EventListener) + `MeshlitCaptureVpnService` + PCAP writer/parser + `PacketCaptureRegistry` |
| `core-observability` | `TracingController` (Off/Local/Otel) + OtelBootstrap + SinkSpanProcessor + TracerHolder + LogSource |
| `core-terminal` | Hand-rolled VT parser + session host (planned swap to ghostty-org/ghostty) |
| `core-bootstrap` | App-start sequence helpers |
| `core-registry` | Service registry abstractions |
| `core-lifecycle` | Lifecycle observation helpers |
| `core-probe` | Capability probe + device profile |
| `core-role` | Role suggestion (Brain / Tool / Monitor) |
| `core-agent-memory` | Phase 0 spike — layered memory architecture borrowed from (rejected) TencentDB review |

### Features + app

| Module | Owns |
|---|---|
| `feature-advanced` | Advanced screens (scaffold) |
| `feature-ghosty` | Ghostty-style terminal screen (scaffold) |
| `:app` | Compose UI, MainActivity, MeshlitApplication, MeshlitRuntime, FGS starters, every screen, all wire-up |

## 3. Runtime engines (the actual on-device inference)

`InferenceCoordinator.pickEngine()` picks one of these at startup. Order is documented in `InferenceCoordinator.kt:89`.

### `RunAnywhereInferenceEngine` — production path today
- Wraps the RunAnywhere SDK 0.20.12 (`io.github.sanchitmonga22:runanywhere-sdk:0.20.12`).
- Bundled native libs: `libllama.so` for arm64-v8a, armeabi-v7a, x86, x86_64 (`~34 MB` each).
- The SDK's `libllama.so` is the actual inference engine — the Meshlit Kotlin code is a thin wrapper.
- `engineTag = "runanywhere"`.

### `LlamaCppInferenceEngine` — **stub with declared JNI surface**
- **State:** Kotlin-side `external fun` declarations exist for 5 symbols (`nativeInit`, `nativeLoadModel`, `nativeInfer`, `nativeUnload`, `nativeCancel`). The actual `libmeshlit_inference.so` is **not built**.
- `core-inference/src/main/cpp/` does not exist.
- No `externalNativeBuild` block in `core-inference/build.gradle.kts`.
- On `loadNativeLibrary()` failure, the engine returns `isReady() == false` and is silently skipped by `pickEngine()`.
- `engineTag = "llama.cpp"` is reserved for the path that doesn't yet ship.
- **This is the headline gap. Slice 2 of the improvement plan starts here.**

### `OnnxOrtInferenceEngine` — wired but inert
- Microsoft ORT Mobile 1.18 aar is bundled (`libonnxruntime.so` ships in APK, `~3.7 MB` for arm64).
- The Java side calls ORT's pure-Java API; the JNI surface (`nativeLoadModel`, `nativeInfer`, `nativeUnload`) is declared but not linked.
- The engine initializes the ORT environment successfully (`isReady() == true` if `.so` loads) but returns typed errors when no `.onnx` model is present.
- `engineTag = "onnx-ort"`.

### `NoOpInferenceEngine` — typed-failure fallback
- Last resort. Returns `MeshlitError.Native("no_engine_for_format:...")` on every operation.
- Never emits placeholder text (the old `JvmStubInferenceEngine` was deleted for exactly this reason — it produced deterministic fake replies that confused users).
- `engineTag = "none"`.

### Engine selection (`pickEngine()`)

Current resolution order:
1. `System.getProperty("meshlit.inference.stub") == "true"` → always `NoOpInferenceEngine`.
2. Try `LlamaCppInferenceEngine.loadNativeLibrary()` → if it succeeds, that's the engine. (Currently never succeeds — no `.so` ships.)
3. Otherwise try the RunAnywhere-backed engine (already initialized at app start).
4. Otherwise try the ONNX engine (will likely fail at loadModel with no model file).
5. Otherwise `NoOpInferenceEngine`.

**Gap:** the selection is silent. `CoordinatorState` exposes `engineTag` but not the *reason* a particular engine was chosen, nor whether the fallback was safe for the requested model format. Slice 2 + Slice 5 will fix this.

## 4. Data flows

### Model loading flow (cold start → first token)

```
App start (MeshlitApplication.onCreate)
   └─→ BundledModelInstaller.installBundledModels()
        ├─ extracts assets/models/smollm2-360m-instruct-q8_0.gguf → filesDir/bundled-models/
        ├─ SHA-256 verified against the pinned sentinel
        └─ RunAnywhereInferenceEngine.registerModel(id, name, url, ...) pre-seeds the SDK cache

User opens Jobs screen
   └─→ JobsScreen collects InferenceCoordinator.state
        ├─ Idle ───────────► Starting (markStarting() during SDK init)
        ├─ Starting ──────► Idle (markInitialized() after init completes)
        └─ Idle ───────────► Loading(path) on user "Load model" tap

Loading(path)
   └─→ InferenceCoordinator.loadModel(request)
        ├─ FileFormat.detect(path) → GGUF | ONNX | unknown
        ├─ RuntimeRegistry.find(format) → RuntimeEngine entry
        ├─ engine.loadModel(...) → tokens, context size, parameter count, quant
        ├─ coordinator emits LoadStarted → LoadSucceeded | LoadFailed
        └─ coordinator state: Loading → Ready(model) | Error(reason)

InferenceCoordinator.infer(prompt)
   ├─ Acquires inferMutex (serialized — only one inference at a time)
   ├─ state: Ready → Generating(startedAtMs, runtime, format)
   ├─ engine.infer(request) → stream tokens via onToken callback
   ├─ Cancellation: coroutineJob.cancel() → engine.nativeCancel() → state Generating → Ready
   └─ state: Generating → Ready (FinishReason.NATURAL_STOP | MAX_TOKENS | STOP_SEQUENCE | CANCELLED | ERROR)
```

**Coordination rules (current behavior — to be hardened in Slice 5):**
- If `loadModel` is called while the coordinator is `Generating`, the call blocks on `inferMutex` until the active generation finishes or is cancelled.
- `currentJob` tracks the active generation; `cancel()` is cooperative.
- A model loaded with a sharded manifest sets `loadedShards` so the planner can route replacement peers via `/v1/model.shardRanges`.
- The engine selection is **silent**: `pickEngine()` returns the first available engine without surfacing why. Slice 5 will surface `engineTag` + `enginePickReason` in `CoordinatorState`.

### HTTP/SSE federation flow (peer → peer inference)

Each Meshlit node exposes the same surface on `:8080`. The endpoints are typed in `core-inference/net/InferenceWire.kt` and pinned by `RuntimesResponseRoundTripTest`.

```
Peer A (responder)                                          Peer B (requester)
────────────────────                                        ────────────────────
InferenceHttpServer.bind(8080)
   ├─ GET  /v1/health   → HealthResponse {
   │       engineTag, runtimeId, runtimeDisplayName,
   │       modelLoaded, fileFormat, tokensPerSecond }
   ├─ GET  /v1/runtimes → RuntimesResponse {
   │       runtimes: List<RuntimeDescriptor>,
   │       summary: RuntimeCatalogSummary }
   ├─ GET  /v1/model    → { id, context, shardRanges }
   └─ POST /v1/infer    → SSE stream
          event: token { text: "..." }
          event: token { text: "..." }
          event: done  { promptTokens, generatedTokens, finishReason }

                                                            RemoteInferenceClient.infer(host, prompt)
                                                               ├─ POST /v1/infer with JSON body
                                                               ├─ parses SSE event stream
                                                               │    token event → onToken callback
                                                               │    done  event → InferenceResult
                                                               │    error event → MeshlitError.Network
                                                               └─ returns MeshlitResult<InferenceResult>
```

**Wire types locked by tests:** `HealthResponse`, `RuntimesResponse`, `RuntimeDescriptor`, `RuntimeCatalogSummary`. Five round-trip tests in `RuntimesResponseRoundTripTest`. **Gap:** no `protocol_version` field on the wire — Slice 7 adds it.

**Auth model (current):** LAN endpoints are reachable without authentication. Bearer-token auth exists for the cloud gateway (`core-cloud-mcp`) but is not enforced for LAN remote inference. **Gap:** Slice 7 enforces authenticated pairing before remote inference.

### Local-only paths (not in the coordinator)

The cloud MCP path is a **separate coordinator** (`CloudMcpCoordinator`) and never reaches `InferenceCoordinator`. Same for `NaraRouterClient`. Don't conflate them.

```
Compose UI (CloudHubScreen / AgentTerminalScreen)
   └─→ CloudMcpCoordinator.connect(provider)
        ├─ SSE-over-HTTPS to provider
        ├─ JSON-RPC 2.0: initialize / tools/list / tools/call
        └─→ McpEvent stream (Connected / Thought / ToolCall / ToolResult / Done)

NaraRouterClient.runAgentPrompt(prompt)
   └─→ streaming OpenAI-compatible chat completions → LlmChunk.{Text,ToolCall,Done}
        └─→ tool calls dispatched via ToolRegistry → CloudMcpSession.tools/call
```

### Coordinator state machine (current shape)

`CoordinatorState` is the typed surface the UI binds to (`StateFlow<CoordinatorState>`):

```
                  ┌──────────────────────────────────────────────────────┐
                  │                                                      │
                  ▼                                                      │
       ┌──────────────────┐  markStarting()    ┌──────────────────┐    │
       │       Idle       │ ─────────────────► │     Starting     │    │
       └──────────────────┘                    └──────────────────┘    │
              │   ▲                                    │                │
              │   │ markInitialized()                  │ init finished │
              │   └────────────────────────────────────┘                │
              │                                                          │
              │ loadModel()                                              │
              ▼                                                          │
       ┌──────────────────┐  load OK        ┌─────────────────────────┐  │
       │     Loading      │ ──────────────► │  Ready(model, runtime,  │  │
       │ (modelPath,      │                 │          format)        │  │
       │  runtime, format)│ ◄────────────── │                         │  │
       └──────────────────┘   load fails    └─────────────────────────┘  │
              │                                      │      ▲            │
              │                                      │      │            │
              │                                      │ infer│            │
              │                                      │      │ generation │
              │                                      ▼      │ complete   │
              │                              ┌──────────────────────────┐│
              │                              │ Generating(startedAtMs,  ││
              │                              │             runtime,      ││
              │                              │             format)       ││
              │                              └──────────────────────────┘│
              │                                      │      │            │
              │                                      │      │ cancel     │
              │                                      ▼      ▼            │
              │                              ┌──────────────────────────┐│
              │                              │       Ready(...)         │┘
              │                              └──────────────────────────┘
              │
              │ unloadModel()
              ▼
       ┌──────────────────┐
       │       Idle       │
       └──────────────────┘

  Side-state (any state): errors surface as CoordinatorState.Error(message, runtime, format).
  The Error state is reachable from Loading, Ready, Generating — see InferenceCoordinator.kt lines 351, 393, 401, 408, 443.
```

**Slice 5 will harden this** by introducing explicit `Cancelling` and `Failed` terminal states, and by adding `enginePickReason` to `Ready` so the UI can show *why* a particular engine was chosen.

## 5. What's actually shipped vs. what's planned

### Shipped (`:app:assembleDebug` is green)

- [x] InferenceEngine abstraction + 4 implementations.
- [x] InferenceCoordinator with serialized inference + StateFlow + cancellation.
- [x] InferenceForegroundService with `dataSync` FGS + persistent notification + LocalBinder IPC + Android 15+ `onTimeout()`.
- [x] Embedded NanoHTTPD server on `:8080` (`/v1/health`, `/v1/runtimes`, `/v1/model`, `/v1/infer` SSE).
- [x] RemoteInferenceClient (Ktor 3, OkHttp engine).
- [x] Voice / JSON / Catalog / Vision screens wired to `RunAnywhere*Engine`.
- [x] Model catalog + download via `RunAnywhere.downloadModelById(...)` + `registerModel(...)`.
- [x] Device pairing via QR + bearer token (Google Play Services scanner).
- [x] 14 bottom-bar destinations + drawer-only entries (Help, Power, Cloud).
- [x] Phase 1.0: per-ABI APKs (arm64 = 82 MB, x86_64 = 89 MB, universal = 191 MB).
- [x] Phase 1.1: `scripts/phase1_validation.py` + `phase1_stress_test.sh` (hardware-dependent, not yet executed on real devices).
- [x] Phase 1.2: wire-surface test coverage (97 `:core-inference` unit tests).
- [x] Phase 0.3: Koin DI scaffolding (MeshlitApplication god-object dissolved).
- [x] Phase Obs-1: tracing (Off/Local/Otel), log export, manual, tour, feedback, network monitor, VPN PCAP capture.

### Not yet shipped (the gap)

- [ ] **Owned llama.cpp JNI path** (`libmeshlit_inference.so` doesn't exist). Slice 2.
- [ ] **Explicit fallback visibility** — `pickEngine()` is silent. Slice 5.
- [ ] **Versioned peer protocol** — `/v1/health` has no `protocol_version` field. Slice 7.
- [ ] **Authenticated pairing required for remote inference** — bearer-token auth exists for cloud gateway but not enforced for LAN remote. Slice 7.
- [ ] **Capability-aware router** — `RouterRef.decideFor(...)` is a placeholder. Slice 7.
- [ ] **CI for `:core-inference` unit tests** — `.github/workflows/ci.yml` runs `:app:test<Flavor>DebugUnitTest` but not `:core-inference:testDebugUnitTest`. Slice 1 step.
- [ ] **Real-device stress test pass** — scripts exist; no recorded run. Slice 6.

### Out of scope for v1 (deferred)

- [ ] MoE expert shard federation (`BUILD_GUIDE.md` §2.7 — planned for Phase 4.5).
- [ ] Co-operative training loops (`BUILD_GUIDE.md` §7.9 — Phase 5).
- [ ] Adaptive thermal/power tuning (`BUILD_GUIDE.md` Phase 5).
- [ ] Rust integration — not needed in v1; re-introduce only with a concrete subsystem.
- [ ] Ghostty swap in `core-terminal` (TODO #179 — pending licence + NDK matrix check).

## 6. The contract surface (what must not break)

These types are touched by the UI, the FGS, and the HTTP/SSE wire. Breaking them ripples into every screen and every peer.

| File | Contract |
|---|---|
| `core-inference/.../InferenceEngine.kt` | `InferenceEngine` interface + `ModelLoadRequest` / `ModelInfo` / `InferenceRequest` / `InferenceResult` / `FinishReason` |
| `core-inference/.../InferenceCoordinator.kt` | `CoordinatorState` sealed class + `InferenceEvent` sealed class + `pickEngine()` selection |
| `core-inference/.../RuntimeEngine.kt` | `RuntimeEngine` interface + `RuntimeStatus` enum + `FileFormat` union + `RuntimeRegistry` |
| `core-inference/.../net/InferenceWire.kt` | `HealthResponse`, `RuntimesResponse`, `RuntimeDescriptor`, wire-type round-trippable JSON |
| `core-common/.../MeshlitError.kt` | Sealed error surface: `Native`, `Network`, `Invalid`, `Permission`, etc. |

**Rule for Slice 2+:** when touching any of these, treat the existing shape as locked. Add new fields via defaults; don't reorder; don't rename.

## 7. Verification recipe for this slice

```bash
# Build stays green
./gradlew :app:assembleDebug

# Inference module tests
./gradlew :core-inference:testDebugUnitTest

# Lint
./gradlew :app:lintDebug
```

If any of these is red, Slice 1 didn't land cleanly.

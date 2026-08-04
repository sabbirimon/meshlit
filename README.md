# Meshlit

> **Many phones. One mind.**
> On-device AI runtime that turns a fleet of Android phones into a
> private, federated inference cluster — no cloud, no telemetry.

Meshlit is an Android app that orchestrates local LLMs, speech,
vision, structured output, and tool-calling across nearby devices.
It runs entirely on-device by default; network transports (LAN,
Wi-Fi Direct, Tailscale, VPN, relay) are opt-in for cluster
federation. Built on the [RunAnywhere](https://github.com/RunanywhereAI/runanywhere-sdks)
SDK 0.20.12.

Apache 2.0 — see [LICENSE](LICENSE).

---

## Highlights

- **Multi-runtime inference.** Pick from GGUF (llama.cpp), ONNX
  (Microsoft ORT Mobile), or any remote OpenAI-compatible endpoint.
  Models ship either bundled in the APK, downloaded on demand from
  the RunAnywhere CDN, or imported from local storage via SAF.
- **Full SDK surface.** Four extra screens at full depth —
  voice (STT + TTS + VAD), structured output + tool calling, dynamic
  model catalog, and vision (image + VLM).
- **Federation.** Data-parallel jobs (no tensor-parallel) across a
  cluster of phones. Optional Mixture-of-Experts shard federation for
  frontier models — each node holds a subset of experts, the router
  dispatches tokens.
- **Embedded HTTP/SSE server.** Capability-aware mini-router,
  `X-Meshlit-Hints` header, SSE streaming, 30 s peer-health cache,
  forward-and-stream proxy.
- **Foreground service for inference.** Android `dataSync` FGS with
  persistent notification, LocalBinder IPC, Android 15+
  `onTimeout()` handling.
- **MCP tool server.** Tools registered with the LLM drive Meshlit
  itself: file ops, device control, peer queries.
- **Trust tiers.** LAN / temporary-local / WAN each carry a
  different auth burden. The Devices screen lets the user lock
  scopes precisely.
- **No telemetry.** Every byte leaving the device is the user's
  explicit, visible choice.

---

## Table of contents

- [Status](#status)
- [Architecture](#architecture)
- [Project layout](#project-layout)
- [Build](#build)
- [Running](#running)
- [The four SDK-backed screens](#the-four-sdk-backed-screens)
- [Cluster model](#cluster-model)
- [Security model](#security-model)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

---

## Status

**Phase 2.x** — full RunAnywhere SDK surface (voice / vision /
structured / catalog) is in `:app:assembleDebug`. The shipped APK
inlines four on-device backends wired to real Compose UI:

| Capability                | Backend                              | Screen              |
|---------------------------|--------------------------------------|---------------------|
| LLM inference             | llama.cpp (`runanywhere-llamacpp`)   | Jobs / Agent        |
| ONNX inference            | Microsoft ORT Mobile 1.18            | (registry slot)     |
| Speech-to-text (STT)      | sherpa-onnx via `runanywhere-onnx`   | Voice               |
| Text-to-speech (TTS)      | sherpa-onnx                          | Voice               |
| Voice activity (VAD)      | sherpa-onnx                          | Voice               |
| Structured output + tools | `RunAnywhere.generateStructuredStream` / `generateWithTools` | JSON    |
| Dynamic catalog           | `RunAnywhere.listModels` / `refreshModelRegistry`           | Catalog |
| Vision (VLM)              | `RunAnywhere.processImageStream` (UI staged, AAR pending)    | Vision   |
| Image picker              | `ActivityResultContracts.PickVisualMedia` | Vision           |
| Peer HTTP dispatch        | NanoHTTPD + Ktor 3 client            | Jobs                |

Detailed progress journal: [PROGRESS.md](PROGRESS.md).
Build guide: [app/BUILD_GUIDE.md](app/BUILD_GUIDE.md).

---

## Architecture

### One-liner

```
┌──────────────────── 1 phone ────────────────────┐
│ Compose UI ─ InferenceCoordinator (typed state) │
│          │                                     │
│          ├── LlamaCppInferenceEngine           │
│          │   └── libllama.so (per ABI)         │
│          ├── OnnxOrtInferenceEngine            │
│          │   └── libonnxruntime.so             │
│          ├── RunAnywhereVoiceEngine            │
│          │   └── sherpa-onnx                   │
│          ├── RunAnywhereStructuredEngine       │
│          ├── RunAnywhereVisionEngine           │
│          ├── RunAnywhereCatalogEngine          │
│          └── RemoteInferenceClient (Ktor 3)    │
│                                                 │
│ InferenceForegroundService                     │
│   └── LocalBinder ─ Jobs / Agent screens        │
│                                                 │
│ NanoHTTPD inference server :8080                │
│   ├── GET  /v1/health                          │
│   ├── GET  /v1/runtimes                        │
│   ├── GET  /v1/model                           │
│   └── POST /v1/infer   (SSE)                   │
└─────────────────────────────────────────────────┘
```

### Inference path

The Jobs screen binds to `InferenceForegroundService` via
`LocalBinder`. A request flows:
**Compose prompt → Service intent → `InferenceCoordinator.loadModel` →
`InferenceCoordinator.generate(prompt)` → engine flow →
`CoordinatorState` `StateFlow` → Compose `collectAsState` → token-by-token render.**

The coordinator owns serialization, cancellation, and StateFlow.
Each engine implements `InferenceEngine` (see
`core-inference/.../RuntimeEngine.kt`) so the coordinator picks the
right backend at load time based on file format and runtime
availability.

### Cluster path

Every Meshlit node exposes the same HTTP/SSE server on `:8080`.
Discovery happens via NSD, Wi-Fi Direct, Tailscale, or a manual
paste. The forwarding-peer registry lives in DataStore.

When the user runs a job and selects a remote endpoint, the
`RemoteInferenceClient` opens a streaming SSE connection; partial
tokens flow into the same Compose pipeline as local jobs.

MoE-shard federation is layered on top: each peer advertises which
experts it holds, and the router dispatches tokens by `expert_id`.

### MCP / tools

`:core-mcp` hosts Meshlit's own tool surface. Tools like
`meshlit.files.list`, `meshlit.peers.health`, `meshlit.shell.run`
are registered with `RunAnywhere.registerTool(...)` on engine init.
The Structured screen exposes them as toggleable cards.

---

## Project layout

```
meshlit/
├─ app/                    # The Android app (Compose UI + FGS)
│  ├─ BUILD_GUIDE.md       # Authoritative build instructions
│  └─ src/main/kotlin/com/meshlit/
│     ├─ MainActivity.kt
│     ├─ MeshlitApplication.kt
│     ├─ agent/            # Claude-Code-style Agent screen
│     ├─ devices/          # Peer / endpoint management
│     ├─ inference/        # FGS + RemoteInferenceClient + DownloadStatus
│     ├─ models/           # GGUF import + on-device catalog
│     ├─ observability/    # LogBuffer + AppLoggerFactory
│     ├─ permissions/      # PermissionHelper
│     ├─ power/            # BatteryOptimizationHelper
│     ├─ scripts/          # ScriptLibrary snapshot storage
│     ├─ settings/         # Settings hub + per-category screens
│     ├─ terminal/         # Sessions screen (TTY-style)
│     └─ ui/
│        ├─ MeshlitApp.kt          # NavHost
│        ├─ components/            # MeshlitHeader, BottomBar, Drawer
│        ├─ nav/                   # TopLevelDestination enum
│        └─ screens/               # Voice / Structured / Catalog /
│                                  #   Vision / Jobs / Devices / Agent
│                                  #   / Models / Files / Sessions /
│                                  #   Cluster / Network / Users /
│                                  #   Settings / Logs / Metrics + stubs
│
├─ core-common/            # MeshlitError, MeshlitResult,
│                          #   CapabilityTier, HostOS detection
├─ core-trust/             # TrustTier, attestation helpers
├─ core-discovery/         # NSD, Wi-Fi Direct, mDNS
├─ core-inference/         # InferenceEngine, RuntimeRegistry,
│                          #   InferenceCoordinator, FGS wire types,
│                          #   NanoHTTPD server, RemoteInferenceClient,
│                          #   RunAnywhere{Voice,Vision,Structured,
│                          #                   Catalog,Inference}Engine
├─ core-mcp/               # MCP tool surface
├─ core-training/          # On-device LoRA, federated loops
├─ core-files/             # SAF-backed file vault
├─ core-ssh/               # LAN/VPN SSH server + client
├─ core-firewall/          # Trust-tier firewall
├─ core-guardrails/        # Prompt guardrails (local)
├─ core-tunnel/            # Tailscale / WireGuard plumbing
├─ core-users/             # Identity + keychain
├─ core-orchestration/     # Job queue, retries, timeouts
│
├─ build-logic/            # Gradle convention plugins
├─ dist/                   # Built APK outputs
├─ docs/                   # architecture/, decisions/, journal/
├─ gradle/libs.versions.toml
├─ settings.gradle.kts
└─ PROGRESS.md             # Running decision journal
```

14 Gradle modules. 123 Kotlin source files in `src/main`.

---

## Build

### Requirements

- Android Studio Ladybug (AGP 9.2.1)
- JDK 21
- Android SDK 36, build-tools 36
- A device or emulator on **API 24+** (Android 7.0+). The
  RunAnywhere SDK 0.20.12 needs `libllama.so` symbols from API 24.

### Quickstart

```bash
git clone https://github.com/sabbirimon/meshlit
cd meshlit
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s MeshlitApplication:I
```

A debug APK is ~1.6 GB (it ships all four native libs across
arm64-v8a, armeabi-v7a, x86, x86_64). Release builds with R8
full mode and ABI splits are a separate plan; today we ship
universal debug.

### Module-level tests

```bash
./gradlew :core-inference:testDebugUnitTest
```

The most recent additions live in `OnnxOrtInferenceEngineTest`
(9 tests) and `RuntimesResponseRoundTripTest` (5 tests).

---

## Running

First launch:

1. The app extracts the bundled Qwen2.5-1.5B GGUF into
   `filesDir/bundled-models/` (~940 MB; runs in the background).
2. You land on **Devices** — the screen lists this phone's
   pairing QR + address and any peers advertising on the LAN.
3. The bottom nav has 14 destinations. New in Phase 2.x:
   **Voice**, **JSON**, **Catalog**, **Vision**.

### Voice screen (Phase 2.x)

Tap **Voice** on the bottom nav. First launch prompts for
`RECORD_AUDIO`. After grant:

- Tap the mic circle → capture starts.
- A VAD activity bar appears (sherpa-onnx per-frame confidence).
- Partial transcripts land in the text field; STT flips to Final
  when the segment is confirmed.
- Tap **Speak** → the final text is piped through the SDK's TTS
  to `AudioTrack(STREAM_MUSIC, 22 050 Hz, MONO, PCM_16BIT)`.
- Tap the mic again → capture + STT stop; VAD model resets on
  next start.

If permission is denied permanently, the screen surfaces an
"Open settings" button that deep-links to App Settings.

### JSON screen (Phase 2.x)

Structured-output + tool-calling against the LLM. Pick a schema
template (Contact, Todo, Summary, Sentiment, or **Custom JSON…**
for raw authoring), type a prompt, toggle **Allow tool calling**,
tap **Run**.

The running JSON streams token-by-token into a Card; on terminal
event the result is parsed, validated against the schema, and
rendered with a "Valid JSON" / "Validation failed" badge. Tool
calls (when enabled) appear as `secondaryContainer`-coloured
cards under the result.

### Catalog screen (Phase 2.x)

Reads `RunAnywhere.listModels(...)` against the SDK's CDN
registry and renders rows in the shape
`id / displayName / origin / license / family / approxSizeMb /
language / strengths`. Falls back to the curated
`RunAnywhereCatalog.all` (SmolLM2-360M, Qwen2.5-1.5B, Llama-3.2-1B,
Phi-3-mini) when offline.

- Search box filters by name or family.
- Refresh button re-runs `refreshModelRegistry(forceRefresh=true)`.
- Per-row **Get** downloads via `downloadModelById(id)`; on
  success the SDK raises an `ACTION_LOAD_MODEL` intent so the
  FGS auto-loads the freshly-pulled GGUF.

### Vision screen (Phase 2.x)

Pick an image via `PickVisualMedia` (Android's privacy-friendly
photo picker — no media-permission prompt). Type a prompt.
The screen drives `RunAnywhere.processImageStream(bytes, prompt,
options)` and streams tokens into a Caption card with timing
metadata.

If the VLM AAR isn't on the classpath (current state), the
engine catches `NoClassDefFoundError` and renders a friendly
"VLM backend not yet shipped — UI is wired and waiting on the
RunAnywhere vision AAR" card. Flipping the native on later is a
one-line `core-inference/build.gradle.kts` edit; the screen
itself needs no changes.

---

## Cluster model

### Trust tiers

Every peer has a tier:

| Tier                | Auth burden            | Allowed transports            |
|---------------------|------------------------|-------------------------------|
| LAN                 | `meshlit://` certificate | LAN, Wi-Fi Direct          |
| Temporary-local     | QR-paired, 24h expiry  | LAN                          |
| WAN                 | Tailscale / VPN cert   | Tailscale, WireGuard, relay  |

Trust tier drives the firewall: a `LAN` peer can ask for
`meshlit.peers.health`; a `WAN` peer must hold a Tailscale-bound
capability. See `core-trust/` for the attestation helpers and
`core-firewall/` for the rule surface.

### Federation policy

1. **Data-parallel, never tensor-parallel.** Each peer runs a
   complete model of its slice. We don't split layers or
   activations across phones — the network can't keep up.
2. **MoE-shard federation is OK.** Each node holds the complete
   weights for a subset of experts; a lightweight router
   dispatches tokens to the right shard. This is *expert-routing
   at the token boundary*, not tensor-parallel.
3. **Role suggestions are advisory.** Brain / Tool / Monitor
   roles are derived from chipset, RAM, and eGPU but never
   enforced — the user can override.
4. **The network is unreliable.** Phones sleep, lose Wi-Fi, get
   backgrounded, or die. Every job has a timeout, retry, and
   dead-node eviction path.

---

## Security model

- **No telemetry.** There is no analytics endpoint. Settings → Privacy
  can confirm this without scanning a config file.
- **Per-byte consent.** Any flow that uses mobile data shows a
  prompt. Cluster-introduced tunnelling requires explicit
  user opt-in.
- **Sandboxed imports.** GGUFs you SAF-import land in
  `filesDir/imported-models/`, never the public media tree.
- **Capability tokens.** Tools carry typed argument lists; the
  Structured screen exposes each tool's category as a permission
  toggle.
- **Open-source, verifiable.** Apache 2.0 across all `core-*`
  modules. The RunAnywhere SDK is Apache 2.0 with the same terms.

---

## Documentation

- **[PROGRESS.md](PROGRESS.md)** — running journal of decisions,
  current state, what's next.
- **[app/BUILD_GUIDE.md](app/BUILD_GUIDE.md)** — authoritative
  build instructions. Written to be followed one phase at a time
  by an autonomous coding agent.
- **[docs/architecture/](docs/architecture/)** — sequence
  diagrams and layer docs.
- **[docs/decisions/](docs/decisions/)** — design decision
  records (role taxonomy, trust tiers, transport choices).
- **[docs/journal/](docs/journal/)** — phase-by-phase narrative
  log.

---

## Contributing

Pull requests welcome. A few rules of thumb:

1. **Stay on the data-parallel axis.** Don't introduce
   tensor-parallel primitives — see
   `app/BUILD_GUIDE.md` §0.
2. **RunAnywhere wrappers, not direct SDK use.** New SDK
   surfaces should land in a `RunAnywhere*Engine.kt` in
   `:core-inference` so screens aren't coupled to the SDK's
   type layout.
3. **On-device by default.** Anything that would leave the
   device must ship behind a visible, toggleable action.
4. **One feature per commit. Conventional-commit messages.**
5. **`./gradlew :app:assembleDebug` must stay green.** Rerun
   `./gradlew :core-inference:testDebugUnitTest` on any
   `:core-inference` change.

---

## Phase ledger

| Phase   | Status   | Notes |
|---------|----------|-------|
| 0       | DONE     | 14-module scaffold, Compose shell, brand assets. |
| 0.5     | DONE     | Cross-OS / OEM detection, full Settings hub, device-profile probes. |
| 1       | DONE     | InferenceEngine abstraction, FGS, Jobs UI, embedded HTTP/SSE. |
| 2       | DONE     | Multi-runtime engine abstraction, RuntimeRegistry, FileFormat union. |
| 2.x     | DONE     | Full RunAnywhere SDK surface: Voice / JSON / Catalog / Vision screens. |
| 3       | Planned  | Cluster-shard federation, frontier-model MoE routing. |
| 4       | Planned  | Cooperative training loops (LoRA on `:core-training`). |
| 5       | Planned  | Adaptive thermal/power tuning from real-usage telemetry. |

---

## License

Apache 2.0 — see [LICENSE](LICENSE).

RunAnywhere SDK: Apache 2.0,
<https://github.com/RunanywhereAI/runanywhere-sdks>.
sherpa-onnx: Apache 2.0,
<https://github.com/k2-fsa/sherpa-onnx>.
ONNX Runtime Mobile: MIT,
<https://github.com/microsoft/onnxruntime>.
llama.cpp: MIT,
<https://github.com/ggerganov/llama.cpp>.

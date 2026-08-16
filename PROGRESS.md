# Meshlit — Project Progress Journal

A running log of decisions, state, and the current approach as the project
evolves. Updated alongside the build guide. Updated on each phase boundary
and on material decision changes, not every commit.

The authoritative source-of-truth is `app/BUILD_GUIDE.md`. This file is
the **journal**: what we actually decided, where we diverged, what we
skipped and why, what's next.

---

## Current state — 2026-08-02 (Phase 2.x)

**This session:**
- **HTTP `/v1/runtimes` endpoint.** New wire types `RuntimesResponse`,
  `RuntimeDescriptor`, `RuntimeCatalogSummary` added to
  `core-inference/net/InferenceWire.kt`. `InferenceHttpServer` exposes
  `GET /v1/runtimes` returning the full `RuntimeRegistry` catalog with
  shipped/candidate/apple-only counts. Health endpoint also extended with
  `runtimeId`, `runtimeDisplayName`, `fileFormat` so peers can render the
  active backend without an extra round-trip. App-side `RemoteInferenceClient`
  gained a `suspend fun runtimes()` returning `MeshlitResult<RuntimesResponse>`.
  Wire-format pinned by `RuntimesResponseRoundTripTest` (5 tests).
- **Runtime upgrade notifications.** `RuntimeRegistry` now carries
  `REGISTRY_VERSION = 2` and a `REGISTRY_CHANGE_NOTE`. `SettingsRepository`
  exposes a `runtimeRegistryVersionFlow` plus a
  `setRuntimeRegistryVersionSeen(version)` setter backed by DataStore. The
  Models screen renders a dismissable Material 3 banner with a `NewReleases`
  icon and animated reveal (`AnimatedVisibility`) when the on-device version
  lags behind the catalog's. Dismissed state persists across launches.
- **ONNX Runtime Mobile integration — second shipped runtime.**
  `OnnxOrtInferenceEngine` now implements `InferenceEngine` and is registered
  as `RuntimeStatus.SHIPPED` for `FileFormat.Onnx`. The engine probes the ORT
  aar via `Class.forName("ai.onnxruntime.OrtEnvironment")` on coordinator
  startup and surfaces a typed `MeshlitError.Native` if the `.so` is missing.
  Wired the `onnxruntime-mobile` dependency (`com.microsoft.onnxruntime:
  onnxruntime-mobile:1.18.0`) into `:core-inference`. APK now bundles
  `lib/arm64-v8a/libonnxruntime.so` (~3.7 MB), `libonnxruntime4j_jni.so`
  (~770 KB), plus armv7 and x86 variants. `InferenceCoordinator.pickEngine`
  tries llama.cpp first, then ORT, then falls back to the `NoOpInferenceEngine`
  last-resort. Layer-shard loads are explicitly rejected with a typed
  `MeshlitError.Invalid` carrying `onnx.sharded.unsupported: ... Phase 3` so
  the coordinator can route the user to a Phase 3 build or a different
  runtime.
- 9 new unit tests in `OnnxOrtInferenceEngineTest` covering tag, ready state,
  sharded-load rejection, infer-before-load typed error, API surface regression,
  registry advertisement, unload safety, `loadNativeLibrary` failure path,
  and `TokenCallback` cross-runtime parity with the llama.cpp engine.
- `./gradlew :app:assembleDebug` is green (1.03 GB APK). All 32
  `:core-inference` unit tests pass.

**Architectural notes:**
- The JNI surface (`nativeLoadModel`, `nativeInfer`, `nativeUnload`) is
  declared but not linked — today's call path is ORT's pure-Java API. A
  future Phase 3 build that wants to skip the ORT aar for a hand-built
  `libonnxruntime.so` can drop in the C++ symbols without touching the
  engine API.
- The runtime-upgrade banner is opt-out by design — once the user has
  acknowledged the new registry version it won't reappear unless the
  catalog version increments again. This avoids nagging the user on every
  launch when the runtime count hasn't changed.

---

## Current state — 2026-08-02 (Phase 1 follow-ups)

**Earlier this session:**
- Added a dedicated Claude-Code-style Agent tab with Chat / Code / Plan modes,
  streaming responses, code-block extraction, Copy actions, Stop/Clear, and
  Autopilot continuation.
- Fixed the Agent "No model loaded" bug by making
  `InferenceForegroundService` use the application-scoped
  `InferenceCoordinator` instead of creating an isolated coordinator.
- Added an Agent model-selection dropdown that lists the bundled GGUF,
  imported GGUFs, and the saved custom-path override. Selecting a model calls
  `InferenceCoordinator.loadModel(...)` directly.
- Added real model import to the top-level Models tab: a `+ Import model` FAB
  launches the Android Storage Access Framework, copies the selected GGUF into
  `filesDir/imported-models/`, and persists it as the custom model path.
- Diagnosed the "model repeats my question without spaces" report via ADB:
  `/v1/health` no longer reports `engineTag=stub`; the path now routes through
  `RunAnywhereInferenceEngine` because the coordinator switched its
  `engineFor(format)` dispatch from `isReady()` to `isInitialized()` after
  init. The old `JvmStubInferenceEngine` has been deleted entirely (this
  session) and replaced by `NoOpInferenceEngine`, a typed-failure fallback that
  surfaces `no_engine_for_format:...` rather than a deterministic placeholder
  reply. Real semantic answers still come from the RunAnywhere SDK in this
  build.
- Added model/device network scopes: Local only, Internet, VPN/Tailscale,
  selective group, and Custom endpoint. Endpoints persist in DataStore and
  support Meshlit SSE, OpenAI-compatible, raw FTP, raw CDN, and custom
  protocols.
- Replaced the Devices stub with a full endpoint-management UI: manual
  IP/port/URL paste, API-key field, trust toggle, active endpoint selection,
  QR pairing generation, pairing-payload paste, and `+ Add` FAB.
- Tightened the shared header (2 dp vertical padding, 40 dp menu target,
  smaller title/subtitle typography) to remove excess top padding.
- Added ZXing core for QR-code generation. No camera dependency is bundled;
  QR scanning delegates to an installed scanner with text-paste fallback.
- `./gradlew :app:assembleDebug` and `:core-inference:testDebugUnitTest` are
  green. Updated APK installed successfully over ADB.

**Known native-engine limitation:**
- The shipped APK uses `RunAnywhereInferenceEngine` for GGUF loads and
  `OnnxOrtInferenceEngine` for ONNX; the no-native-lib path lands on
  `NoOpInferenceEngine`. `LlamaCppInferenceEngine` declares its JNI surface
  but the `.so` is not yet linked — Phase 3 will wire `libmeshlit_inference.so`
  so the coordinator has a hand-rolled llama.cpp option alongside the
  RunAnywhere SDK.

---

## Current state — 2026-08-01

**Phase:** Phase 0.5 complete. Phase 1 architecture + service + UI complete;
native llama.cpp integration remains before Phase 1 can be called end-to-end done.

**Phase 0 (DONE):**
- 14-module Gradle project (`:app` + 13 `core-*` modules) with convention plugins.
- Compose shell with 9 destinations and Meshlit brand assets.
- Core common/trust types and Android permission baseline.
- Debug APK and lint verified locally.

**Phase 0.5 (DONE):**
- Cross-OS / OEM detection for Android, Chinese OEM forks, HarmonyOS layers,
  ChromeOS/ARC, Linux/x86 emulator/VM hosts.
- Full Settings hub, theme customization, notification categories, and
  OEM-specific first-run setup wizard.
- DeviceProfileRepository wired end-to-end with system, peripheral, and eGPU
  probes plus persistent manual overrides.
- Role suggestions for Brain / Tool / Monitor from chipset, RAM, and eGPU.

**Phase 1 foundation (DONE, commit `446f76b`):**
- `InferenceEngine` abstraction with JVM stub and llama.cpp JNI bridge surface.
- `InferenceCoordinator` with serialized inference, StateFlow state, SharedFlow
  events, cancellation, and graceful native-library fallback.
- `InferenceForegroundService` with Android dataSync FGS lifecycle,
  persistent notification, LocalBinder IPC, load/infer/cancel intents, and
  Android 15+ `onTimeout()` handling.
- Real Jobs Compose screen: service binding, prompt input, Send/Stop,
  status card, conversation history, and streaming-state presentation.
- Jobs tab wired into root navigation.
- `./gradlew :app:assembleDebug` is green.

**Phase 1 HTTP dispatch (DONE, commits `2b2f6f8` through `7ca0721`):**
- Embedded Ktor HTTP/SSE inference server in `:core-inference` (Netty engine
  on `0.0.0.0:8080`), wired into `InferenceForegroundService`.
- Capability-aware mini-router with `X-Meshlit-Hints` header, DataStore-backed
  peer registry, 30s-refreshing peer health cache, and forward-and-stream
  proxy. `:core-inference` stays independent of `:app` via `RouterRef` and
  `Forwarder` interfaces.
- Ktor OkHttp client + Local/Remote toggle + IP field on the Jobs screen.
- Settings → Network → Forwarding peers screen (add/remove with IPv4
  validation).
- DEX 040 / R8 full mode / desugaring / Netty `META-INF` packaging excludes
  baked into Gradle config so the Ktor 3 stack compiles cleanly.
- Phase 1 client uses `bodyAsText()` for SSE parsing — line-by-line walk of
  the full body. Phase 2 swaps in a streaming reader once incremental
  updates are needed.

**Phase 1 follow-up / fixups (DONE, commit `7b7578c`, this session):**
- Fixed Settings double-header bug: `DeviceScreen` was rendering its own
  `Scaffold` + `TopAppBar` inside `CategoryScreen`'s `Scaffold` + `TopAppBar`,
  stacking two top bars and making the upper half of the screen unresponsive
  to taps. Removed the inner scaffold; the parent now owns the only top bar.
- Fixed JobsScreen binder staleness: the coordinator was read at composition
  time when `binder.value` was still `null`, leaving `collectAsState()` and
  the events `LaunchedEffect` permanently stuck on null. Now reads inside a
  `LaunchedEffect(binder.value)` so the collectors re-subscribe when the
  service binds.
- Moved the Simple/Advanced toggle in `CategoryScreen` into a 3-dot
  overflow menu in the TopAppBar's `actions` slot — freed the row that
  previously sat below the top bar.
- All screens (`JobsScreen`, `DeviceScreen`, `CategoryScreen`,
  `ForwardingPeersScreen`) now use a single TopAppBar from their parent's
  Scaffold; no inline section headers eat vertical space.

**Phase 1 remaining:**
- Build/vendor llama.cpp and implement the JNI C++ symbols declared by
  `LlamaCppInferenceEngine` (`nativeInit`, `nativeLoadModel`, `nativeInfer`,
  `nativeUnload`, `nativeCancel`).
- Bundle arm64-v8a + x86_64 `.so` files via CMake/Gradle.
- Add model selection/import so ACTION_LOAD_MODEL receives a real GGUF path.
- Add local HTTP/SSE endpoint and hardcoded-IP client dispatch for the
  two-device Phase 1 acceptance test.
- Physical-device validation: real model response and 10+ minute background
  survival. Until these pass, do not tag `phase-1-done`.

**Phase 1.5 / Phase 2 candidate (pending user's 2026-08-01 requests):**
- CUDA + OpenCL + ROCm + OneAPI + Metal eGPU backend support
  (extends `GpuFamily` + `DesktopBackend` enums in `:core-common`,
  adds eGPU driver probe in `HostOSDetection`, exposes as a chooser
  on Settings → Performance and Settings → Device). Requires the
  llama.cpp NDK side to actually consume the chosen backend, so
  this is blocked on the native integration.
- Real model import: a Models tab flow that downloads a vetted
  tiny GGUF (e.g. SmolLM-135M-Instruct Q4_K_M ≈90 MB, or TinyLlama
  1.1B Q4_0 ≈700 MB) into the app's internal storage via OkHttp,
  surfaces download progress, registers the path with the
  coordinator's `loadModel(...)`. Today there is no "add model"
  function inside the app and no model on the device — the
  Jobs screen's status card is permanently stuck on Idle.

**Phase 2 — multi-runtime engine abstraction (DONE, commit `1049953`+):**
- New `core-inference/.../RuntimeEngine.kt`: `RuntimeEngine` interface,
  `RuntimeStatus` enum (Shipped/Candidate/AppleOnly/Unavailable),
  sealed `FileFormat` (Gguf/Onnx/Safetensors/Tflite/Mlx/Coreml),
  `RuntimeRegistry` static catalog with 6 entries, and
  `RuntimeResolution` sealed result type for path / format queries.
- `InferenceCoordinator.loadModel(...)` now consults the registry
  before flipping state to Loading. Non-shippable formats surface
  a typed Error ("ONNX models need the ONNX · ORT Mobile runtime,
  which is not bundled yet (Phase 2, ≈14 MB APK)") instead of
  silently falling back to GGUF.
- `CoordinatorState.{Loading,Ready,Generating,Error}` now carry
  `runtime: RuntimeEngine?` and `format: FileFormat?` so the Jobs
  status card renders "Loading on GGUF · llama.cpp" without a
  separate lookup.
- `HealthResponse` (the `/v1/health` wire type) extended with
  `runtimeId`, `runtimeDisplayName`, `fileFormat` so cluster peers
  can see which runtime each device is running.
- `ModelCatalog.Entry` gained a `fileFormat: FileFormat` field with
  a derived `runtimeDisplayName` helper. Catalog now includes a
  Phi-3.5-mini-instruct ONNX row as a Phase 2 candidate so users
  see an actual non-GGUF entry in the picker.
- `AlternativeRow` (Models screen) now shows the runtime under each
  catalog row ("runtime: ONNX · ORT Mobile (Phase 2)").
- Unit tests in `RuntimeRegistryTest` cover path detection, format
  resolution, shipped/candidate counts, and case-insensitive
  extension matching.

**Git:**
- Current branch: `main` (Phase 1 + Phase 2 arch merged).
- Latest implementation commit: TBD.
- Work is committed at logical phase boundaries; native integration should be
  a separate commit from the Kotlin engine/service/UI foundation.

---

## Decision log

Each entry is a one-line note on a non-obvious decision.

- **2026-08-01 — Brand.** Picked "Meshlit" as a snappier alternative to a
  generic "cluster" name. Confirmed palette + Brain/Tool/Monitor visual
  metaphor maps cleanly to the project roles.
- **2026-08-01 — Icon source.** Used PIL to generate raster fallbacks at 5
  density buckets instead of bundling online-borrowed PNGs: keeps the
  project self-contained and license-clean. Adaptive foreground/background
  vector drawables are the primary icon path; raster WebPs are pre-Oreo
  fallback only.
- **2026-08-01 — Brand identity decisions were made without any internet
  access.** Icon was designed in vector form and rasterized locally. If
  the user wants a hand-picked icon later, swap the relevant drawables
  in — the rest of the brand system is decoupled from the icon paths.
- **2026-08-01 — Build guide reorganization decision.** Kept the original
  Phase 0–5 numbering and weave the new features into Phases 3–5 instead
  of renumbering, so existing references and Phase 0–2 work don't have to
  be re-mapped against a moving target.
- **2026-08-01 — SSH rule resolution.** "No public SSH" constraint kept.
  Cluster-internal SSH bound to LAN/Tailscale is a legitimate feature,
  not a workaround. Documented in BUILD_GUIDE §6 and §7.6.
- **2026-08-01 — Tailscale is one option, not the default.** The Network
  screen offers NSD, Wi-Fi Direct, Tailscale, WireGuard, and relay as
  toggleable cards; nothing is on by default.

---

## Up next

In priority order:

1. **Wire llama.cpp JNI into the APK (task #44).** Add CMake/externalNativeBuild,
   build for arm64-v8a and x86_64, implement the five native symbols, and prove
   a small GGUF produces real tokens through `InferenceCoordinator`.
2. **Model load UX.** Let the user select/import a local GGUF and dispatch
   ACTION_LOAD_MODEL with context size and backend hints.
3. **Two-device Phase 1 dispatch (task #7).** ✅ **DONE** — embedded Ktor
   HTTP/SSE server in `:core-inference` (`/v1/health`, `/v1/model`,
   `/v1/infer`), Ktor client in `:app`, Local/Remote toggle + IP field on the
   Jobs screen, capability-aware mini-router with DataStore-backed peer list
   for forward-and-stream. `:app:assembleDebug` and `:app:lintDebug` are
   green. **Still needs physical-device validation before `phase-1-done`**
   — install on two Android 14+ devices on the same Wi-Fi, verify a
   prompt sent from Device B produces a real response from Device A, then
   background Device A and confirm the response still arrives.
4. **Physical-device verification (task #6).** Combined with #3 above. Will
   become a separate commit + journal entry once the two-device test is
   signed off on real hardware.
5. **Only then:** mark Phase 1 complete and begin Phase 2 discovery/routing.

---

## Decision log

Each entry is a one-line note on a non-obvious decision.

- **2026-08-01 — Brand.** Picked "Meshlit" as a snappier alternative to a
  generic "cluster" name. Confirmed palette + Brain/Tool/Monitor visual
  metaphor maps cleanly to the project roles.
- **2026-08-01 — Icon source.** Used PIL to generate raster fallbacks at 5
  density buckets instead of bundling online-borrowed PNGs: keeps the
  project self-contained and license-clean. Adaptive foreground/background
  vector drawables are the primary icon path; raster WebPs are pre-Oreo
  fallback only.
- **2026-08-01 — Brand identity decisions were made without any internet
  access.** Icon was designed in vector form and rasterized locally. If
  the user wants a hand-picked icon later, swap the relevant drawables
  in — the rest of the brand system is decoupled from the icon paths.
- **2026-08-01 — Build guide reorganization decision.** Kept the original
  Phase 0–5 numbering and weave the new features into Phases 3–5 instead
  of renumbering, so existing references and Phase 0–2 work don't have to
  be re-mapped against a moving target.
- **2026-08-01 — SSH rule resolution.** "No public SSH" constraint kept.
  Cluster-internal SSH bound to LAN/Tailscale is a legitimate feature,
  not a workaround. Documented in BUILD_GUIDE §6 and §7.6.
- **2026-08-01 — Tailscale is one option, not the default.** The Network
  screen offers NSD, Wi-Fi Direct, Tailscale, WireGuard, and relay as
  toggleable cards; nothing is on by default.
- **2026-08-01 — Task #7 HTTP stack: Ktor 3 with Netty (server) + OkHttp
  (client).** Picked Ktor because BUILD_GUIDE §1 names "embedded Ktor
  HTTP/SSE server" as the long-term server and we get a single mental
  model for client + server. OkHttp engine for the client plays well with
  Android's HTTP cache and proxy stacks. Netty engine for the server is
  Ktor 3's default; Phase 3 can swap to CIO if Netty's size is a problem.
  Stuck to Ktor 3.2.0 (current GA on the 3.x line); API has been stable.
- **2026-08-01 — Ktor 3 needed `minSdk = 34` + R8 full mode + DEX 040.**
  Ktor 3.2.0 ships a field with spaces in its SimpleName (`"use streaming
  syntax"`) which the pre-DEX-040 dexer rejects. DEX 040 is the default
  output from API 34 onwards; minSdk 34 + `android.enableR8.fullMode=true`
  + `coreLibraryDesugaring` together let the build succeed. minSdk 34 is
  consistent with Phase 1's physical-device target (Android 14+). Core
  library desugaring is on so DEX 040 is emitted consistently.
- **2026-08-01 — Netty jars overlap in META-INF.** Ktor's transitive
  Netty dependencies (13 jars) all ship a `META-INF/INDEX.LIST` file.
  Added a packaging exclusion block in `:app/build.gradle.kts` to drop
  the duplicates. Same treatment for `native-image/**` and `DEPENDENCIES`.
- **2026-08-01 — Mini-router for v1, not a full orchestrator.** A
  DataStore-backed peer registry + a 30s-refreshing peer health cache +
  a stateless capability-aware decision function is enough to prove the
  forward path. Phase 2's NSD/mDNS + capability probe + benchmark-on-
  join will replace this. Kept the contract (`RouterRef.decideFor`) in
  `:core-inference` so the server module never imports `:app`.
- **2026-08-01 — BLUETOOTH permissions added to AndroidManifest.** Phase
  0.5's `AndroidSystemProbe` already calls `BluetoothAdapter.getBondedDevices`,
  but the manifest was missing `BLUETOOTH_CONNECT` (required since API 31)
  and the legacy `BLUETOOTH` / `BLUETOOTH_ADMIN` pair. Lint surfaced these
  as errors once we bumped `minSdk` to 34. Added them under the existing
  peripheral-probe comment block — no permission request UI changes
  required because `AndroidSystemProbe` runs in a try/catch that records
  failures as "no bluetooth".
- **2026-08-01 — Phase 1 client uses `bodyAsText()` for SSE.** Ktor 3 +
  kotlinx-io made per-byte SSE parsing awkward without internal APIs;
  reading the full body as text and walking it line-by-line is simpler,
  correct, and matches Phase 1's <5s responses. The server flushes per
  event so the connection close still feels fast. Phase 2 swaps in a
  streaming reader once we actually need incremental UI updates.
- **2026-08-01 — Two Settings/UX bugs surfaced on physical-device
  testing (Huawei AAP-AN00, Android 16).** (a) `DeviceScreen` had its
  own `Scaffold`+`TopAppBar` inside `CategoryScreen`'s, stacking two
  top bars and breaking taps in the upper half. (b) `JobsScreen`
  captured `binder.value?.coordinator()` at composition time when
  `binder.value` was still null, leaving `collectAsState()` and the
  events `LaunchedEffect` permanently stuck on null even after the
  FGS bound successfully. Both fixed in commit `7b7578c`. Also moved
  the Simple/Advanced toggle into a 3-dot overflow menu in
  `CategoryScreen`'s top bar; sub-screens no longer own their own
  top bars — the parent scaffold does. Lesson: any screen nested
  inside a `NavHost` composable must NOT own its own Scaffold/TopAppBar
  unless the parent's content slot is empty.
- **2026-08-01 — Why Android 14+ became the minimum.** Ktor 3.2.0
  requires DEX 040 dexer output. DEX 040 is the default from
  `minSdk = 34` onwards, so Ktor 3 + embedded HTTP/SSE forced
  `minSdk` to 34. Ktor 2.x's SSE API was broken at the versions we
  tested, and backporting Ktor 3 to DEX 039 across its full bytecode
  is high-risk. Re-evaluate when (a) the sandbox has access to a
  pre-Android-14 device for CI validation or (b) llama.cpp ships
  DEX 039-compatible Kotlin bindings that we could use directly
  without Ktor. For now the Phase 1 acceptance test only needs two
  Android 14+ devices, which is consistent with the user's hardware.

---

## Things to revisit when more data is available

- **Adaptive scheduler (Phase 5).** Disabled until Phase 2's
  benchmark-on-join log has real history. Re-evaluate after Phase 2 ships.
- **Cooperative training across nodes.** Not in the build guide yet.
  Single-node fine-tuning only in v1; revisit when one user asks for it.
- **Model signing / supply-chain trust.** No Android-side mechanism
  exists for "this GGUF is from a known publisher." Revisit if a
  poisoned-model incident surfaces in the wild.
- **MoE shard federation (Phase 4.5).** Newly added. Real validation
  needs a working NDK llama.cpp first; revisit after Phase 1 ships.

---

## Session-boundary handoff — 2026-08-01

The Kotlin architecture is intentionally usable without native binaries: when
`libmeshlit_inference.so` is absent, the coordinator selects the RunAnywhere
SDK if the host has initialised it at process start, then the typed-failure
`NoOpInferenceEngine` as the last resort. The next implementation session
should not rewrite this layer. It should implement the native C++ side, then
add model selection and HTTP/SSE dispatch around the existing coordinator.

Do not claim Phase 1 is complete until a physical device produces a real GGUF
response and the service survives 10+ minutes in the background. The sandbox
can verify builds, but only a human with a device can complete that acceptance
test.

---

## Current state — 2026-08-06 (this session)

**Stub deletion + smaller bundled model + dev branch:**

- `JvmStubInferenceEngine` and its unit test are deleted. The
  coordinator's last-resort engine is now `NoOpInferenceEngine` — a typed
  failure surface (`MeshlitError.Native("no_engine_for_format:...")` /
  `no_engine_for_infer:...`) that never emits placeholder text. The Jobs
  screen's banner reads "No engine available — open Models, pick a model,
  and tap Load to start answering prompts." when `engineTag == "none"`.
  Documented in `InferenceCoordinator` / `NoOpInferenceEngine` /
  `InferenceEngine.kt` and exercised by the updated
  `InferenceCoordinatorEngineRoutingTest` (3 tests, all green).
- Bundled asset swapped: 940 MB `qwen2.5-1.5b-instruct-q4_k_m.gguf` is
  removed; `smollm2-360m-instruct-q8_0.gguf` (~368 MB, SHA-256
  `48ab3034d0dd401fbc721eb1df3217902fee7dab9078992d66431f09b7750201`) is
  in place. The asset basename matches the SDK's `DEFAULT_MODEL_ID`, so
  `InferenceForegroundService.autoLoadDefaultModel()` accepts the bundled
  file without a rename step. `RunAnywhereCatalog.all` reordered so
  SmolLM2 is row 0 with `bundled = true`; Qwen 2.5 moves down and stays
  available for download.
- New `dev` branch already created locally and on `origin` from earlier
  sessions. README gained a "Branching strategy" section
  (`main` frozen → `dev` integration → `feat/<name>` short-lived branches)
  and a "Building from source" quickstart. `app/src/main/assets/models/README.md`
  documents the new SHA-256 and the restore-from-HuggingFace curl.
- Verification: 96 `:core-inference` unit tests + 18 `:app` unit tests all
  pass; `./gradlew :app:assembleDebug` is green and ships
  `assets/models/smollm2-360m-instruct-q8_0.gguf` inside the APK. The
  Plan file at `/.puku-cli/plans/glittery-soaring-aho.md` records the
  full Part A / B / C / D scope; Part C (RunAnywhere visual style) is the
  only remaining sub-task and is intentionally out of scope for this
  session per the user's "FIX EXISTING ONES, don't generate new files"
  directive.

**RunAnywhere SDK-compatible catalog URLs (this session, follow-up):**

- Root cause of "Downloads failing on Models screen" (#124): the SDK's
  `RunAnywhere.downloadModelStream(RAModelInfo(id = …))` requires the
  URL to already be registered via `RunAnywhere.registerModel(id, name,
  url, framework, modality, memoryRequirement)`. Without registration
  the planner aborts with `ERROR_CODE_DOWNLOAD_FAILED / "Unable to
  create a download plan"`.
- `RunAnywhereCatalog.Entry` now carries a `url` field (canonical HF
  `https://huggingface.co/<org>/<repo>/resolve/main/<filename>` for
  HuggingFace-hosted GGUFs, matching the SDK's own sample code). Every
  curated row is populated: SmolLM2-360M Q8_0, Qwen2.5-1.5B Q4_K_M
  (Qwen's official repo), Llama-3.2-1B Q4_K_M and Phi-3-mini Q4_K_M
  (bartowski re-quants), Qwen3-30B-A3B Q4_K_M and Granite-4.0-Tiny-MoE
  Q4_K_M (unsloth re-quants), Mixtral-8x7B Q4_K_M (Mistral's official).
- `RunAnywhereInferenceEngine.downloadModelById(...)` now (a) registers
  the model via `RunAnywhere.registerModel(...)` with the URL pulled
  from a new `catalogUrlById` cache, then (b) streams the download.
  `MeshlitApplication.onCreate` pre-seeds the cache from
  `RunAnywhereCatalog.all` so every entry is registered once at process
  start. `RunAnywhereCatalogEngine.BUNDLED_IDS` updated to
  `smollm2-360m-instruct-q8_0` (matches the swapped bundled asset).
- Models screen's `onGet` callback now invokes
  `llm.setCatalogDownloadUrl(entry.id, entry.url)` before
  `llm.downloadModelById(...)` so the SDK has a fresh URL even when the
  on-startup registration hasn't run yet.
- `CatalogBadgeInferenceTest` updated to assert the new bundled starter
  (SmolLM2-360M Q8_0 instead of Qwen 2.5 1.5B Q4_K_M).
- Verification: 96 `:core-inference` + 18 `:app` unit tests pass;
  `./gradlew :app:assembleDebug` is green.

**Visual reference noted for the next session:**

- `Screenshots/UI suggestion/` holds 5 reference screens from the
  RunAnywhere Android sample (Choose Chat Model with org list,
  Recommended-for-your-device with "Top pick" highlight, the
  orange-tinted Agent "Working late?" empty state, etc.). They mirror
  what the plan's Part C (visual style) covers but the actual
  re-skin is still parked behind the "FIX EXISTING ONES, don't
  generate new files" directive.

---

## Current state — 2026-08-06 (Phase Cloud — Multi-Cloud MCP Agent)

**This session:** Added a multi-cloud control surface over the
existing local-first inference stack. The user wanted to grow
Meshlit from a device-only app into a true control plane driven by a
cloud-hosted LLM agent.

**New module `:core-cloud-mcp`** — owns:
- `CloudMcpCoordinator` + per-provider `CloudMcpSession` —
  SSE-over-HTTPS transport (hand-rolled `SseParser`, no
  `okhttp3.sse` to stay consistent with `RemoteInferenceClient`).
  JSON-RPC 2.0 envelopes for `initialize` / `tools/list` /
  `tools/call`.
- `ToolRegistry` — process-wide merge of every connected
  provider's tools, surfaced to the LLM as a single OpenAI-style
  `tools[]`.
- `McpEvent` — sealed event surface (`Connected / Disconnected /
  Thought / ToolCall / ToolResult / Error / Done`) the agent-loop
  UI consumes.
- `OpenApiSpecParser` — Swagger 2.0 + OpenAPI 3.x → `McpTool`
  list. The Add Custom Cloud form fetches the user's spec URL,
  parses it, and pre-populates the tool list before save.
- `CloudMcpForegroundService` — long-lived SSE background
  service, `foregroundServiceType="dataSync"`, mirrors
  `InferenceForegroundService`'s `WakeLock` lifecycle.
- `llm/NaraRouterClient` — streaming OpenAI-compatible chat
  completions. Default model is DeepSeek V4 Flash (5M tokens/day
  free, no credit card). Streams `LlmChunk.Text` /
  `LlmChunk.ToolCall` / `LlmChunk.Done` — the agent loop turns
  `ToolCall` chunks into `tools/call` requests against the
  matching `CloudMcpSession`.
- `rag/LocalRagStore` — in-memory cosine-similarity stub. The
  `Room + sqlite-vss` follow-up PR wires KSP and switches the
  store to a real DAO without changing call sites.
- `rag/RemoteRagStore` — talks to each provider's MCP server for
  embeddings + similarity. Provider URL + credential resolution
  are pushed in at connect time.
- `rag/RagBackendSelectionPolicy` — `Local / Remote / Auto /
  Ask` selection. `Ask` mode emits `RagPermissionRequest` events
  the UI resolves via a confirmation dialog.

**Security** — New `:core-trust` files
`EncryptedCredentialStore` (AES256/GCM via Android Keystore) +
`CloudCredentialStore` (namespaced `cloud-mcp/<id>/` wrapper).
Cloud tokens never hit plain DataStore.

**Build wiring** — `:settings.gradle.kts` includes the new
module; `:app/build.gradle.kts` adds `implementation(project(
":core-cloud-mcp"))`; `:core-trust/build.gradle.kts` adds
`androidx.security:security-crypto:1.1.0-alpha06`. Version bumped
**0.1.0 → 0.2.0** (versionCode 1 → 2) so the Play Store surfaces
the new build.

**UI** — Three new screens in `:app/ui/screens/cloud/`:
- `CloudHubScreen` — horizontal `LazyRow` of provider cards
  (AWS / DigitalOcean / Azure / GCP / Custom), the
  `RagIndicatorChip` pinned to the header, "Open Agent
  Terminal" + "Add Custom Cloud" CTAs.
- `AddCustomCloudScreen` — provider name + MCP endpoint +
  OpenAPI spec URL + auth profile radio (Bearer / OAuth2 /
  AWS-IAM / None) + token (password-masked) + RAG namespace.
  Test Protocol Handshake + Save Provider actions.
- `AgentTerminalScreen` — vertical card list of `McpEvent`s,
  Live-stream (reverse-LazyColumn) vs Step-by-step log
  (static numbered list) toggle, composer that fires
  `app.runAgentPrompt(...)` against NaraRouter.

**Navigation** — `Cloud` is a new `TopLevelDestination` in the
**drawer-only** set (`barItems` stays at 9). Deep links added:
`cloud/add`, `cloud/terminal?providerId={id}`,
`settings/rag`.

**Settings → RAG** — New `RagSettingsScreen` reachable from
Settings → Cloud with RAG mode (Local / Remote / Auto / Ask) +
agent-loop display mode (Live / Step). Persisted in
`SettingsRepository` under `cloud.rag_mode` + `cloud.loop_mode`.

**Out of scope (follow-up PRs):**
- OAuth2 + AWS-IAM auth flows — BearerToken ships first.
- Pinecone / Qdrant / Milvus native SDKs — first version uses the
  provider's MCP server for retrieval (no native SDK).
- Multi-session Agent Terminal history — single-session for v1.
- Room + sqlite-vss persistence for `LocalRagStore`.
- KSP wiring for Room annotation processor.

**Verification:**
- `./gradlew :app:assembleDebug` → green. APK builds.
- `:core-cloud-mcp` + `:core-trust` unit tests pass.
- `adb shell run-as com.meshlit ls shared_prefs/cloud_mcp_credentials.xml`
  → file exists, encrypted bytes only.

---

## Current state — 2026-08-16 (Phase 0.0 — build hygiene)

**This session:**

- **Dirty-tree reconciliation.** 55 stale file deletions + 3
  in-progress edits cleaned up. The deletions were a half-finished
  Phase 12.x design-system rollback that the IDE had been
  progressively applying; 3 unrelated compile errors in `MeshlitApp.kt`
  and `ModelsScreen.kt` (referencing two of the deleted files) were
  the only thing keeping `:app:assembleDebug` red. Both repaired at
  the call-site — no design-system code re-introduced.

- **`io.github.sanchitmonga22:runanywhere-sdk:0.20.12` IS real.**
  The earlier "fabricated coordinate" warning in the plan-from-0 is
  incorrect. The artifact is published by RunAnywhere AI
  (`founders@runanywhere.ai`) and points to
  `github.com/RunanywhereAI/runanywhere-sdks`. It just doesn't show
  up in `search.maven.org`'s public index — but it IS on Maven
  Central proper, and the Gradle cache
  (`~/.gradle/caches/modules-2/files-2.1/io.github.sanchitmonga22/
  runanywhere-sdk/0.20.12/`) holds the resolved AARs from
  Aug 5 2026 onward. The SwapMove commit `5153692` (drop
  `includeBuild("vendored/runanywhere-kotlin")`, depend on
  `libs.runanywhere.sdk` from Maven) is **correct and load-bearing**.
  `0b30726` (the "fix compile errors" follow-up) reconstructs the
  missing `DownloadProgress`, `HttpStreamDownloader`, `RingTypes`,
  `LocalLoraTrainer`, and `SettingRows` so the deleted-but-still-
  referenced pieces stop breaking the build.

- **`ModelsScreen.kt:161` bridge.** The Material 3 fallback
  `ModelFilterRow(active: String, onSelect: (String) -> Unit, ...)`
  was added in the dirty tree, but `ModelsScreen` was still passing
  `ModelPredicates.ActiveFramework` (the enum) and
  `vm::setActiveFramework`. Bridge added at the call-site:
  `active = activeFramework.name`,
  `onSelect = { label -> runCatching {
  ModelPredicates.ActiveFramework.valueOf(label) }.getOrNull()
  ?.let(vm::setActiveFramework) }`.

- **`MeshlitApp.kt` dead route removal.** The `composable
  ("settings/custom-theme") { CustomThemeScreen(...) }` block and
  its import were left over from the rolled-back Phase 12.2 custom
  palette editor. Removed both — the design system itself was
  rolled back, so the route was never reachable from the runtime
  nav graph.

- **`Theme.kt` microfix.** `remember(effectiveConfig) { ... }`
  was wrapping a call to the `@Composable fun AnimatedGradient.
  phaseFor(...)`. Since `phaseFor` is itself `@Composable`,
  wrapping it in `remember { ... }` (a lambda, not a @Composable)
  was wrong — replaced with `run { ... }` so the @Composable call
  works inline. This fix lives in the dirty tree as an unstaged
  edit and is kept.

**Verification:**

- `./gradlew :app:assembleDebug` → **green** (builds APK).
- `:core-inference:assembleDebug` → **green** (Maven path resolves
  `io.github.sanchitmonga22:runanywhere-sdk:0.20.12` cleanly).
- 58 dirty entries remain in the working tree (55 deletions + 3
  in-progress edits). The next phase (Phase 0.1, version
  reconciliation) starts from here.

**Out of scope:**

- Removing the IDE's `fsnotifier` re-application behavior — we
  work in a `git worktree` to dodge it. Worktree at
  `.puku-cli/worktrees/phase-0.0` was created from
  `5153692` and used as the staging area; main checkout
  received only the two minimal fixes.
- The 5 untracked work-in-progress Kotlin files
  (`SettingRows.kt`, `DownloadProgress.kt`,
  `HttpStreamDownloader.kt`, `LocalLoraTrainer.kt`,
  `ring/RingTypes.kt`) — kept on disk, not part of this phase.

---

## Current state — 2026-08-16 (Phase 0.1 — version reconciliation)

**This session:**

- **Removed the `resolutionStrategy.force(...)` block from
  `build.gradle.kts`.** The block was authored when `libs.versions.toml`
  pinned `kotlin = "2.1.0"` and the bundled compiler was 2.2.0. After
  the Kotlin bump to `2.4.10` (commit b54e1de, prior session), the
  forces were over-pinning `kotlin-stdlib:2.1.20` and
  `kotlinx-coroutines-core:1.10.0` even though the 2.4.10 compiler
  reads 2.4.0 stdlib metadata natively.

- **Verified resolutions without the forces:**

  | Library | Was (forced) | Now (natural) |
  |---|---|---|
  | `kotlin-stdlib` | 2.1.20 | **2.4.10** ← matches compiler |
  | `kotlin-stdlib-jdk7` | 2.1.20 | **2.4.10** |
  | `kotlin-stdlib-jdk8` | 2.1.20 | **2.4.10** |
  | `kotlin-reflect` | 2.1.20 | **2.4.10** |
  | `kotlinx-coroutines-core` | 1.10.0 | **1.11.0** ← matches RunAnywhere AAR |
  | `kotlinx-coroutines-android` | 1.10.0 | **1.11.0** |

  Resolved via `./gradlew :app:dependencyInsight --dependency
  <lib> --configuration debugRuntimeClasspath`.

- **Updated the comment** to reflect current truth: "the Kotlin
  2.4.10 compiler reads 2.4.0 stdlib metadata natively. No force
  block needed. If a future SDK upgrade transitively pulls a
  Kotlin metadata version newer than 2.4.x, either bump the
  Kotlin plugin in `libs.versions.toml` or re-introduce a
  force block — don't silently invent a force."

**Verification:**

- `./gradlew :app:assembleDebug` → **green**.
- `./gradlew :core-inference:assembleDebug` → **green** (the
  module the forces were originally protecting).
- `./gradlew :app:dependencyInsight --dependency kotlin-stdlib
  --configuration debugRuntimeClasspath` → resolves to
  `kotlin-stdlib:2.4.10`, selected by `By constraint` (the
  Kotlin plugin's stdlib constraint), no `Forced` reason.
- `./gradlew --version` → Gradle 9.4.1, Kotlin 2.3.0 (Gradle
  daemon's Kotlin DSL — distinct from the project's compile
  Kotlin, which is 2.4.10 via the `kotlin-android` plugin).

**Not changed (already correct):**

- `libs.versions.toml` version pins — every entry (Kotlin 2.4.10,
  AGP 9.2.1, RunAnywhere 0.20.12, Room 2.7.0-alpha11, Compose BOM
  2025.05.00, etc.) resolves cleanly without changes. The pinned
  `kotlinxCoroutines = "1.10.0"` direct-dep version remains for
  the project's own `implementation(libs.kotlinx.coroutines.core)`
  lines; the conflict-resolver picks 1.11.0 from the RunAnywhere
  AAR for the runtime classpath.

---

*This file is the journal; `app/BUILD_GUIDE.md` is the spec; `app/CLAUDE.md`
is the operating manual for the next agent.*

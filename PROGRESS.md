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
  startup and falls back to `JvmStubInferenceEngine` if the `.so` is missing.
  Wired the `onnxruntime-mobile` dependency (`com.microsoft.onnxruntime:
  onnxruntime-mobile:1.18.0`) into `:core-inference`. APK now bundles
  `lib/arm64-v8a/libonnxruntime.so` (~3.7 MB), `libonnxruntime4j_jni.so`
  (~770 KB), plus armv7 and x86 variants. `InferenceCoordinator.pickEngine`
  tries llama.cpp first, then ORT, then falls back to the stub. Layer-shard
  loads are explicitly rejected with a typed `MeshlitError.Invalid` carrying
  `onnx.sharded.unsupported: ... Phase 3` so the coordinator can route the
  user to a Phase 3 build or a different runtime.
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
  `/v1/health` reports `engineTag=stub`; no `libmeshlit_inference.so` is bundled.
  The old `JvmStubInferenceEngine` intentionally echoed prompt tokens. It now
  emits a clearly-labelled non-echo demo reply and has a regression unit test.
  Real semantic answers still require the llama.cpp native bridge or a remote
  model endpoint.
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
- The shipped APK still uses `JvmStubInferenceEngine`; the bundled Qwen GGUF is
  extracted and registered, but it is not executed by a real model runtime.
  Phase 1 is not semantically complete until a llama.cpp JNI `.so` or a
  compatible prebuilt AAR is integrated.

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
`libmeshlit_inference.so` is absent, the coordinator selects
`JvmStubInferenceEngine` and the app remains testable. The next implementation
session should not rewrite this layer. It should implement the native C++ side,
then add model selection and HTTP/SSE dispatch around the existing coordinator.

Do not claim Phase 1 is complete until a physical device produces a real GGUF
response and the service survives 10+ minutes in the background. The sandbox
can verify builds, but only a human with a device can complete that acceptance
test.

---

*This file is the journal; `app/BUILD_GUIDE.md` is the spec; `app/CLAUDE.md`
is the operating manual for the next agent.*

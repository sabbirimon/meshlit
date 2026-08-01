# Meshlit — Project Progress Journal

A running log of decisions, state, and the current approach as the project
evolves. Updated alongside the build guide. Updated on each phase boundary
and on material decision changes, not every commit.

The authoritative source-of-truth is `app/BUILD_GUIDE.md`. This file is
the **journal**: what we actually decided, where we diverged, what we
skipped and why, what's next.

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

**Git:**
- Current branch: `phase/0.5-device-profile` (will branch to
  `phase/1-http-dispatch` for the task #7 series).
- Latest implementation commit: `446f76b`.
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

# Release checklist

> **Audience:** the person cutting a tagged release of Meshlit (the release captain). Each gate must pass before the tag is pushed. The list is intentionally narrow: it's the floor, not the ceiling. If a gate is uncomfortable to sign off, that's a signal the slice isn't done.

---

## 0. Branch + tag policy

- [ ] Branch is `dev` (or a release branch off `dev`); `main` is reserved for already-released tags.
- [ ] Working tree has **zero** untracked and **zero** modified-tracked files that aren't accounted for by the release commit (`git status` is clean or lists only the release commit's diff).
- [ ] The dirty-tree inventory doc for any open integration branch (e.g. `docs/architecture/dirty-tree-inventory-redesign-drawer-gemini.md`) has been re-read and every preserved file is still expected to ship in this tag.
- [ ] `git log dev --not <last-tag>` walks only the commits expected in this release.

---

## 1. Build gates

- [ ] `./gradlew :app:assembleDebug` is green on the release commit.
- [ ] `./gradlew :app:assembleRelease` is green (signed with the release keystore, not the debug one).
- [ ] `./gradlew :core-inference:testDebugUnitTest` is green (97 wire-contract tests).
- [ ] `./gradlew :core-common:testDebugUnitTest` is green (capability matrix, MeshlitError, CapabilityTier).
- [ ] `./gradlew :app:testDebugUnitTest` is green for the chosen flavor.
- [ ] `./gradlew :app:lintDebug` is green (no new warnings introduced).
- [ ] CI matrix (`.github/workflows/ci.yml`) is green on the PR that introduced the release commit.

---

## 2. Native engine gates

> **Current state (Slice 1):** the owned `libmeshlit_inference.so` does not ship. `LlamaCppInferenceEngine` is a stub with declared JNI symbols whose `loadNativeLibrary()` call fails. `pickEngine()` silently falls back to the RunAnywhere SDK. These gates therefore run against `engineTag == "runanywhere"` for now. Slice 2 replaces this section.

- [ ] **Physical-device llama.cpp inference** — the bundled model (`smollm2-360m-instruct-q8_0.gguf`, ~368 MB) loads and generates on at least one arm64-v8a device and one x86_64 device. Asserted via `/v1/health.engineTag == "llama.cpp"` (post-Slice-2) or `"runanywhere"` (pre-Slice-2).
- [ ] **Load / generate / unload cycle** — `LoadModel → Generate → UnloadModel → LoadModel` works cleanly across the three ABIs (arm64-v8a, armeabi-v7a, x86_64) without FGS restart.
- [ ] **Cancellation** — issuing `cancel()` mid-generation transitions `CoordinatorState` from `Generating` to `Ready` within 1 s; no token is delivered after the cancel ack.
- [ ] **No native-handle leak after stress** — `pmap` / `dumpsys meminfo` of the FGS process after 50 load/generate/unload cycles shows the same VmHWM as after 5 cycles (within ±5 MB).
- [ ] **Bundle vs catalog parity** — bundled SmolLM2 360M Q8 and a downloaded catalog model (Qwen 1.5B Q4_K_M) both produce non-empty token streams on the first generation.

---

## 3. Cluster federation gates

- [ ] **Two-device LAN smoke** — two phones discover each other via NSD within 15 s; the Devices screen renders both peers with a stable NodeId + trust tier badge.
- [ ] **Remote authenticated job** — peer B issues an authenticated `/v1/infer` against peer A (bearer token from the QR pairing flow); the response streams back as SSE and lands in `CoordinatorState` on peer B as if it were a local generation.
- [ ] **Offline behaviour** — with airplane mode toggled mid-generation, the client surfaces a typed `MeshlitError.Network` within ~15 s (not a hang, not an `IOException` wrapped in something opaque).
- [ ] **Protocol version field** — `/v1/health` includes `protocol_version: 1`; peer rejects (or warns) any response missing this field. (Slice 7.)

---

## 4. Benchmark gates

> Use `scripts/native_benchmark.py`. Append the JSON report to `docs/benchmarks/<tag>.json`.

- [ ] **SmolLM2 360M Q8** — `tokens_per_sec ≥ 8` on a Pixel 6-class device; `first_token_ms ≤ 2000`; `peak_mem_mb ≤ 1500`.
- [ ] **Qwen 1.5B Q4_K_M** — `tokens_per_sec ≥ 4`; `first_token_ms ≤ 4000`; `peak_mem_mb ≤ 2400`.
- [ ] **Cross-ABI parity** — the same two models produce within ±15 % tokens/sec on arm64-v8a vs x86_64 emulators.
- [ ] **No silent engine swap** — the benchmark's `engineTag` assertion (`EXPECTED_ENGINE_TAG`) is satisfied. If `engineTag == "llama.cpp"` ever appears, that's a milestone (Slice 2), not a regression.

---

## 5. UI + accessibility gates

- [ ] **14 bottom-bar destinations** still render and navigate; drawer-only entries (Help, Power, Cloud) still resolve.
- [ ] **Devices screen** renders a `CapabilityMatrixCard` for the local device + every peer (per-peer capability is wire-fetched via `/v1/health`).
- [ ] **Settings hub search** indexes every leaf-level setting; deep-link from search result to parent category works without a back-stack reset.
- [ ] **Live retheming** — flipping the accent in Settings → Theme re-themes the entire app within 200 ms; no "Apply" button; survives app kill.
- [ ] **Screenshot suite** — `adb shell screencap` of every bottom-bar destination + every drawer entry committed under `docs/screenshots/<tag>/`.
- [ ] **Accessibility smoke** — `adb shell uiautomator dump` confirms every interactive element has a `content-desc`; TalkBack walkthrough recorded for one full flow (Open App → Pair Peer → Load Model → Generate).

---

## 6. Observability gates

- [ ] **Tracing modes** — `Settings → Tracing` switches between `Off` / `Local` / `Otel`; switching takes effect within 200 ms; the log source badge updates in the header.
- [ ] **PCAP capture** — `Settings → Network → Capture VPN` toggles PCAP; a recorded capture round-trips through `PacketCaptureRegistry` and can be exported via the share sheet.
- [ ] **No PII in logs** — `LogSource` filter strips prompt content + response content at `Local` and `Otel` modes; only structured fields (engine tag, tokens, latency) are emitted.

---

## 7. Documentation gates

- [ ] `docs/architecture/current-state.md` is current. If anything in it is now wrong, fix the doc as part of the release commit — **don't** ship a release with stale documentation.
- [ ] `docs/release-checklist.md` (this file) is current. If a gate became stale, either remove it or document why it no longer applies.
- [ ] `README.md` Quickstart walks through `adb install` + first launch without requiring a private Discord/email.

---

## 8. Tag + post-release

- [ ] Tag created with annotated tag (`git tag -a vX.Y.Z -m "..."`); pushed (`git push origin vX.Y.Z`).
- [ ] Play Console / sideload artefact uploaded.
- [ ] Post-release monitoring: confirm `/v1/health` from the release APK shows `engineTag` matching the gate above; no `MeshlitError.Native("no_engine_for_format:...")` in production logs.

---

## What this checklist deliberately does NOT cover

These are tracked separately and do not gate a release:

- MoE expert shard federation (`BUILD_GUIDE.md` §2.7, Phase 4.5).
- Co-operative training loops (`BUILD_GUIDE.md` §7.9, Phase 5).
- Adaptive thermal/power tuning (`BUILD_GUIDE.md` Phase 5).
- Ghostty swap in `core-terminal` (TODO #179 — pending licence + NDK matrix check).
- Rust integration — explicitly dropped from v1 unless a later benchmark proves a need.
- The 5-area workstation UI redesign — treated as a proposal, not a release gate.

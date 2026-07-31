# Meshlit — Project Progress Journal

A running log of decisions, state, and the current approach as the project
evolves. Updated alongside the build guide. Updated on each phase boundary
and on material decision changes, not every commit.

The authoritative source-of-truth is `app/BUILD_GUIDE.md`. This file is
the **journal**: what we actually decided, where we diverged, what we
skipped and why, what's next.

---

## Current state — 2026-08-01

**Phase:** 0 → 1 boundary. Phase 0 shipped; beginning Phase 1.

**Phase 0 (DONE, this session):**
- 14-module Gradle restructure (`:app` + 13 `core-*` modules).
- build-logic composite build with convention plugins.
- Compose UI shell with bottom navigation across 9 destinations.
- core-common / core-trust types shipped (sealed result, error taxonomy,
  NodeId, ClusterRole, CapabilitySnapshot, JobSpec, MeshlitLogger,
  TrustTier, DeviceTrustPolicy).
- AndroidManifest declares foreground service, FGS-data-sync,
  near-by Wi-Fi, network, notification permissions.
- `./gradlew :app:assembleDebug` succeeds.
- `./gradlew :app:lintDebug` succeeds.
- APK at `app/build/outputs/apk/debug/app-debug.apk` (62 MB).

**Build guide v1.2 (DONE, this session):**
- §0 principle 1 updated: data-parallel preserved, but MoE-shard
  federation explicitly allowed.
- §2.6 added — frontend/backend language choices.
- §2.7 added — frontier model federation architecture and flow.
- Phase 4.5 added — public-side gateway, OpenAI-compatible HTTP API,
  bearer tokens, federated cluster.

**Git (DONE, this session):**
- Three commits on `phase/0-scaffolding`: planning snapshot, Phase 0
  scaffold, build-guide expansion.
**Brand identity (DONE, this session):**
- Name: **Meshlit** ("Many phones. One mind.")
- Palette: midnight `#0A0E1A` / violet `#7C5CFF` / cyan `#22D3EE` / emerald `#34D399`
- Adaptive launcher icon designed and generated across all density buckets:
  central violet Brain node + 3 satellites (cyan tool nodes, emerald
  monitor node) + light beams on a hex-cell cluster motif. Includes the
  Android 13+ monochrome layer.
- `Theme.Meshlit` applied; `strings.xml` populated with screen labels.

**Build guide (DONE, this session):**
- v1.1 expansion committed to `app/BUILD_GUIDE.md`:
  - New §0 principles 9 & 10: user-driven choices resolve conflicting scope
    (SSH, bypass-charging, long-distance transport, adaptive vs static).
  - Phases 3–5 reorganized to slot in new features.
  - New module layout (`:core-users`, `:core-files`, `:core-firewall`,
    `:core-guardrails`, `:core-tunnel`, `:core-ssh`, `:core-training`).
  - New §7 Feature-area playbooks (10 sections) and §8 honest limits.
- `app/CLAUDE.md` updated with the new rules in prose.

**Git (DONE, this session):**
- `git init`, `.gitignore` expanded for Gradle/IDE/Puku-CLI/native/gen.
- This is the baseline commit.

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

1. **Phase 0: Multi-module Gradle scaffolding.** Restructure from a single
   `:app` module to the layout in BUILD_GUIDE §4. Update `settings.gradle.kts`,
   the top-level `build.gradle.kts`, `libs.versions.toml`, and create
   `build.gradle.kts` per module. Keep `minSdk = 29`, `targetSdk = 36`.
2. **Phase 0: Compose UI shell.** Bottom navigation across Devices / Jobs /
   Models / Files / Sessions / Cluster / Network / Users / Settings. Each
   tab is a stub screen with its empty-state placeholder.
3. **Phase 0: Physical-device verification.** `gradlew assembleDebug`,
   install on a real Android 14+ device, confirm the empty Devices
   screen renders and the brand assets appear.
4. **Phase 1: Hardest risk first — NDK + llama.cpp + foreground service.**
   Stays inside Phase 1 / not starting until Phase 0 ships.

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

## Honest session-boundary note (added 2026-08-01)

Autopilot for the full project is **not** a single session. The
remaining work spans at least 6 more phase-cycles. Each requires
on-device verification that I cannot perform in a sandboxed CLI
environment — only the human can plug in a phone, run `adb install`,
and confirm a 15-minute background survival test.

**Right now, the next agent should:**
1. Read this PROGRESS.md.
2. Read the `phase-0-done` tag state (git checkout phase-0-done after
   it's tagged below).
3. Begin Phase 1: NDK + llama.cpp + foreground service. The hardest
   risk. The first prompt must round-trip Device-A → Device-B. Plan
   on at least one full session for this phase.
4. Tag `phase-1-done` only when the on-device test passes.

**To tag Phase 0 done:**
```
git tag -a phase-0-done -m "Multi-module scaffold + Compose UI shell. APK builds, lint clean."
```

---

*This file is the journal; `app/BUILD_GUIDE.md` is the spec; `app/CLAUDE.md`
is the operating manual for the next agent.*

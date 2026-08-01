# CLAUDE.md

Operating instructions for Claude Code (or any autonomous/"full autopilot"
session) working in this repository. Read this file first, every session.

## What this project is

An Android app that discovers other Android devices (wired or wireless),
assigns each one a cluster role, and runs local LLM inference, MCP tool
servers, and agents across them. Full context and the phased build plan live
in `BUILD_GUIDE.md` — read it before starting work if this is a fresh
session or you're unsure what phase we're in.

## Current phase

> Update this line at the end of every session so the next session (or
> agent) doesn't have to reconstruct state from git log.

`PHASE: 1 — Kotlin engine/service/UI foundation complete; awaiting llama.cpp JNI integration and physical-device verification before phase-1-done`

## How this project handles conflicting scope choices

When two requirements look like they conflict (e.g. "users want SSH" vs
"no public SSH"; "users want WAN clustering" vs "no multi-hop mesh";
"users want adaptive scheduling" vs "ship static thresholds first"),
the resolution is **user choice surfaced in the UI, not silent
relaxation of the constraint**.

The app offers both options with honest trade-offs and consent flows.
The constraint itself remains in force — it just stops being the user's
problem. See `BUILD_GUIDE.md` §0 principles 9 and 10 for the worked
examples (SSH, bypass-charging, long-distance connectivity, adaptive
tuning).

## Non-negotiable constraints

Do not do any of the following, even if it looks like the shortest path to
completing a task:

- **Do not implement tensor/pipeline-parallel model splitting across
  devices.** Every job runs a complete model on one node. If a task seems to
  require splitting a model's layers across the network, stop and flag it —
  it likely means the task should be reframed as job distribution instead.
- **Do not hard-lock device roles.** Role assignment is advisory; the UI can
  warn, never block outright.
- **Do not add a job-dispatch path without a timeout and retry.** Assume
  every node can vanish mid-job.
- **Do not expose SSH, MCP, or any control port directly to the public
  internet.** WAN traffic goes through the relay in Phase 4, with per-device
  TLS + signed tokens. Local trust tiers are defined in
  `skills/cluster-trust-security/SKILL.md` — check it before writing any
  auth code.
- **Do not claim or implement programmatic bypass-charging control.** No
  public AOSP API exists for it. Detect charging state and recommend the
  user enable it manually where their device supports it; nothing more.
- **Do not silently provision devices over ADB.** It requires a manual
  on-device authorization prompt every time — build the UI flow around that
  reality, don't try to script past it.
- **Do not build multi-hop mesh relay routing.** Single-hop discovery only
  (NSD/mDNS, Wi-Fi Direct, Wi-Fi Aware) in this version.
- **Do not build the adaptive/self-tuning scheduler (Phase 5) before Phase 2's
  benchmark-on-join logging exists and has real data behind it.** Static
  thresholds first, always.
- **Do not expose SSH, MCP, or the firewall to the public internet.** WAN
  traffic reaches those endpoints through Tailscale/WireGuard or the
  relay, with per-device TLS + signed tokens. Cluster-internal SSH
  (Phase 5) is a real feature bound to LAN/Tailscale, NOT a workaround
  for this rule.

## Before writing code in an unfamiliar area, check `skills/`

This project ships domain-specific skill files. If a task touches one of
these areas, read the matching skill file **before** writing code — they
encode gotchas (Android 15 foreground-service timeouts, LMK/RAM behavior,
Wi-Fi Aware feature-gating, MCP transport patterns) that aren't obvious from
general Android or LLM knowledge:

- `skills/android-foreground-services/SKILL.md` — any background service,
  wakelock, or long-running task work
- `skills/llama-cpp-android/SKILL.md` — anything touching the NDK inference
  engine, model loading, or quantization
- `skills/mesh-networking-android/SKILL.md` — discovery, Wi-Fi Direct/Aware,
  transport fallback logic
- `skills/mcp-server-android/SKILL.md` — building or modifying any
  in-app MCP tool server
- `skills/cluster-trust-security/SKILL.md` — anything involving auth,
  tokens, trust tiers, or the WAN relay

## Coding conventions

- Kotlin, idiomatic coroutines for async work — no raw threads unless a
  skill file specifically calls for it (native JNI callbacks sometimes do).
- Keep `core-inference` and `core-mcp` behind interfaces consumed by
  `core-orchestration` — don't let the router import concrete engine
  classes directly. See module layout in `BUILD_GUIDE.md` §4.
- Every new background code path gets a log line on start, stop, and
  failure. This project's Phase 5 adaptive scheduler depends on historical
  logs existing from day one — don't wait to add instrumentation later.
- Physical-device testing is required for anything touching foreground
  services, battery, or thermal state. Note in the PR/commit message which
  physical device(s) it was verified on; an emulator pass alone is not
  sufficient for those areas.

## Definition of done for a phase task

A task is done when:
1. It builds and runs on a physical device.
2. It survives the app being backgrounded (test: 15+ min).
3. Killing/disconnecting a node mid-operation is handled (no silent hang,
   no infinite queue).
4. The relevant checklist item in `BUILD_GUIDE.md` §5 is satisfied.
5. `CLAUDE.md`'s `PHASE:` line is updated if this completes a phase.

## When something in this project's real Android environment contradicts a skill file

Skills capture research current as of when they were written. Android
platform behavior (background execution limits especially) changes across
OS versions and OEM builds. If observed device behavior contradicts a skill
file, trust the observed behavior, note the discrepancy at the top of the
relevant skill file, and continue — don't silently work around it without
recording why.

---
name: llama-cpp-android
description: How to integrate llama.cpp via the Android NDK for on-device GGUF model inference — build setup, JNI bindings, quantization/RAM-fit selection, hardware acceleration (NNAPI/GPU/vendor NPU delegates), and safe model loading that won't get killed by the OS. Use this for any task involving loading a model, running inference, picking a quantization level, managing GGUF files, or wiring the native inference engine into the orchestration core — this is not "just call an API," it's native code with real device-fit constraints.
---

# llama.cpp on Android (NDK)

The inference engine for every "Brain" role node in the cluster. This is
native C++ code cross-compiled for Android, not a managed-code library —
budget real NDK/build-system time for this, especially the first
integration.

## Build setup

- Compile llama.cpp as a shared library via the NDK, wrapped with a thin JNI
  layer exposing: load model, run inference (streaming token callback),
  unload model, get memory/context stats.
- Build per-ABI (`arm64-v8a` covers essentially all target devices in a
  modern phone cluster; skip `armeabi-v7a`/x86 unless a specific device in
  the pool needs it — check `Build.SUPPORTED_ABIS` per node before deciding
  whether to ship it).
- Keep the JNI surface small and stable — token streaming should cross the
  JNI boundary via a callback per token or small batch, not by buffering an
  entire response in native memory before returning.

## Model file management

- GGUF files are large (multi-GB even quantized) — do not bundle a model in
  the APK. Download on first use into app-scoped storage
  (`getExternalFilesDir` or scoped storage equivalent), not shared/public
  storage.
- Implement the **shared model cache** from the build guide (Phase 3): once
  one node has a GGUF, other LAN nodes should be able to fetch it from that
  node over local HTTP instead of each re-downloading from the internet.
  This matters even more for any WAN/cellular node (Phase 4) — never let a
  cellular node silently pull a multi-GB model without an explicit,
  metered-connection-aware confirmation.
- Verify file integrity (checksum) after any transfer, local or remote,
  before attempting to load — a truncated GGUF can crash the native loader
  in ways that are hard to distinguish from an OOM kill in your logs.

## Quantization / RAM-fit selection

There's no single safe global rule ("never exceed 75% of RAM") — LMK
behavior varies by device (see `android-foreground-services/SKILL.md`).
Instead, pick quantization per node based on actual measured headroom:

- Query `ActivityManager.getMemoryInfo()` for available RAM at load time,
  not at app start (background state changes).
- Maintain a small lookup of model size → quant variant (e.g., Q4_K_M vs Q8
  vs Q2_K) and pick the largest quant that fits comfortably under the node's
  current headroom, with margin for the OS and other apps.
- Feed the **benchmark-on-join** result (tokens/sec, thermal delta, and
  whether the load succeeded at all) back into the node's capability record
  — this is what lets Phase 5's scheduler stop guessing and start knowing.

## Hardware acceleration

- Check for Vulkan support and vendor NPU/NNAPI delegate availability per
  device before defaulting to pure-CPU inference — the device pool in a
  real cluster (older Snapdragons, MediaTek chips, etc.) varies widely in
  what's actually available, and llama.cpp's backend selection needs to be
  probed at runtime, not assumed from the SoC name alone.
- Vendor-specific NPU SDKs (e.g., Qualcomm's) are their own integration
  project — treat "NPU acceleration" as a stretch goal per node type, not a
  Phase 1–2 requirement. CPU-only inference should be the reliable fallback
  path everywhere.

## Streaming and cancellation

- Support mid-generation cancellation cleanly (the router may need to abort
  a job if the requesting node disconnects, or if a higher-priority job
  needs the node). Make sure cancellation actually frees the native context
  — a leaked llama.cpp context on a phone-class device will exhaust RAM
  quickly across repeated jobs.
- Stream tokens back over the local HTTP/SSE server (see
  `mcp-server-android/SKILL.md` for the transport pattern) rather than
  buffering a full response — this keeps time-to-first-token reasonable and
  lets the UI show progress on constrained hardware.

## What NOT to build here

- No cross-device tensor/pipeline parallelism — see the non-negotiable
  constraint in `CLAUDE.md`. Every node's llama.cpp instance runs a
  complete, independent model.
- No speculative-decoding draft/target pairing across two different phones
  for v1 — it's a legitimate technique but adds real-time cross-device
  coordination that isn't worth the complexity until the basic data-parallel
  cluster is solid.

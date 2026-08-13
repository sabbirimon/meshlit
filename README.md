<!-- SEO: meta description for GitHub social previews and crawlers -->
<!-- keywords: on-device AI, federated inference, Android LLM, llama.cpp, ONNX, MCP, RunAnywhere, MoE, Mixture of Experts, edge AI, private AI, no telemetry, local LLM, speech-to-text, TTS, vision language model, federated learning -->

<!-- ANIMATED HERO BANNER -->
<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/assets/meshlit-hero.svg">
    <img alt="Meshlit — Many phones. One mind." src="docs/assets/meshlit-hero.svg">
  </picture>
</p>

<!-- HERO SOURCE (kept as inline fallback so even first-time visitors before the file is committed see the art) -->
<p align="center">
  <a href="sabbirimon/meshlit">
    <img alt="Meshlit — animated federated inference hero banner (light + dark)" src="data:image/svg+xml;utf8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20viewBox%3D%220%200%201280%20320%22%20role%3D%22img%22%20aria-label%3D%22Meshlit%20%E2%80%94%20Many%20phones.%20One%20mind.%22%3E%0A%20%20%3Cdefs%3E%0A%20%20%20%20%3ClinearGradient%20id%3D%22bg%22%20x1%3D%220%22%20y1%3D%220%22%20x2%3D%221%22%20y2%3D%221%22%3E%0A%20%20%20%20%20%20%3Cstop%20offset%3D%220%25%22%20stop-color%3D%22%230b1020%22%2F%3E%0A%20%20%20%20%20%20%3Cstop%20offset%3D%22100%25%22%20stop-color%3D%22%231a1140%22%2F%3E%0A%20%20%20%20%3C%2FlinearGradient%3E%0A%20%20%20%20%3ClinearGradient%20id%3D%22mesh%22%20x1%3D%220%25%22%20y1%3D%220%25%22%20x2%3D%22100%25%22%20y2%3D%220%25%22%3E%0A%20%20%20%20%20%20%3Cstop%20offset%3D%220%25%22%20stop-color%3D%22%237c3aed%22%3E%0A%20%20%20%20%20%20%3Canimate%20attributeName%3D%22offset%22%20values%3D%22-1%3B1%22%20dur%3D%226s%22%20repeatCount%3D%22indefinite%22%2F%3E%0A%20%20%20%20%20%20%3C%2Fstop%3E%0A%20%20%20%20%20%20%3Cstop%20offset%3D%2250%25%22%20stop-color%3D%22%2306b6d4%22%3E%0A%20%20%20%20%20%20%3Canimate%20attributeName%3D%22offset%22%20values%3D%22-0.5%3B1.5%22%20dur%3D%226s%22%20repeatCount%3D%22indefinite%22%2F%3E%0A%20%20%20%20%20%20%3C%2Fstop%3E%0A%20%20%20%20%20%20%3Cstop%20offset%3D%22100%25%22%20stop-color%3D%22%23f472b6%22%3E%0A%20%20%20%20%20%20%3Canimate%20attributeName%3D%22offset%22%20values%3D%220%3B2%22%20dur%3D%226s%22%20repeatCount%3D%22indefinite%22%2F%3E%0A%20%20%20%20%20%20%3C%2Fstop%3E%0A%20%20%20%20%3C%2FlinearGradient%3E%0A%20%20%20%20%3CradialGradient%20id%3D%22core%22%20cx%3D%2250%25%22%20cy%3D%2250%25%22%20r%3D%2250%25%22%3E%0A%20%20%20%20%20%20%3Cstop%20offset%3D%220%25%22%20stop-color%3D%22%23ffffff%22%20stop-opacity%3D%221%22%2F%3E%0A%20%20%20%20%20%20%3Cstop%20offset%3D%2240%25%22%20stop-color%3D%22%23a78bfa%22%20stop-opacity%3D%220.9%22%2F%3E%0A%20%20%20%20%20%20%3Cstop%20offset%3D%22100%25%22%20stop-color%3D%22%237c3aed%22%20stop-opacity%3D%220%22%2F%3E%0A%20%20%20%20%3C%2FradialGradient%3E%0A%20%20%20%20%3Cfilter%20id%3D%22glow%22%20x%3D%22-50%25%22%20y%3D%22-50%25%22%20width%3D%22200%25%22%20height%3D%22200%25%22%3E%0A%20%20%20%20%20%20%3CfeGaussianBlur%20stdDeviation%3D%226%22%20result%3D%22b%22%2F%3E%0A%20%20%20%20%20%20%3CfeMerge%3E%3CfeMergeNode%20in%3D%22b%22%2F%3E%3CfeMergeNode%20in%3D%22SourceGraphic%22%2F%3E%3C%2FfeMerge%3E%0A%20%20%20%20%3C%2Ffilter%3E%0A%20%20%3C%2Fdefs%3E%0A%20%20%3Crect%20width%3D%221280%22%20height%3D%22320%22%20fill%3D%22url(%23bg)%22%2F%3E%0A%20%20%3Cg%20stroke%3D%22%23a78bfa%22%20stroke-opacity%3D%220.35%22%20stroke-width%3D%221.4%22%20fill%3D%22none%22%3E%0A%20%20%20%20%3Cpath%20d%3D%22M640%2C170%20L420%2C90%22%20stroke-dasharray%3D%226%208%22%3E%3Canimate%20attributeName%3D%22stroke-dashoffset%22%20from%3D%220%22%20to%3D%22-28%22%20dur%3D%221.6s%22%20repeatCount%3D%22indefinite%22%2F%3E%3C%2Fpath%3E%0A%20%20%20%20%3Cpath%20d%3D%22M640%2C170%20L860%2C90%22%20stroke-dasharray%3D%226%208%22%3E%3Canimate%20attributeName%3D%22stroke-dashoffset%22%20from%3D%220%22%20to%3D%22-28%22%20dur%3D%221.8s%22%20repeatCount%3D%22indefinite%22%2F%3E%3C%2Fpath%3E%0A%20%20%20%20%3Cpath%20d%3D%22M640%2C170%20L380%2C210%22%20stroke-dasharray%3D%226%208%22%3E%3Canimate%20attributeName%3D%22stroke-dashoffset%22%20from%3D%220%22%20to%3D%22-28%22%20dur%3D%221.5s%22%20repeatCount%3D%22indefinite%22%2F%3E%3C%2Fpath%3E%0A%20%20%20%20%3Cpath%20d%3D%22M640%2C170%20L900%2C210%22%20stroke-dasharray%3D%226%208%22%3E%3Canimate%20attributeName%3D%22stroke-dashoffset%22%20from%3D%220%22%20to%3D%22-28%22%20dur%3D%222s%22%20repeatCount%3D%22indefinite%22%2F%3E%3C%2Fpath%3E%0A%20%20%20%20%3Cpath%20d%3D%22M640%2C170%20L640%2C280%22%20stroke-dasharray%3D%226%208%22%3E%3Canimate%20attributeName%3D%22stroke-dashoffset%22%20from%3D%220%22%20to%3D%22-28%22%20dur%3D%221.4s%22%20repeatCount%3D%22indefinite%22%2F%3E%3C%2Fpath%3E%0A%20%20%3C%2Fg%3E%0A%20%20%3Cg%20filter%3D%22url(%23glow)%22%3E%0A%20%20%20%20%3Ccircle%20cx%3D%22640%22%20cy%3D%22170%22%20r%3D%2270%22%20fill%3D%22url(%23core)%22%2F%3E%0A%20%20%3C%2Fg%3E%0A%20%20%3Cg%3E%0A%20%20%20%20%3Ccircle%20cx%3D%22420%22%20cy%3D%2290%22%20r%3D%2218%22%20fill%3D%22%237c3aed%22%3E%3Canimate%20attributeName%3D%22r%22%20values%3D%2218%3B26%3B18%22%20dur%3D%222.4s%22%20repeatCount%3D%22indefinite%22%2F%3E%3C%2Fcircle%3E%0A%20%20%20%20%3Ccircle%20cx%3D%22860%22%20cy%3D%2290%22%20r%3D%2218%22%20fill%3D%22%2306b6d4%22%3E%3Canimate%20attributeName%3D%22r%22%20values%3D%2218%3B26%3B18%22%20dur%3D%222.8s%22%20repeatCount%3D%22indefinite%22%2F%3E%3C%2Fcircle%3E%0A%20%20%20%20%3Ccircle%20cx%3D%22380%22%20cy%3D%22210%22%20r%3D%2218%22%20fill%3D%22%2322d3ee%22%3E%3Canimate%20attributeName%3D%22r%22%20values%3D%2218%3B26%3B18%22%20dur%3D%223.2s%22%20repeatCount%3D%22indefinite%22%2F%3E%3C%2Fcircle%3E%0A%20%20%20%20%3Ccircle%20cx%3D%22900%22%20cy%3D%22210%22%20r%3D%2218%22%20fill%3D%22%23f472b6%22%3E%3Canimate%20attributeName%3D%22r%22%20values%3D%2218%3B26%3B18%22%20dur%3D%222.6s%22%20repeatCount%3D%22indefinite%22%2F%3E%3C%2Fcircle%3E%0A%20%20%20%20%3Ccircle%20cx%3D%22640%22%20cy%3D%22280%22%20r%3D%2218%22%20fill%3D%22%23facc15%22%3E%3Canimate%20attributeName%3D%22r%22%20values%3D%2218%3B26%3B18%22%20dur%3D%223s%22%20repeatCount%3D%22indefinite%22%2F%3E%3C%2Fcircle%3E%0A%20%20%3C%2Fg%3E%0A%20%20%3Ctext%20x%3D%22640%22%20y%3D%22182%22%20text-anchor%3D%22middle%22%20font-family%3D%22'Segoe%20UI'%2CInter%2Csans-serif%22%20font-size%3D%2216%22%20font-weight%3D%22700%22%20fill%3D%22%23ffffff%22%3EMeshlit%3C%2Ftext%3E%0A%20%20%3Ctext%20x%3D%22640%22%20y%3D%2256%22%20text-anchor%3D%22middle%22%20font-family%3D%22'Segoe%20UI'%2CInter%2Csans-serif%22%20font-size%3D%2244%22%20font-weight%3D%22800%22%20letter-spacing%3D%222%22%20fill%3D%22url(%23mesh)%22%3EMESHLIT%3Canimate%20attributeName%3D%22letter-spacing%22%20values%3D%222%3B4%3B2%22%20dur%3D%224s%22%20repeatCount%3D%22indefinite%22%2F%3E%3C%2Ftext%3E%0A%20%20%3Ctext%20x%3D%22640%22%20y%3D%22300%22%20text-anchor%3D%22middle%22%20font-family%3D%22'Segoe%20UI'%2CInter%2Csans-serif%22%20font-size%3D%2220%22%20font-weight%3D%22500%22%20fill%3D%22%23e5e7eb%22%3EMany%20phones.%20%C2%B7%20One%20mind.%3C%2Ftext%3E%0A%3C%2Fsvg%3E" width="100%" style="max-width:860px;">
  </a>
</p>

# Meshlit — Many phones. One mind.

> ## 🚧 **THIS IS THE `dev` BRANCH — ACTIVE DEVELOPMENT IN PROGRESS**
>
> **You are reading the latest integration branch.** Things here change daily, may break, and may not be tied to a release yet. The interface, screens, and SDK wiring are evolving as Phase 2.x → Phase 3 work lands. For the frozen snapshot, see the [`main`](https://github.com/sabbirimon/meshlit/tree/main) branch instead.
>
> **Status:** 🟡 **Active development** · Phase 2.x shipped · Phase 3 (cluster-shard federation, MoE routing) in progress · latest tag: none yet.
>
> Quick links: [📦 main (frozen)](https://github.com/sabbirimon/meshlit/tree/main) · [🌿 dev (this branch)](https://github.com/sabbirimon/meshlit/tree/dev) · [📓 PROGRESS.md](PROGRESS.md) · [🛠 TODO.md](TODO.md) · [📋 Issues](https://github.com/sabbirimon/meshlit/issues) · [💬 Discussions](https://github.com/sabbirimon/meshlit/discussions)

> **On-device AI runtime + federated inference cluster for Android.**
> Turn a fleet of Android phones into a private LLM cluster — no cloud,
> no telemetry, full SDK-backed voice / vision / structured / catalog surfaces.

<p align="left">
  <a href="https://github.com/sabbirimon/meshlit/blob/dev/LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=for-the-badge&logo=apache" alt="License: Apache 2.0"></a>
  <a href="https://github.com/sabbirimon/meshlit/stargazers"><img src="https://img.shields.io/github/stars/sabbirimon/meshlit?style=for-the-badge&logo=github" alt="GitHub stars"></a>
  <a href="https://github.com/sabbirimon/meshlit/network/members"><img src="https://img.shields.io/github/forks/sabbirimon/meshlit?style=for-the-badge&logo=github" alt="GitHub forks"></a>
  <a href="https://github.com/sabbirimon/meshlit/issues"><img src="https://img.shields.io/github/issues/sabbirimon/meshlit?style=for-the-badge&logo=github" alt="Open issues"></a>
  <a href="https://github.com/sabbirimon/meshlit/commits/dev"><img src="https://img.shields.io/github/last-commit/sabbirimon/meshlit/dev?style=for-the-badge&logo=github" alt="Last commit"></a>
  <a href="https://github.com/sabbirimon/meshlit"><img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 7.0+"></a>
  <a href="https://github.com/sabbirimon/meshlit"><img src="https://img.shields.io/badge/Kotlin-2.1-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin 2.1"></a>
  <a href="https://github.com/sabbirimon/meshlit"><img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"></a>
  <a href="https://github.com/sabbirimon/meshlit/tree/dev"><img src="https://img.shields.io/badge/branch-dev-orange?style=for-the-badge&logo=git" alt="Branch: dev"></a>
</p>

**Meshlit** is an open-source **Android app** that orchestrates **local LLMs, speech-to-text, text-to-speech, vision (VLM), structured output, and tool-calling** across a private fleet of phones using **federated inference**. It runs entirely **on-device by default** — no cloud, no telemetry — with optional LAN, Wi-Fi Direct, Tailscale, VPN, and relay transports for cluster federation. Built on the [RunAnywhere SDK](https://github.com/RunanywhereAI/runanywhere-sdks) 0.20.12 (llama.cpp + sherpa-onnx + ONNX Runtime Mobile).

> 🔎 **Looking for keywords?** On-device AI · Federated inference · Android LLM · llama.cpp · GGUF · ONNX Runtime Mobile · sherpa-onnx · Mixture-of-Experts (MoE) sharding · MCP tool server · Private AI · No telemetry · Local LLM · Speech-to-text · TTS · Vision Language Model · Edge AI · Apache 2.0 · Jetpack Compose · Material 3 · VpnService packet capture · RunAnywhere SDK

## Why Meshlit?

| Problem                                                                 | Meshlit's answer                                                                                       |
|-------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| Cloud LLM APIs leak prompts and harvest telemetry.                      | **No telemetry by default.** Every byte off the device is an explicit, visible user choice.            |
| A single phone can't run frontier models.                               | **Federated inference cluster** — data-parallel jobs across phones, MoE sharding for frontier models.   |
| On-device tool-calling SDKs lock you into one runtime.                  | **Multi-runtime abstraction** — llama.cpp · ONNX Runtime Mobile · any OpenAI-compatible remote endpoint.|
| Hard to inspect what your AI app is actually doing on the network.      | **Built-in network monitor + opt-in `VpnService` PCAP capture** — Wireshark-ready libpcap output.       |
| Voice / vision / structured output needs three different apps.          | **Four Compose screens, one app** — Voice (STT+TTS+VAD), JSON (structured+tools), Catalog, Vision (VLM).|

---

## ✨ Highlights

- 🚀 **Multi-runtime inference.** GGUF (llama.cpp), ONNX (Microsoft ORT Mobile), or any OpenAI-compatible remote endpoint. Models bundled, downloaded on demand, or SAF-imported.
- 🎙️ **Full SDK surface.** Voice (STT + TTS + VAD), structured output + tool calling, dynamic model catalog, vision (VLM + image picker) — all on-device.
- 🔗 **Federation.** Data-parallel jobs across phones. Optional **Mixture-of-Experts shard federation** — each node holds a subset of experts, the router dispatches tokens.
- 🌐 **Embedded HTTP/SSE server.** Capability-aware mini-router, `X-Meshlit-Hints` header, SSE streaming, 30 s peer-health cache, forward-and-stream proxy on `:8080`.
- ⚙️ **Foreground service for inference.** Android `dataSync` FGS with persistent notification, LocalBinder IPC, Android 15+ `onTimeout()` handling.
- 🧰 **MCP tool server.** Tools registered with the LLM drive Meshlit itself: file ops, device control, peer queries.
- 🛡️ **Trust tiers.** LAN / temporary-local / WAN each carry a different auth burden. The Devices screen lets the user lock scopes precisely.
- 📜 **Self-describing runtime.** Opt-in OpenTelemetry tracing (Off / Local / Otel), in-app user manual with intent / use case / configuration / troubleshooting per feature, UI tour overlay fired on first visit, and GitHub-Issues-backed feedback that auto-attaches the last 200 log lines. Network monitor surfaces Meshlit's own HTTP calls plus an opt-in `VpnService`-backed packet capture that writes libpcap-format files for inspection in Wireshark / `tshark` / Termux / PCAPdroid.
- 🔕 **No telemetry by default.** Every byte leaving the device — including OpenTelemetry exports — is the user's explicit, visible choice. The Tracing toggle in Settings defaults to Off.

---

## Table of contents

- [Why Meshlit?](#why-meshlit)
- [Highlights](#-highlights)
- [Status](#status)
- [Architecture](#architecture)
- [Project layout](#project-layout)
- [Build](#build)
- [Branching strategy](#branching-strategy)
- [Building from source](#building-from-source)
- [Running](#running)
- [The four SDK-backed screens](#the-four-sdk-backed-screens)
- [Cluster model](#cluster-model)
- [Security model](#security-model)
- [FAQ — Frequently asked questions](#faq--frequently-asked-questions)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [Phase ledger](#phase-ledger)
- [License](#license)
- [Acknowledgments](#acknowledgments)

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
│     ├─ observability/    # LogBuffer + LogExporter + TracingController wiring
│     ├─ network/          # TermuxBridge + PcapdroidBridge
│     ├─ quickactions/     # SyncViewModel + BoostViewModel
│     ├─ ui/screens/help/  # HelpHubScreen + UserManualScreen +
│     │                    #   UiTourScreen + FeedbackScreen
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
├─ core-observability/     # TracingController + OtelBootstrap + SinkSpanProcessor
│                          #   + TracerHolder + LogSource
├─ core-net/               # NetworkObserver (OkHttp EventListener) +
│                          #   MeshlitCaptureVpnService + PcapWriter +
│                          #   PcapParser + PacketParser + PacketCaptureRegistry
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

1. The app extracts the bundled `smollm2-360m-instruct-q8_0.gguf`
   from `assets/models/` into `filesDir/bundled-models/`
   (~368 MB; runs in the background on `appScope`). The asset
   basename matches the SDK's `DEFAULT_MODEL_ID` so the FGS
   auto-loads it without a rename step. To swap in a larger
   model, use **Models → Catalog** or the Models screen's import
   flow. See `app/src/main/assets/models/README.md` for the
   restore-from-HuggingFace instructions.
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
`RunAnywhereCatalog.all` (SmolLM2-360M — bundled — plus
Qwen2.5-1.5B, Llama-3.2-1B, Phi-3-mini and the MoE rows
Qwen3-30B-A3B, Granite-4.0-Tiny-MoE, Mixtral-8x7B) when offline.

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

## FAQ — Frequently asked questions

### What is Meshlit?

Meshlit is an **open-source Android app** that turns a fleet of phones into a **federated inference cluster** for **large language models (LLMs)**, speech, and vision. Every model runs **on-device** — there is no cloud round-trip by default.

### How is Meshlit different from llama.cpp, Ollama, or LM Studio?

Those projects run on a desktop/server. Meshlit is the **phone-native** counterpart: it ships an `InferenceForegroundService`, an embedded NanoHTTPD inference server, a **RunAnywhere SDK** wrapper for voice/vision/catalog, and a **federation protocol** that turns multiple phones into one cluster.

### Does Meshlit send my data anywhere?

**No, by default.** All inference is local. Network transports (LAN / Wi-Fi Direct / Tailscale / WireGuard / relay) are **opt-in**, and every flow that uses mobile data surfaces a user prompt. OpenTelemetry exports are also off by default.

### Which Android versions are supported?

API 24+ (Android 7.0 Nougat and up). The RunAnywhere SDK 0.20.12 requires `libllama.so` symbols from API 24.

### Which models can I run?

- **Bundled**: `smollm2-360m-instruct-q8_0.gguf` (~368 MB).
- **Catalog**: Qwen2.5-1.5B, Llama-3.2-1B, Phi-3-mini, Qwen3-30B-A3B (MoE), Granite-4.0-Tiny-MoE, Mixtral-8x7B.
- **Import**: any GGUF via SAF, plus any OpenAI-compatible remote endpoint (Ktor 3).

### What is federated inference?

You run one job across **multiple phones**. Each phone holds a complete model slice (data-parallel, no tensor-parallel). For Mixture-of-Experts models, each node holds a **subset of experts** and the router dispatches tokens by `expert_id`. See [Cluster model](#cluster-model).

### Is there a web UI?

Each node exposes a tiny HTTP/SSE surface on `:8080` for peers:
- `GET  /v1/health`
- `GET  /v1/runtimes`
- `GET  /v1/model`
- `POST /v1/infer` (SSE)

### What's the difference between `main` and `dev`?

- **`main`** is frozen until the next tagged release (1.0). It's the safe surface.
- **`dev`** is the integration branch. It receives `feat/*`, `fix/*`, and `chore/*` PRs and may break between merges. See [Branching strategy](#branching-strategy).

### How do I report a bug or request a feature?

Use [GitHub Issues](https://github.com/sabbirimon/meshlit/issues). The in-app **Feedback** screen (Settings → Feedback) auto-attaches the last 200 log lines.

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

## Branching strategy

`main` is frozen until the next tagged release. Day-to-day
integration happens on **`dev`**, and every non-trivial change
lands on a short-lived feature branch off `dev`. The repo today
looks like:

```
main              ← frozen (next: 1.0 release)
└─ dev            ← integration branch — push here
   ├─ feat/<name> ← one feature / fix per branch
   ├─ fix/<name>
   └─ chore/<name>
```

Workflow:

```bash
git checkout dev
git pull --rebase origin dev
git checkout -b feat/<short-name>   # or fix/, chore/
# … work, conventional-commit messages …
git push -u origin feat/<short-name>
# open a PR into dev — squash-merge once CI is green
```

For one-off hotfixes, branch straight from `main`, but don't
merge back until `dev` is ready — `main` is intentionally a
release tag, not a moving target.

## Building from source

The full per-phase walkthrough lives in
[app/BUILD_GUIDE.md](app/BUILD_GUIDE.md). The 30-second version:

```bash
git clone https://github.com/sabbirimon/meshlit
cd meshlit
./gradlew :app:assembleDebug                      # build the APK
./gradlew :app:installDebug                       # install on a connected device
./gradlew :app:testDebugUnitTest                  # run app-side unit tests
./gradlew :core-inference:testDebugUnitTest       # run inference engine tests
```

Requirements:

- Android Studio Ladybug (AGP 9.2.1)
- JDK 21
- Android SDK 36, build-tools 36
- A device or emulator on **API 24+** (Android 7.0+)

The first launch extracts `smollm2-360m-instruct-q8_0.gguf`
(~368 MB) from `app/src/main/assets/models/` into
`filesDir/bundled-models/`. To rebuild the asset after a fresh
clone, follow
[app/src/main/assets/models/README.md](app/src/main/assets/models/README.md)
— it documents the Hugging Face URL and the SHA-256 sentinel
the installer verifies against.

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
| Obs-1   | DONE     | Observability 1 — tracing (Off/Local/Otel) + log export + manual + tour + feedback + network monitor + VpnService capture. |
| 3       | Planned  | Cluster-shard federation, frontier-model MoE routing. |
| 4       | Planned  | Cooperative training loops (LoRA on `:core-training`). |
| 5       | Planned  | Adaptive thermal/power tuning from real-usage telemetry. |

---

## License

Apache 2.0 — see [LICENSE](LICENSE).

## Acknowledgments

Built on the shoulders of:

- [RunAnywhere SDK](https://github.com/RunanywhereAI/runanywhere-sdks) — Apache 2.0
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) — Apache 2.0
- [ONNX Runtime Mobile](https://github.com/microsoft/onnxruntime) — MIT
- [llama.cpp](https://github.com/ggerganov/llama.cpp) — MIT
- [Hugging Face](https://huggingface.co) — model hosting
- [Jetpack Compose](https://developer.android.com/jetpack/compose) + [Material 3](https://m3.material.io)

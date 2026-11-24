# Device Compatibility & Recommended Configurations

> **The Meshlit debug build is feature-rich but still in active development.**
> Issues are expected — UI may glitch, transitions may stutter, and some
> features may be disabled on lower-end devices. Please report what you find
> via `Issues → Bug report`. The roadmap table at the bottom of this doc
> tracks which features are stable vs. experimental on each device tier.

This document is the **source of truth for testers**. If you download a
debug APK from the GitHub releases page, this is the doc that tells you
what to expect on your device and what features will actually unlock.

---

## 1. TL;DR — does Meshlit run on my device?

| Your device | Tier | App launches? | Inference? | Cluster (host)? | Browser UI? |
|---|---|---|---|---|---|
| Pixel 8 Pro / Samsung S24 Ultra          | **A — flagship phone**   | ✅ smooth | ✅ fast (LLM 7B q4) | ✅ yes | ✅ sub-100 ms |
| Pixel 7 / Samsung S23 / OnePlus 11        | **B — high-end phone**    | ✅ smooth | ✅ usable (LLM 3B q4) | ✅ yes | ✅ < 200 ms |
| Pixel 6a / Samsung A54 / mid-range 2022  | **C — mid-range phone**   | ✅ usable | ⚠️ slow (LLM 1B q8) | ⚠️ standby only | ✅ < 500 ms |
| Pixel 4a / Samsung A14 / older 2020      | **D — low-end phone**     | ⚠️ slow UI | ⚠️ very slow (LLM 0.5B) | ❌ no | ⚠️ 1-2 s |
| iPad / Galaxy Tab S6+                     | **E — tablet**            | ✅ smooth | ✅ usable | ✅ yes | ✅ good |
| Pixel Tablet / iPad mini                 | **F — small tablet**      | ✅ usable | ✅ usable | ⚠️ standby only | ✅ good |
| Chromebook / Linux mini-PC               | **G — x86 desktop**       | ✅ smooth | ✅ fast (depends on RAM) | ✅ yes | ✅ good |
| Mac M1+ / Windows 11 PC                  | **H — workstation**       | ❌ no install (browser) | n/a | n/a (browser only) | ✅✅ best |
| Mac Intel / Windows 10 PC                | **I — older PC**          | ❌ no install | n/a | n/a | ⚠️ works |
| Router / NAS / VPS                        | **J — server**            | ❌ not a target | n/a | n/a | n/a |

> **Important:** Meshlit is **Android-only**. The phone is the cluster.
> Mac / Windows / Linux users access the cluster **through a browser** at
> `http://meshlit-master.local:8080/` — no install, no extension. See
> [§7 — Browser-client compatibility](#7-browser-client-compatibility).

---

## 2. Honest "might be unstable" report

The 0.2.x debug build **may** exhibit one or more of the following on
specific device categories. We've grouped them by likelihood:

### Common on Tier D (low-end phones)
- **UI jank during scroll.** The Stitch glass-morphism design uses
  `Modifier.glow()` + `MeshlitMeshGradientBackground` which composites
  multiple blur layers. On Mali-G57 / Adreno 6xx-class GPUs the frame
  budget overflows around 24 fps. **Workaround:** Settings → Display →
  "Reduce motion" disables the floating orbs and breathing CTAs.
- **Web UI (`/`) takes 3-5 s to first paint.** The QR generation runs
  on the request thread; it's < 30 ms on a Pixel 7 but ~ 600 ms on a
  Pixel 4a.
- **Cluster webserver (port 8080) may fail to bind** if another app
  already holds it (corporate MDM profile, VPN, etc.). The app
  auto-falls back to `localhost:0` and the cluster card goes red.
  **Workaround:** Settings → Cluster → BindScope → OFF, then back to LAN.

### Common on Tier C (mid-range phones)
- **Inference speed drops to ~6 tokens/sec on a 3B q4 model.** The
  LlamaCpp engine is fine, but the phone's thermal envelope kicks in
  after ~ 90 s and the scheduler throttles. **Workaround:** drop
  max_tokens to 64, or use the 1B model.
- **Cluster handover occasionally loses the in-flight prompt.** The
  kube scheduler's takeover protocol (see §26.5 of the user manual) is
  best-effort — a battery-drained host phone won't send a graceful
  `BYE` envelope. The orchestrator surfaces `pipeline.host_lost` to
  the chat UI and the user can re-prompt.

### Common on Tier B (high-end phones)
- **Notification grouping is off on OneUI 6.** The InAppNoticeCenter
  uses SystemUI's bundled notifications; OneUI's "Brief" view
  compresses them into a single line. **Workaround:** Settings →
  Notifications → "Show all categories".
- **Picture-in-Picture for the Jobs screen stutters on first entry.**
  Compose lazy-list measurement triggers a recomposition. We ship a
  fix in 0.2.4.

### Common on Tier A (flagship phones)
- We don't have known regressions. If you find one, **please file a bug
  with `adb logcat -d -s MeshlitApplication`** — the logs are tiny
  because the kube scheduler is happy.

### Common on Tier E / F (tablets)
- **No landscape support for the Jobs screen.** The chat UI is locked
  to portrait. The Cluster webserver card is responsive.
- **Split-screen with the keyboard hides the FAB.** Compose handles
  the window-insets but the FAB anchor sits at the bottom edge.

---

## 3. Hardware categories & what unlocks

Meshlit's host-election (`KubeScheduler`) classifies each device into
a **hardware tier** based on the same signals the kube scoring uses
(see [core/cluster/KubeScoring.kt]). The tier decides which features
unlock:

### Tier A — flagship phone (FULL)
- **Tier tag:** `FULL`
- **Typical example:** Pixel 8 Pro / Samsung S24 Ultra / OnePlus 12
- **Min RAM:** 8 GB
- **Recommended model:** TinyLlama-1.1B q4, Phi-3-mini q4, Llama-3-8B q3
- **Unlocks:**
  - Cluster host election (auto-promoted when peer drops)
  - Browser UI w/ QR in < 100 ms
  - Multi-runtime JIT (LlamaCpp + ONNX Runtime in parallel)
  - Voice agent (speech-to-text + LLM + text-to-speech round-trip)
  - Vision agent (camera capture + captioning)
  - Sharded inference (4-phone load balanced over LAN)
  - Cooperative LoRA training (ring all-reduce)

### Tier B — high-end phone (FULL)
- **Tier tag:** `FULL`
- **Typical example:** Pixel 7 / Samsung S23 / iPhone 14 Pro
- **Min RAM:** 6 GB
- **Recommended model:** TinyLlama-1.1B q4, Phi-3-mini q4
- **Unlocks:** All Tier A features except:
  - Multi-runtime JIT (single LlamaCpp instance)
  - Cooperative LoRA falls back to 2-phone ring only

### Tier C — mid-range phone (MID)
- **Tier tag:** `MID`
- **Typical example:** Pixel 6a / Samsung A54
- **Min RAM:** 4 GB
- **Recommended model:** TinyLlama-1.1B q8, Qwen-1.5B q4
- **Unlocks:**
  - Cluster worker (forwards to a Tier A/B host)
  - Browser UI (slower, ~ 500 ms)
  - Single model inference
  - Voice agent (text-to-speech only, no STT)
  - **No** vision agent
  - **No** cooperative LoRA
  - **No** sharded inference

### Tier D — low-end phone (LITE)
- **Tier tag:** `LITE`
- **Typical example:** Pixel 4a / Samsung A14
- **Min RAM:** 3 GB
- **Recommended model:** SmolLM2-360M q8 (the bundled starter)
- **Unlocks:**
  - Cluster standby (peer discovery; cannot be host)
  - Browser UI (slow, 1-2 s)
  - Single small-model inference
  - **No** voice agent
  - **No** vision agent
  - **No** training
  - **No** sharded inference

### Tier E — high-end tablet (FULL)
- **Tier tag:** `FULL`
- **Typical example:** iPad Pro / Galaxy Tab S9+
- **Min RAM:** 8 GB
- Same as Tier A. The larger screen unlocks split-pane (chat +
  cluster map) on the Jobs / Cluster screens.

### Tier F — small tablet (MID)
- **Tier tag:** `MID`
- **Typical example:** Pixel Tablet / iPad mini
- **Min RAM:** 4 GB
- Same as Tier C, plus the chat UI uses the tablet's extra vertical
  space for an inline token-rate gauge.

### Tier G — x86 desktop (FULL, lower priority)
- **Tier tag:** `FULL` (when Meshlit is installed)
- **Typical example:** Mac Mini / Intel NUC / Steam Deck
- **Min RAM:** 8 GB
- Same as Tier A. **Note:** Meshlit currently ships only for Android.
  Use the **browser client** (Tier H below) until the Mac/Linux/
  Windows apps land.

### Tier H — workstation (browser client only)
- **Tier tag:** n/a (no install)
- **Typical example:** Mac M1 / Windows 11 PC / Linux workstation
- **What you get:** Open `http://meshlit-master.local:8080/` in any
  modern browser. The chat UI, model picker, cluster map, and QR
  generator all work. **No install required.**
- **Best for:** dApps, IDE integrations, LM Studio / Open WebUI proxies.
- **Browsers:** Chrome 110+, Safari 16+, Firefox 110+, Edge 110+.

### Tier I — older PC (browser client, slow)
- **Tier tag:** n/a
- **Typical example:** Mac Intel 2017 / Windows 10 PC
- Same as Tier H, but the SSE streaming stutters on Safari 14. Use
  Chrome for the best experience.

### Tier J — server / VPS / NAS (not a target)
- **Tier tag:** SERVER
- Meshlit is not designed to run on a VPS. The cluster is **always**
  hosted on phones that the user physically owns. If you want to run
  Meshlit on a Raspberry Pi, follow the **Tier G** instructions and
  install the Android arm64 image via a custom ROM.

---

## 4. Per-feature compatibility matrix

| Feature | Tier A | Tier B | Tier C | Tier D | Browser (H) |
|---|---|---|---|---|---|
| Local inference (≤ 1B q8)            | ✅ | ✅ | ✅ | ✅ | — |
| Local inference (3B q4)              | ✅ | ✅ | ⚠️ slow | ❌ | — |
| Local inference (7B q4)              | ✅ | ⚠️ slow | ❌ | ❌ | — |
| Cluster worker                       | ✅ | ✅ | ✅ | ✅ | — |
| Cluster host election                | ✅ | ✅ | ⚠️ standby | ❌ | — |
| Browser UI (chat)                    | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| Browser UI (model picker)            | ✅ | ✅ | ✅ | ✅ | ✅ |
| Browser UI (cluster map)             | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| Voice agent (TTS)                    | ✅ | ✅ | ✅ | ❌ | — |
| Voice agent (STT)                    | ✅ | ✅ | ⚠️ partial | ❌ | — |
| Vision agent (camera + caption)      | ✅ | ✅ | ❌ | ❌ | — |
| Image generation (Stable Diffusion)  | ✅ | ⚠️ slow | ❌ | ❌ | — |
| Cooperative LoRA training (2 peers)  | ✅ | ✅ | ❌ | ❌ | — |
| Cooperative LoRA training (4 peers)  | ✅ | ⚠️ | ❌ | ❌ | — |
| Sharded inference (4-phone)          | ✅ | ✅ | ❌ | ❌ | — |
| MCP server (Claude desktop bridge)   | ✅ | ✅ | ✅ | ✅ | ✅ |
| OpenRouter fallback (cloud)          | ✅ | ✅ | ✅ | ✅ | ✅ |
| OpenAI-compatible HTTP API           | ✅ | ✅ | ✅ | ✅ | ✅ |
| Stitch glass UI (motion)             | ✅ | ✅ | ⚠️ | ⚠️ disable | ✅ |
| InAppNoticeCenter                    | ✅ | ✅ | ✅ | ⚠️ | — |
| Background foreground-service (FGS)  | ✅ | ✅ | ✅ | ⚠️ killed | — |

Legend: ✅ works · ⚠️ degraded / partial · ❌ feature disabled

---

## 5. Recommended debug configs by tier

### Tier A / B — flagship or high-end phone
```
Build:       assembleDebug
Model:       Phi-3-mini-4k-instruct-q4.gguf (≈ 2.3 GB)
Quant:       Q4_K_M
Context:     4096 tokens
GPU:         auto (Vulkan if available)
Cluster:     BindScope = LAN, eligible as host
Notes:       Set Settings → Performance → "Aggressive GPU offload"
             to unlock the 7B model. Watch the thermal gauge; if it
             hits HOT, the kube scheduler will demote you.
```

### Tier C — mid-range phone
```
Build:       assembleDebug
Model:       TinyLlama-1.1B-Chat-v1.0-q4.gguf (≈ 700 MB)
Quant:       Q4_K_M
Context:     2048 tokens
GPU:         CPU only
Cluster:     BindScope = LAN, standby only
Notes:       Disable voice agent (saves 80 MB RSS). Disable
             motion in Settings → Display.
```

### Tier D — low-end phone
```
Build:       assembleDebug
Model:       SmolLM2-360M-Instruct-q8.gguf (≈ 360 MB, bundled)
Quant:       Q8_0
Context:     2048 tokens
GPU:         CPU only
Cluster:     BindScope = OFF, worker only
Notes:       Reduce motion in Settings → Display. Disable
             notifications bell. Expect 0.5 – 1 tokens/sec on
             first generation (warms up the kernel page cache).
```

### Tier H / I — browser-only client
```
Browser:     Chrome 110+, Safari 16+, Firefox 110+, Edge 110+
URL:         http://meshlit-master.local:8080/
Notes:       Discover the master via mDNS (Bonjour on macOS,
             Windows services on Windows). If mDNS is blocked,
             ask the master phone for the IP via the
             Cluster → "Show QR" button.
```

---

## 6. How to verify the cluster is working

1. Open the **Cluster** tab on every Meshlit phone. The "Peers" list
   should show every other phone on the same Wi-Fi.
2. The card with the **red halo** is the current host. The kube
   score (`1.42` etc.) is shown next to the node ID.
3. Tap **"Re-elect now"** to force a re-evaluation. Within 5 s the
   highest-scoring phone should take the halo.
4. From a Mac/PC on the same Wi-Fi, open
   `http://meshlit-master.local:8080/` and run a chat. The first
   response should arrive in < 1 s.
5. Yank the host phone's battery. Within 30 s the cluster should
   re-elect a new host.

If the cluster re-election loops forever, the kube scheduler is
churning on a flaky probe. Check the logcat:

```bash
adb logcat -d -s MeshlitApplication -s KubeScheduler | tail -50
```

Look for `kube.tick.fail` lines. They indicate a peer is unreachable
and the scheduler is retrying its probe.

---

## 7. Browser-client compatibility

The browser UI is a static HTML/JS/CSS bundle shipped inside the
APK at `app/src/main/assets/web/`. It's served at the cluster
master's `http://<host>:8080/` and is **fully OpenAI-compatible**
(`/v1/chat/completions`, `/v1/models`, `/v1/runtimes`).

- **Chrome 110+** — best experience. SSE streaming uses the
  `EventSource` shim that the chat client bundles.
- **Safari 16+** — works. SSE on Safari 14 is buggy; we fall back
  to a single fetch.
- **Firefox 110+** — works. SpeechRecognition API is not available
  (no voice agent in the browser).
- **Edge 110+** — works. Same as Chrome.
- **Lynx / w3m / curl** — `curl http://meshlit-master.local:8080/v1/chat/completions`
  works. The chat UI itself needs JavaScript; the JSON API is
  text-only.

To point Open WebUI / LM Studio / Aider / VS Code at the cluster:

```
base_url: http://meshlit-master.local:8080/v1
api_key:  (any string — LAN trust is the auth model)
model:    smollm2-360m-instruct-q8_0.gguf
```

---

## 8. Reporting device-specific bugs

When you file a bug, please include:

1. **Device model** (e.g. "Samsung SM-A546B", not just "Samsung")
2. **Android version** (Settings → About → Android version)
3. **RAM** (Settings → About → RAM)
4. **Tier** (look on the Cluster card — top-right shows
   `Tier: FULL` / `MID` / `LITE`)
5. **Reproduction steps** — what screen, what you tapped, what
   happened vs. what you expected.
6. **Logcat excerpt:**

   ```bash
   adb logcat -d -s MeshlitApplication -s InferenceHttpServer \
              -s KubeScheduler -s ClusterCoordinator > bug.txt
   ```

The 0.2.x debug build's logs are intentionally verbose (info+ for
the cluster pipeline, debug+ for the inference loop). We don't
recommend running logcat at debug level for the full device — the
inference loop will fill the buffer in 30 s.

---

## 9. Roadmap

| Phase | Status | What it brings |
|---|---|---|
| Phase 4.x (in progress) | ✅ shipped in 0.2.x | Stitch glass UI, RunAnywhere SDK parity, RunAnywhere SDK catalog |
| Phase Hivemind-1 | ✅ shipped in 0.2.x | Kubernetes-style cluster, browser UI, no-install-on-PC |
| Phase 4.5          | 🚧 in flight | Chat history persistence, agent voice polish |
| Phase Hivemind-2   | 📋 planned | Token-level pipeline parallelism (TP across phones), expert-parallel (MoE) |
| Phase 5.x          | 📋 planned | Mac / Windows / Linux desktop apps (so users can opt-in to richer hosts) |
| Phase 6.x          | 📋 planned | Federated over WAN (NAT traversal, trust tier escalation) |

---

## 10. Versioning

| Version | Date | Notes |
|---|---|---|
| 0.1.0 | 2025-08 | Initial release. Single-device, no cluster. |
| 0.2.0 | 2026-08 | Phase 4.x (Stitch UI) + Phase Hivemind-1 (cluster). |
| 0.2.3 | 2026-08 | Initial dev-tagged release. Same code as 0.2.0 + version bump. |
| 0.2.4 | upcoming | Bug fixes (PiP stutter, OneUI notification grouping). |

GitHub releases: <https://github.com/sabbirimon/meshlit/releases>

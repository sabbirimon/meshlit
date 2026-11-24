# Older Android Studio request and plan audit
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-23: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

## Current request reconciliation — 2026-11-06

The [Chat/Studio request inventory](CHAT_STUDIO_REQUEST_RECONCILIATION_2026-11-06.md)
reconciles this plan with the current main shell, passwordless local chat, bounded
project autonomy, bots, personalities, voice/translation and media requests.
Earlier architecture choices and completion labels below retain their dated
scope. Studio settings parity, durable history/RAG, training, media adapters,
managed device execution and release acceptance remain separate gates.

Reviewed 2026-10-23 against the separate `mllm` checkout's `PLAN.md`, `REQUESTS.md` and `TODO.md`. These files are historical requests and implementation notes from a different working tree. Their “done” labels do not prove that the current Meshlit APK contains or qualifies the same behavior. This table names the current gate to close before marking a request transferred.

| Older request | Current Meshlit evidence | Remaining gate |
| --- | --- | --- |
| R-01/06/09/19 settings, active model and popup regressions | Current Settings and Models have separate screens and the Android debug build compiles. | Recheck navigation, loaded-model truth, popup contrast and long text on both phones. |
| R-02/03 voice spectrum and animated icons | Pairing and Device radar now draw an active scan sweep. No current `MicSpectrumVisualizer` or reusable `AnimatedIcon` implementation was found. | Port only if live audio input level and accessible motion controls can be verified on device. |
| R-04 palettes and theme motion | Current Appearance settings provide palettes, fonts and animation control. | Test light/dark, large system text and reduced motion across all screens. |
| R-05/11/18 model download, import and progress | Current model catalog/download code and Models UI exist. | Test authenticated/failed URL, device import, resume/pause/cancel, storage exhaustion, integrity and load on a phone. GitHub/FTP import is a separate trust and provenance path. |
| R-10 agent screen and R-20/22 Auto Pilot repair | Current `AgentSession` has an autopilot continuation mode and the app has an agent UI. The older watchdog/repair executor was not found as a matching current module. | Design explicit observations, owner consent, bounded repair actions, rollback and audit; test cancellation and denied actions. |
| R-12 GitHub code/agent/tool import | Current file, agent and MCP areas are not proof of safe repository installation. | Add reviewed provenance, permissions, sandboxing and an owner approval path before code execution. |
| R-13 file management | A current Files workspace exists. | Test provider import/export, cancellation and hostile paths on device. |
| R-15 power, battery and thermal gauges | Current Resources/monitoring screens and device telemetry exist. | Check live samples, chart updates, units, thermal throttling and background/permission behavior on both phones. |
| R-21 bundled Qwen coder | Current starter APK uses a smaller SmolLM2 model to control install size. | Offer a verified optional coder-model download with hardware and storage admission; do not silently re-bundle a much larger model. |
| R-23 distributed LoRA training | `core-training` has ring/DiLoCo strategy contracts and tests; Fine-tuning describes a paired host job. | Prove a real licensed dataset, compatible training host, multi-device job, adapter output, evaluation, peer loss and cancellation. Contracts are not a completed phone cluster training run. |
| Old TODO #183 personality, skill templates and tuned weights | Current chat personality controls alter instructions. | Add per-model persona/skill templates and separately verified adapter selection; prompt styling is not weight tuning. |
| Old TODO #179 Ghostty terminal | Current Android terminal parser and separate desktop terminal paths exist. | Qualify any Android renderer replacement, dependency/license and NDK build before claiming Ghostty integration. |
| Old PLAN gateway/discovery/trust work | Current app has newer gateway, discovery, trust and pairing paths with different wiring. | Audit real call sites, denial/revocation, hostile peer payloads and end-to-end device behavior instead of copying old branch phase labels. |

The older plan's proposed commit order, phase tag and build status belong to that checkout. Current priority remains: working two-phone pairing and UI, safe model download/load, explicit permission boundaries, then physical multi-device inference and training gates.

## Desktop gap review, 2026-11-24

Compared the original Android Studio checkout’s PLAN.md, REQUESTS.md and TODO.md, the October Android gap audit, the November CS-01–CS-23 Studio inventory and the current native Studio entry points. Earlier prototypes and the current native package expose different features.

| Early request | Current desktop evidence | Missing or awaiting acceptance |
| --- | --- | --- |
| Durable chats, branching, personality and recall (CS-05, 07–09, 17) | Message actions, per-reply votes and an explicit local reply preference. Native threads are held in memory. | Encrypted restart-safe conversation history, session search/branching, per-model personas and evaluated learning from feedback. |
| Model sources, management and tuning (R-05, 11, 18; CS-03) | Public two-pane GGUF download picker, checksum verification, local-library imports, provider and peer routes. | Resumable transfer queue, gated entitlement handling, broad format execution, hardware-tested GPU offload and several concurrently resident models. |
| Documents, media and voice (CS-10–14) | Text/image attachments, Windows dictation and local read-aloud. | Durable document indexing with citations, qualified live captions/translation, integrated video/image/music generation and an end-to-end microphone test on target hardware. |
| Agents and automation (R-10, 12, 20, 22; CS-06, 16, 22–23) | Native IDE, actions dashboard, SSH profiles and MCP inventory. | Reviewed per-chat tool calls, independent durable agent runs, restart and revocation, native bot windows, verified tool imports and browser task execution. |
| Network and distributed execution (CS-15, 21) | Signed pairing, approved GGUF sharing, LAN inference and a Samsung LTE protocol-client test. HyperL has separate layer-worker evidence. | An end-to-end WAN test inside the released Android app, public relay commissioning, multi-host recovery/checkpoints and qualified physical accelerator combinations. |
| Contribution rewards and identity | Android signed opt-in receipts. Desktop uses the same receipt fields and contributor fingerprint. MeshCoin adds signed per-contributor chains, recordkeeper acknowledgments, optional percentage allocation and deterministic per-unit serials. | Cross-platform facade for desktop block replication on the released Android app, independent work verification, dispute handling and production settlement. |
| Phone/platform parity (R-02, 03, 04, 13, 15, 23; CS-20) | Desktop branding, themes, native toolchain and scoped device control exist. | Full-app hardware-backed lock, all-screen accessibility testing, mobile energy attribution, distributed LoRA qualification and parity for older phone-only workflows. |

The [original Android gap matrix](OLD_ANDROID_GAP_AUDIT_2026-10-23.md) also tracks cluster storage, cloud administration, group/offline messaging, resource billing, optional VM tooling and fleet diagnostics. See the [Studio request ledger](CHAT_STUDIO_REQUEST_RECONCILIATION_2026-11-06.md) for the individual CS request IDs.

The desktop catalog now offers LM Studio and Hugging Face as separate sources. The pinned official LM Studio SDK requests the model catalog and per-model download options. The HP's 0.4.28 guest session returned a permissions error for the Staff Picks RPC. Studio can instead read the public catalog at https://lmstudio.ai/models and follow publisher-listed GGUF sources to immutable Hugging Face files. The public page markup and the SDK's experimental Hub methods can change upstream. The Hugging Face dashboard supports paginated public GGUF search, publisher avatars, optional read-token sign-in, the user's model repositories, and sign-out. Read credentials stay in the extension secret store.

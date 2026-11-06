# Chat and Studio request reconciliation — 2026-11-06
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-06: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

This is the current request inventory, not a release certificate. “Source” means
implementation exists in this working tree; it does not mean the running preview
contains it. “Verified” names the exact test scope. Older records remain intact.

## Current requests and acceptance

| ID | Owner request | Current state | Next acceptance gate |
| --- | --- | --- | --- |
| CS-01 | A separate General Chat application window by default; IDE opens only for a workspace | Main shell and independent project-window source exist; native shell screenshots reviewed | Final rebuilt app: cold launch, restart after IDE use, independent project chat, no IDE chrome in main window |
| CS-02 | Chat settings sidebar and models/modes dashboard; Studio-only features grouped separately | Grouped shell, real Models/LLM/permissions pages and provider dashboard routes in source | Check every destination, empty states, save/reload, keyboard navigation, narrow windows and theme |
| CS-03 | Exact model name, local/online/device/cluster source, reasoning and tool permissions | Runtime-backed composer/source labels implemented; unsupported local reasoning stays Auto | Verify displayed route and actual dispatched request agree for each supported connection |
| CS-04 | Fix Qwen claiming it was created by Anthropic | Running old preview reproduced the false claim; grounded runtime identity passed isolated host and native packaged tests | Ship the changed host into the preview and repeat the owner's questions, including after misleading history; never treat generated self-description as provenance |
| CS-05 | Ordinary chat without a password; optional secure projects and first-use setup | Local in-memory usage and project security/action presets implemented; live local test created no vault | Verify optional lock, both project choices, cancel and restart; hosted/remote accounting currently still requires protected unlock |
| CS-06 | Powerful task agents with fewer repeat approvals and Accessibility/device control | Bounded workspace edits and app/model/task/chat-bound Mac grants in source | Native deny/revoke/Stop, stale files, app changes and expiry; shell, OS, device, MCP and credentials retain separate scopes |
| CS-07 | Copy, share/export and standard chat message actions | Copy, Reply, local export and read-aloud actions implemented; native Copy and Markdown export passed; audible speech qualification remains | Copy exact plain text, keyboard selection, local Markdown/text export, cancelled save and failure; public sharing is a separate opt-in destination |
| CS-08 | Multiple personalities and custom style | Eight-preset module, host validation and per-turn chat integration tested; native packaged Save/Reload passed | Save/reload and next-turn behavior, custom length limit, trusted identity precedence, reset and separate bot settings |
| CS-09 | Censored/uncensored preferences and guardrail lock/unlock | Local candid-style preference implemented at prompt level; full Android guardrail editor is not ported | Distinguish content style, actual model choice, project protection and action grants; no promise of changing learned model refusals or hosted rules |
| CS-10 | Natural live voice chat with transcripts and subtitles | Optional OpenLive call/transcript handoff exists; native inline Studio microphone/subtitles are open | Human microphone/speaker turn-taking, interruption, visible recording, Stop, transcript review, device loss and accessible captions |
| CS-11 | Tunable voice translation | Natural/translation prompt presets use OpenLive's real customInstructions endpoint; actual speech engine/language/voice controls remain in OpenLive | Live supported-language translation, source/target transcript display and tuning; Bengali speech remains unqualified; no recording starts when saving preferences |
| CS-12 | Chat read-aloud, voice choice and speed | Local macOS voice module and message integration implemented; contract tests passed, audible playback not qualified | Installed voice enumeration, actual playback, rate persistence, per-chat Stop, error and unsupported-platform behavior |
| CS-13 | Image and video/media generation | Android explicit provider adapters exist; Studio integrated generation surface remains planned | Capability-specific model selection, real artifact preview/export, job progress/cancel, endpoint/credential/cost disclosure; local generation only with a verified installed engine |
| CS-14 | Sound generation | Read-aloud/TTS is separate; Studio sound-effect/music generation remains planned | Real adapter and supported model, duration/format limits, playable artifact, cancellation, errors, usage and export; do not pass speech output off as music |
| CS-15 | Local/remote devices sharing power, large LLM sharding and coding projects | Real Mac/Samsung native layer completion over USB; earlier development WAN evidence retained | Managed enrollment/placement, oversized model, cluster-backed IDE edit, worker loss and authenticated WAN recovery |
| CS-16 | Own/third-party bots, hooks, skills and Android parity | Reusable bot profiles, endpoint bridges and reviewed MCP routes exist; universal channel/skill integration is not complete | Live vendor interoperability, scoped inbound/outbound behavior, reviewed skill execution and hook lifecycle; no claim of OpenClaw/AstrBot feature parity |
| CS-17 | App-wide bots/LLM self-improvement and reinforcement learning | Requested; no weight-training/RL implementation claimed | Scoped feedback, evaluation dataset consent, versioned skill/prompt proposals, regression thresholds, promotion/rollback; real RL requires separate trainer/reward/data qualification |
| CS-18 | One Management dashboard/group for models, devices, network, health and other administration | Unified overview and Settings group implemented; all 36 settings destinations registered and native page checks passed | Real runtime status; routes to Models/compute, Devices/clusters, Network/connections, Health/resources and Usage/storage; distinguish unavailable controllers from healthy services |
| CS-19 | Credentials and SSH/terminal administration | SecretStorage dashboard and explicit built-in OpenSSH profiles implemented/tested; live authentication remains open | Key checks/replace/remove without disclosure, host identity and cancellation; macOS System Terminal handoff is implemented with contract tests; Ghostty/Termius remain separate |
| CS-20 | Optional whole-app password/biometric lock and hardware-backed keys | Requested for desktop/mobile; existing BiometricGate interface is not a wired full-app lock | Backend-enforced lock across windows/jobs/IPC; native authentication-bound key use, recovery, idle/background lock, denial/enrollment changes and hardware proof |
| CS-21 | Cross-platform app and supported device hosting | Android/Intel Mac current builds; platform source/evidence matrix recorded | Windows/Linux/current Studio migration, Apple Silicon native runtime, Harmony NEXT tooling/device tests; iOS later with native Apple glass UI and real-device gates |
| CS-22 | Bot browser use with automatic/manual approval requests | Requested; Android browser and scoped desktop app/MCP controls remain separate | Browser discovery, explicit network/browser grants, bounded sessions, per-origin scope, manual/automatic action review, revocation and blocked/missing-runtime recovery |
| CS-23 | Per-bot detailed independent windows and access from General Chat | Detailed bot pages implemented; native separate window and bot chat in that window passed, with General Chat retained | Open/reuse/edit/remove, main-chat handoff, real native detached window and accurate capability status |

The first project choice offers **Local autonomous · no password**, with
bounded reads/edits and unsaved review. It does not imply shell/device/provider
authority; protected work remains a separate choice.

## Work order

1. Finish CS-04 and CS-07 in the actual preview; preserve drafts and clearly
   separate runtime metadata from generated identity claims.
2. Integrate and qualify CS-01–06, CS-08–09 and CS-12, including all main-window
   settings routes. Ordinary chat must remain usable without project setup.
3. Qualify CS-10–11 with real human audio and transcripts before labeling native
   Studio live voice complete. Keep OpenLive handoff clearly identified.
4. Build capability-specific CS-13–14 adapters and a generation UI using real
   provider contracts, then qualify real output and failure/cancel behavior.
5. Continue CS-15–16 and older device/model/recovery gates below. A UI increment
   does not replace physical sharding and recovery priorities.

## Older requirements retained

- **Models:** verified download/import, pause/resume/cancel, storage admission,
  accurate active-model labels, optional coder weights, model provenance and
  supported-host lifecycle. A connection profile is not a full model library.
- **Conversation data:** durable encrypted history, search, export, branching,
  attachment parsing and document retrieval. Current transient open-chat lists
  and project notes do not satisfy every history/RAG requirement.
- **Routing and dashboards:** native SDK, pipeline, HyperL and online-provider
  dashboards; per-scenario selection, free/paid fallback and measured usage/cost
  provenance. Do not infer pricing or authenticated quotas from a model catalog.
- **Agents and imports:** safe repository/tool imports, provenance and scoped
  execution; watchdog observations, bounded repairs and rollback; reusable
  per-model personas/skills and actual adapter selection remain distinct.
- **Training:** actual Soup host training/evaluation/GGUF export and multi-device
  training proof remain open. Prompt styling is not weight tuning or autograd.
- **Devices:** paired discovery, measured thermal/power/resource monitoring,
  secure remote links, committed recovery/fencing and independent owner tests.
  Job distribution, storage sharding and model-layer execution are separate.
- **Product quality:** accessibility, large text/reduced motion, file-provider
  failures, clean installs, production signing/notarization, physical mobile
  tests, upstream dependency review and LTS support gates remain open.

The old Compose-only desktop direction and mandatory local-chat unlock have
been superseded by the owner's newer window and passwordless-chat decisions.
Historical “done” labels and old bundled-model names are not current evidence.
No old request is silently removed because the corresponding menu is absent.

## Source and plan references

- [Current plan](../PLAN.md), [requests](../REQUESTS.md), [acceptance queue](../TODO.md), [progress](../PROGRESS.md).
- [All Android settings comparison](STUDIO_ANDROID_SETTINGS_PARITY_2026-11-06.md).
- [Older request audit](OLD_ANDROID_STUDIO_REQUEST_AUDIT.md) and [archived requests](history/REQUESTS-before-studio-2026-10-10.md).
- [Archived pre-Studio plan](history/PLAN-before-studio-2026-10-10.md), [desktop plan](DESKTOP_STUDIO_PLAN.md), [Studio/mobile LTS plan](STUDIO_MOBILE_LTS_PLAN.md).
- [Desktop evolution](DESKTOP_EVOLUTION_PLAN.md), [media routing](model-router-and-media.md), [local behavior and training](local-model-behavior-and-training.md).
- [Passwordless local identity test](desktop/evidence/studio-passwordless-local-identity-20261106.json) and [Mac/Samsung USB evidence](desktop/evidence/studio-mac-samsung-usb-native-20261106.json).

## Persistent bots and improvement design

The dashboard must distinguish saved chat profiles, workspace teams and connected
workers. Persistent workers need an actual host, durable task queue, heartbeats,
restart reconciliation, cancellation, resource/time budgets and scoped credentials.
Skills/hooks need versioned definitions, observed tool receipts, review of new
capabilities and revocation. A local Mac cannot continue working while asleep
unless an authorized running worker has accepted the job.

Improvement starts with opt-in corrections and outcomes, excluding secrets and
private data from datasets by default. Proposals for persona, routing or skills
are evaluated against held-out cases and permission regressions before promotion;
retain the previous version and a rollback action. Changes to application code
use normal tests and review. Actual fine-tuning/RL must specify trainer, base
weights, dataset provenance, reward metric, compute budget and evaluation. It
cannot silently rewrite permissions or install executable skills.

Cursor's [official Grok Bot documentation](https://cursor.com/docs/grok-bot)
describes persistent shared cloud computers, skills, routines and bot handoffs;
these inform the design, not a claim that Meshlit implements the same service.
The remaining supplied vendor pricing/model/plugin comparisons were not verified
and are not used as implementation contracts.

## Management navigation design

Keep New chat, Workspaces and Bots visible. Use a single **Management** group
with an overview and themed categories: Models & compute, Devices & clusters,
Network & connections, Health & resources, and Usage & storage. Chat settings
retain conversational mode/reasoning, personality, voice and permission controls.
Studio-only engineering capabilities stay separately grouped. Each management
card links to an actual controller; show selected/ready/unknown states from
runtime evidence rather than green health badges based on mere configuration.

Desktop SDK/pipeline/HyperL/provider dashboards requested in the LTS plan remain
part of Models & compute, with migration gates when only older Compose screens
exist. Device topology, network policy, security/recovery, audit/logs and budgets
remain administrative subareas. Do not claim full control by linking a setup
page. Shared settings and the overview must use the same persisted source and
show errors, cancellation and unsupported platforms consistently.

## Platform-wide scope and security decisions

All new requests apply to desktop and mobile, subject to supported capabilities.
The [cross-platform hosting plan](CROSS_PLATFORM_HOSTING_PLAN_2026-11-06.md) is the
platform matrix. iOS is explicitly a later phase: native Apple glass appearance,
Dynamic Type/VoiceOver and reduced-transparency alternatives; no iPhone/iPad is
available for real-device qualification. HarmonyOS NEXT is separate from running
an Android APK on an Android-compatible Harmony device. No host role is enabled
merely because a device appears in the list.

Whole-app lock must be enforced in the backend and every window/API path, not
just a screen overlay. Define passphrase recovery, native authentication-bound
key access, lock-on-background/idle policies, job cancellation/suspension, redacted
notifications and cross-window unlock leases before delivery. Local passwordless
work remains an explicit option. TPM, Secure Enclave, Android Keystore and Harmony
key services require real capability evidence; do not infer hardware protection
from generic encrypted storage. A biometric prompt alone does not encrypt data.

Use existing open-source/native bridges where suitable. Electron's documented
Touch ID API and AndroidX BiometricPrompt are primary platform-integration paths.
The MIT [bio library](https://github.com/yashikota/bio) is a candidate for isolated
cross-platform investigation, not an approved dependency or qualified adapter.
[Electron documents](https://github.com/electron/electron/blob/main/docs/api/system-preferences.md)
that its prompt itself does not protect stored data;
[Android documents](https://developer.android.com/identity/sign-in/biometric-auth)
authentication-bound cryptographic use. MediaPipe
[Face Detector](https://developers.google.com/edge/mediapipe/solutions/vision/face_detector)
returns face locations/keypoints; this is not evidence of secure identity or
liveness authentication. [ONNX Runtime Mobile](https://onnxruntime.ai/docs/get-started/with-mobile.html)
is an execution engine, not an app-lock policy. Optional face/presence features
must not release vault keys based on an unqualified camera classifier.

For browser bots, prefer an installed reviewed adapter or system browser. A
missing browser or denied network request produces a visible request/blocked
state. Auto mode can reuse approved app/origin/task limits; it cannot infer a
new grant or install unreviewed downloaded code. Manual mode remains available.
User credentials/login/MFA stay in the approved native/browser flow, and Stop
revokes the bounded session. Bot model assignments can use bundled local,
user-configured online, device or existing coordinator endpoints; selecting a
coordinator does not enroll workers or prove its placement.

## HyperL-owned model engine continuation — 2026-11-06

See [Studio engine plan](STUDIO_HYPERL_ENGINE_PLAN_2026-11-06.md). The engine belongs to standalone HyperL; Studio consumes its versioned interface. Expert accounting is implemented and tested in HyperL; expert paging and Studio integration remain planned. AMD partial offload is a correctness prototype with a measured slowdown, not a shipping performance feature. Desktop first-run agreements are deferred by the owner until engine work.

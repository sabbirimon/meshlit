# Desktop evolution, settings and cross-device plan
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-06: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

## Personality, live conversation, and media request reconciliation — 2026-11-06

The latest owner requests extend the separate Chat window and its settings. They
do not replace the earlier workspace, bot, autonomy, device-compute, security,
model-quality, and Android-parity requests recorded below and in the
[settings parity audit](STUDIO_ANDROID_SETTINGS_PARITY_2026-11-06.md). Track each
requested surface through implementation, wiring, packaged checks, and real use.

| Requested feature | Current implementation and evidence | Remaining acceptance |
| --- | --- | --- |
| Chat personality and custom instructions | [Personality module](../companions/studio-runtime/extension/chat-personality.cjs) has eight tone presets, bounded custom instructions, and persisted ordinary app preferences. Module tests cover validation, escaping and failed saves. Main Chat/backend integration is being qualified. | Open from main Chat and Settings; save, reload and relaunch; inspect the exact next request for local, hosted and bot chats; verify tone changes without changing model identity or tool authority. Retain workspace-specific agent policy separately. |
| Local candid discussion style | The personality module has Default/Candid discussion as a local style preference. It is separate from security controls and is intended only for the bundled local route. It does not alter model weights, learned restrictions, hosted-provider policy or device permissions. | Verify the local prompt includes the selected style, hosted requests omit it, custom text cannot replace runtime identity, and changing it cannot unlock data, grant tools or widen a project scope. Android behavior/guardrail-editor parity remains incomplete. |
| Per-message read aloud and voice settings | [Chat voice module](../companions/studio-runtime/extension/chat-voice.cjs) uses installed macOS voices through owned `/usr/bin/say` processes. Saved voice and rate, bounded text, Stop, owner isolation, errors, timeouts and cancellation have eight module tests. It sends text through stdin and does not use a speech API. | Finish composer/message-action wiring; verify installed-voice selection and audible output on this Mac; test Stop/new reply/window close, removed voices and output-device changes. Non-macOS should expose the separate OpenLive setup honestly. Speaker playback is not microphone/transcription proof. |
| Live voice conversation, captions and transcript | Existing OpenLive is the separate live-call interface. Studio's Voice and vision page now adds live-call/transcript navigation and text-response tuning under integration. The Studio page itself does not capture a microphone or render a live transcript. | Exercise a real microphone-to-transcript-to-model-to-spoken-reply cycle; inspect captions, interim/final transcript, interruptions, Stop and capture release in the actual call UI. Check headset and device-loss paths independently. The reported Android “does not hear or reply” issue remains a distinct phone gate. |
| Live translation and voice tuning | [Live voice settings](../companions/studio-runtime/extension/live-voice-settings.cjs) adds conversation/translation, input/output language and response-style preferences through OpenLive custom instructions. This changes the language model's text response, not the recognition or synthesis engine. Integration and live language qualification remain open. | Save/load against the installed OpenLive runtime without overwriting unrelated instructions; test unsupported languages, translation target selection, transcript accuracy, spoken target language and cancellation. Configure actual microphone sensitivity, turn timing, speech engine, voice and speed in OpenLive. Bengali text preference is not evidence of Bengali recognition or speech; independent source/target speech pipelines are not yet provided by this preset. |
| Images, video and generated speech | Android has explicit online image, speech, vision and video-job adapters in [MediaGeneration.kt](../app/src/main/kotlin/com/meshlit/media/MediaGeneration.kt). Those adapters do not establish current Studio generation support. A native Studio media creation/output page remains planned. | Design modality-specific model/provider selection and request review, protected credentials/accounting, real progress/cancel/error states, bounded downloads, preview/export/delete and restart recovery. Qualify actual image and speech artifacts plus video submit/poll/download, separating an accepted job from a finished video. |
| Sound effects, music and other media | Requested; no general Studio sound/music generation adapter or qualified local diffusion/video/music runtime is currently provided. Speech synthesis is a different operation. | Select and license an actual supported runtime/provider, define modality-specific inputs and ownership/export metadata, implement bounded artifact lifecycle and usage handling, then generate and inspect real outputs. Unsupported controls must stay visibly unavailable until wired. |

Implementation order is personality/request wiring and local read aloud, then
real OpenLive live conversation/translation checks, then the desktop media
creation pipeline. Preserve the current working text/model routes and the separate
coding window with its own chat. Do not equate a style choice, a model source,
a settings link, a generated text response, or a unit fixture with successful
speech/media generation. Final integration evidence must update this checkpoint;
module completion alone does not close the product request.

## Chat defaults and controls checkpoint — 2026-11-06

Current source defaults local Chat to passwordless session accounting in memory,
with optional require-unlock on next launch. Protected hosted/remote accounting
and credentials stay separate. The visible Chat settings group covers models,
modes, dashboards, LLM/reasoning, tool permissions, connectors, devices, memory,
and protected unlock. Composer controls reflect the selected model/source.

Project setup separately selects security and a saved Ask/read-only/autonomous
workspace action preset. Edits remain bounded and unsaved for review. App-access
task grants bind app/model/human task/chat for 25 operations or 10 minutes and
end on Stop/completion; OS Accessibility consent remains independent.

The pinned offline Qwen runner was confirmed after a false Anthropic identity
reply. Runtime identity grounding now passes 15 host tests; live reply acceptance
is pending. Native screenshots verify the main shell. Final candidate checks and
native app-action requalification remain open; no complete parity is claimed.

## Separate main-window shell correction — 2026-11-06

The earlier Chat tab within the IDE did not fulfill the owner's application
layout. Current source gives empty windows a full-width main shell. Chat,
Workspaces, Models, and LLM settings are primary navigation; Usage and Settings
are at the bottom. Studio tools groups agents, bots, devices/clusters, MCP, and
resources. Models, Workspaces, and LLM settings have dedicated pages backed by
existing services. Open conversations are real transient sessions.

Project selection opens a separate IDE window, keeping its own chat and agents.
The main shell shares the active IDE theme. Workbench checksum refresh preserves
installation integrity checks. Five shell, five main-page, and nine packaging
tests passed. Next qualify the packaged startup, navigation, window separation,
and absence of the corruption warning visually. Session unlock limitations are
unchanged. Earlier Chat-first evidence qualifies routing, not this new shell.

Requested and planned 2026-10-31, before implementation. This plan extends the
voice work; it does not replace the native inference and device-compute priorities.
Each acceptance gate is independent. A source change, health response, unit test,
installed app and real device execution are different evidence.

## Studio chat and compute continuation — 2026-11-06

Keep the chat and project windows separate and use the active IDE theme across
the Studio UI. General Chat uses direct model messages. Explicit agent modes
retain workspace scope and review. Models and LLM settings cover route choice,
local sampling, output length, and history. The source picker exposes a local
model, reviewed providers and devices, approved coordinator APIs, agents, bots,
and reviewed MCP connectors.

Studio generated through two CPU RPC workers on one Mac, then through physical
Mac and Samsung CPU workers over scoped USB. The remaining sequence is native
packaged navigation, cluster-backed project edits, then managed placement and
models larger than one node's usable memory. Use the earlier development
Tailscale/LTE run as a separate baseline for managed WAN and failover.
[Ordered plan](../PLAN.md#studio-chat-workspace-windows-and-mixed-device-compute--2026-11-06) ·
[Evidence](../PROGRESS.md#studio-chat-and-cluster-routing--2026-11-06).

## Why, where and how

| Need | Why it fits Meshlit | Where | Implementation and acceptance |
| --- | --- | --- | --- |
| Persistent useful memory | Avoid repeatedly sending entire histories; make retained context inspectable | Studio workbench, existing protected knowledge architecture; mobile PersonalMemory | Default-off, project-scoped encrypted notes, bounded search, provenance, preview, forget and explicit local/remote recall. Test corruption, persistence, isolation and revocation. Optional claude-mem connector preserves upstream capture/search features. |
| Agent computers and multi-agent work | Let desktop companions supply environments Meshlit does not bundle | Studio Services and existing MCP dispatch | Configure owner-installed OpenMausBot/OpenBot, check exact health contracts, open their complete UI, attach supported MCP through existing per-call review. Preserve upstream auth, setup and licensing. Do not invent an OpenBot MCP or confuse generic health with authenticated readiness. |
| Understandable settings | Current workbench Settings opens generic editor preferences; mobile Advanced is a long flat list | Dedicated Studio Settings hub; shared Android modern settings | Group by task, searchable descriptions, distinguish editor/product settings, reveal advanced controls without deleting access. Keep existing routes and saved settings. Test every catalog route and trace changes to persistence/backend. |
| Context preview | People should see what memory may accompany a request and where it goes | Studio memory/chat | A model-independent context preview with source/character bounds; actual runtime token counts only when available. It is inspired by controlled conversation state, not OpenAI proprietary compaction. |
| Action receipts | Review should lead to an understandable result and Stop | Existing reviewed agent tool dispatcher | Record bounded non-secret action metadata and outcomes, including denied/failed/cancelled. Do not persist raw prompts, keys or tool arguments in audit metadata. No automatic replay after restart. |
| Engine and HyperL harmony | Devices need consistent admission and honest capabilities | shared-workspace, desktop-engine, core-inference, core-hyperl, desktop-hyperl | Audit existing portable contracts, run both platform tests and native CPU checks, fix mismatches. Keep LLM inference distinct from HyperL numerical kernels. Unsupported GPU/NPU/OS/device paths remain unavailable. |
| Vendor choice | Models, speech and tools should not require one company | Existing local and compatible provider adapters | Include Meta Llama, OpenAI, Google, Anthropic, Mistral, DeepSeek and open runtimes as clearly described routes. Provider protocol, model format, license, installed backend and modality each require compatibility. No claim that every model works or that public weights imply free hosted inference. |
| Complete distributable builds | The newer workbench currently depends on absolute development paths | Studio packager, Gradle builds, platform CI | Preserve existing packaged Studio, build current workbench explicitly as development until portable dependencies/licenses are bundled and relocation-tested. Produce current macOS and Android artifacts; Windows/Linux/ARM qualification requires target runners. No false universal installer label. |
| Detailed manual and settings verification | A visible switch is not proof of implementation | User guide, offline help, audit evidence | Document setup, data destinations, persistence, prerequisites, failure recovery, Stop and limits. Inventory all settings destinations and mark source-reviewed/tested/device-tested/unavailable separately. |

## Upstream decisions

Reviewed sources and revisions (runtime installation is separate):

| Project | Reviewed revision | License at revision | Use |
| --- | --- | --- | --- |
| [claude-mem](https://github.com/thedotmack/claude-mem) | `70aa40042cfb8157a0d84954b9c0d2b30b50af80` | Apache-2.0 root license | Optional installed worker/MCP, persistent observation search/timeline/details and upstream viewer. No automatic cloud observer or paid fallback. |
| [OpenMausBot](https://github.com/milind-soni/OpenMausBot) | `21628af229abe8100cdd1f8761ccca391ff56bff` | Apache-2.0 | Optional full UI and documented installed `openmausbot mcp`; preserve paired token/port and upstream approvals. |
| [OpenBot](https://github.com/CopilotKit/OpenBot) | `743649efc3f21d7bf0badbe6af2bcd5daedd97ce` | MIT | Optional full source-template UI/API. Docker, Bun, Postgres, models and CopilotKit Intelligence configuration remain upstream prerequisites. AG-UI is not MCP. |
| [Impeccable](https://github.com/pbakaus/impeccable) | Guidance read 2026-10-31 | Apache-2.0 | Apply Operate-mode hierarchy, native controls, readable state and keyboard access. No downloaded hooks/telemetry needed. |

These are integration boundaries, not a promise that a settings link reproduces
every upstream feature. Full upstream applications remain optional and independently
configured; individual Meshlit adapters must state which operations they implement.
Future upgrades must revalidate protocols and licenses against a new pin.

## Review findings and feature decisions

- Keep native Android inference, existing optional local desktop backend, usage
  ledger, permission gates, SSH, MCP, paired hosts and real voice adapters.
- Reuse protected knowledge and explicit personal memory. Do not advertise the
  unused in-memory core-agent-memory scaffold as durable production memory.
- Consolidate vendor navigation under Models/Services. Preserve commands and
  settings; remove misleading duplication, not user data or working features.
- Keep standalone agents and phone/browser automation under their existing
  grants. Installing a companion does not grant it device or workspace authority.
- Distinguish editor preferences from Meshlit settings. Distinguish saving a URL
  from connecting to a service and an endpoint response from model readiness.
- Correct stale architecture/build documentation, but retain dated historical
  evidence. Do not change third-party license text or overwrite previous voice work.

## Desktop/mobile contract

Desktop is the primary surface for memory, editor workflows and optional isolated
computer companions. Android receives grouped searchable settings, manual updates,
existing encrypted memory and supported paired-host operations. Docker, Electron,
desktop SDKs and desktop VM images are not Android capabilities. A phone can be a
client or approved node without executing every host feature.

Cross-device compatibility is capability-based: OS/ABI, installed runtime revision,
model artifact/format, available memory, operation support, trust and permission.
No device name implies compute eligibility. No automatic backend substitution or
claim of combined GPU/LLM execution follows from HyperL kernel support.

## Distinctive model-independent features

1. **Context preview:** see bounded project memory selected for a request; separate
   local and online permission. Keep unknown token counts unknown.
2. **Reviewed action history:** inspect actual outcomes after approval and stop
   ongoing work through existing authority. No recovered side effects auto-run.
3. **Capability-first setup:** routes explain on-device, local-host or online
   execution and the prerequisite preventing an unavailable option from working.

OpenAI references are inspiration for conversation state and human review:
[conversation state](https://developers.openai.com/api/docs/guides/conversation-state),
[guardrails and approvals](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals).
Meta models follow their own model terms and backend compatibility:
[Llama models](https://github.com/meta-llama/llama-models).
Portable runtime references include [llama.cpp](https://github.com/ggml-org/llama.cpp)
and [ONNX Runtime](https://github.com/microsoft/onnxruntime). These references do not
add a new runtime, account entitlement or hosted provider by themselves.

## Ordered implementation and verification

1. Inventory source/docs/settings and record defects and coverage. Architectural
   review is not a claim of reading or executing every line of the repository.
2. Implement searchable Studio Settings/Services and grouped Android settings.
3. Add protected project memory, context preview and reviewed-action receipts.
4. Add optional upstream connections with bounded health checks and supported
   MCP attachment. Keep unavailable prerequisites visible and non-destructive.
5. Run affected contracts, all desktop/workbench regressions, core-engine/HyperL
   checks, both Android test/build/lint gates and mandatory bundled-model integrity.
6. Build current desktop/Android artifacts, inspect rendered settings and verify
   native execution where available. Record hashes, target, signing and limits.
7. Update detailed manual, offline help, PLAN/PROGRESS/TODO/REQUESTS and tracking.

## Acceptance limits that must stay visible

All-settings runtime verification needs configured providers, OS grants and actual
devices; absent prerequisites remain untested or unavailable, never passed. Live
paid services, full upstream installation, clean-machine portability, Windows/Linux
and additional GPU/NPU/phone hardware require their own evidence. No credentials
are requested in chat, no model weights/binaries enter git, and no release is
published by this work. Exact ChatGPT allowance/remaining chat tokens are not
available to this agent; compact updates and saved progress reduce repetition.

## Native implementation choices

The owner additionally requested Rust/C++/C# where useful. Existing llama.cpp and
HyperL native CPU paths already use C++. Preserve their narrow ABI and testable
contracts; do not rewrite them based on a language-performance assumption. Rust
is a candidate for future bounded host/transport services with measured need.
C# is a potential Windows-specific adapter, not a new cross-device requirement.
Changes must be benchmarked with real workloads and retain model/license boundaries.

## Reported defects and owner priority

The owner reports chat tools cannot be enabled and Android live conversation starts
but does not hear or reply. These are tracked independently from playback tests.
Settings/routing audit follows the requested repository integrations; previous
voice playback success does not close the microphone-to-reply acceptance gate.

# Meshlit implementation and qualification plan
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-06: [Plan](PLAN.md) · [Progress](PROGRESS.md) · [Document status](docs/DOCUMENTATION_STATUS.md).
Scope: cross-platform tracking; feature and device gates remain separate. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

## HyperL engine priority — 2026-11-06

The owner moved the custom model engine into standalone HyperL; Studio will be
its application client. [Engine plan](docs/STUDIO_HYPERL_ENGINE_PLAN_2026-11-06.md)
separates specialist-model routing, native MoE expert paging, layer sharding and
cloud API calls. Plan-first, bounded implementation and focused tests take priority
to conserve credits. Agreement onboarding is explicitly deferred until engine work.

HyperL's first increment extends its existing residency planner with exact total/
active expert metadata, shared/router weights and cache accounting. All experts
remain counted; this does not execute expert paging. Standalone checks: 13 focused,
84 Python passed / 5 optional JAX skipped, Gradle distribution build successful,
5 native tests passed. Existing HyperL native CPU/RPC model evidence is separate.

Studio now has compact SVG navigation, a native Studio menu and source support for
a second desktop-only Qwen3-4B Q4_K_M reasoning model. The verified artifact is
2,497,280,256 bytes; Qwen2.5-1.5B stays the default. Integration checks: 225 Node,
46 packaged Python, 17 focused desktop backend tests passed. Native Qwen3 loaded
and answered 2+2 correctly after idle build daemons released memory; preserve the
memory admission guard. Native Thinking off returned 4 for 2+2; Thinking on returned 8 for 3+5;
Stop closed a further active request. Package provenance/launch evidence is
recorded separately. No Android model/default change.

An isolated AMD560X prototype passed 11 numerical cases and produced the same
short text as CPU with 4/29 layers on GPU. It was slower (0.75 versus 11.23 decode
tokens/s in that short probe), so it is not promoted into the app. See
[hardware evidence](docs/desktop/evidence/studio-amd560x-partial-offload-20261106.json).
Actual tiered MoE inference, efficient GPU kernels and the new Studio→HyperL client
remain pending; no general throughput or production claim is made.

Obsolete QA bundles, an old QA DMG, the abandoned Qwen3.5 partial and regenerable
Android intermediates were removed. Current/rollback builds, verified models,
final APKs/reports and user data were preserved. APFS shared blocks/snapshots mean
logical deleted bytes do not equal immediately reclaimed space; use the
[cleanup record](docs/desktop/evidence/build-storage-cleanup-20261106.json).

## Verified delivery checkpoint — 2026-11-06

Bots is a primary General Chat sidebar destination. The dashboard creates and
edits reusable bot profiles, assigns local or saved cloud/device/cluster model
connections, and opens per-bot Details in a separate native window. Starting
chat from detached Details keeps that chat in the detached window; General Chat
remains open. Details shows actual model/source, instructions, capabilities and
links to workspace agents, teams, companions, connectors and permissions.
Native window creation and chat placement passed. Saved notification handling
was corrected so it cannot block subsequent bot actions.

Management groups models, devices/clusters, network, health, credentials, SSH
and usage. All 36 Settings destinations are registered; ten main pages passed
native navigation checks. Credential controls and reviewed built-in/system
Terminal SSH handoff have contract coverage; live key rotation and SSH login
are not claimed. The native bundled Qwen reply correctly identified local
Qwen2.5 inference. Copy and Markdown export passed using a synthetic chat.

Final extension suite: **221 passed**. Android V1/V2 settings tests passed
(seven each), both debug variants assembled and both lint tasks passed. The final
APK rebuild passed with the synchronized offline manual verified in all six APKs. Samsung
UI acceptance remains blocked by its locked/off screen; no data was replaced.
This is a development candidate, not a production/notarized release. Native
voice playback, hosted media, persistent browser bots, training/RL, hardware-
bound full-app lock, iOS and HarmonyOS qualification remain open.

[Native evidence](docs/desktop/evidence/studio-chat-settings-native-20261106.json) ·
[Current feature matrix](docs/CHAT_STUDIO_REQUEST_RECONCILIATION_2026-11-06.md).

## Cross-platform hosting, security and bots — 2026-11-06

These requests cover desktop and mobile: unified Management, credential/SSH
controls, optional full-app password/biometric lock, supported hardware-backed
keys, and device client/host roles. iOS comes later with native Apple glass UI
and accessibility alternatives; HarmonyOS NEXT retains its own native gates.
Bots need detailed separate windows, General Chat access, explicit local/cloud/
device/cluster model assignments, and browser/network access with manual or
scoped automatic approvals. Whole-app lock, persistent workers and training/RL
remain distinct from current passwordless local autonomy.

[Per-feature status](docs/CHAT_STUDIO_REQUEST_RECONCILIATION_2026-11-06.md) ·
[Platform and hosting matrix](docs/CROSS_PLATFORM_HOSTING_PLAN_2026-11-06.md).

## Unified Management dashboard — 2026-11-06

Group model/compute, device/cluster, network/connection, health/resource and
usage/storage management under one main-window Management overview and settings
group. Reuse real controllers and runtime status; keep Chat settings and
Studio-specific tools distinct. Preserve planned SDK/pipeline/HyperL/provider,
security/recovery and audit dashboards with honest migration/qualification gaps.
[Design and acceptance](docs/CHAT_STUDIO_REQUEST_RECONCILIATION_2026-11-06.md#management-navigation-design).

## Consolidated Chat, voice, media and bot delivery — 2026-11-06

The [request reconciliation](docs/CHAT_STUDIO_REQUEST_RECONCILIATION_2026-11-06.md)
tracks each new request and preserves older model, routing, training, recovery,
and release gates. The [Android settings audit](docs/STUDIO_ANDROID_SETTINGS_PARITY_2026-11-06.md)
compares the original six groups and the new Management category; absent Studio routes are not marked complete.

1. Deliver the identity correction and Copy/local Export actions in the actual
   running preview. Keep the exact model/source and supported reasoning visible.
2. Finish main-window settings, personality/read-aloud integration, project
   security, reusable action scopes and separate IDE chat; qualify the package.
3. Add a visible Bots dashboard combining saved custom profiles, external
   connection setup and workspace-team navigation. Distinguish chat profiles,
   actual tool workers and genuinely persistent remote workers.
4. Qualify natural voice, transcripts/captions and translation tuning. Existing
   OpenLive handoff is distinct from native inline Studio live microphone UI.
5. Implement image/video and separate sound/music adapters with real outputs,
   previews, export, cancellation and declared provider capabilities/costs.
6. Build opt-in improvement loops: scoped feedback and evaluation datasets,
   versioned persona/skill proposals, offline regression evaluation, reviewed
   promotion and rollback. Weight fine-tuning/RL requires a real training
   backend, licensed datasets, reward design and measured evaluation; do not
   describe saved preferences or model self-critiques as reinforcement learning.
7. Continue physical managed device/sharding/recovery work and clean-machine
   release qualification. No roadmap item implies production readiness.

## Passwordless chat and approved autonomy — 2026-11-06

- Default local General Chat uses an in-memory usage ledger without a passphrase.
  Keep encrypted history and saved budgets intact. Hosted requests and protected
  records retain encrypted usage unlock. Offer opt-in local chat locking in LLM settings.
- On first project use, show protection and reusable permission presets. Retain
  each coding window's independent chat. Never unlock the project from the main
  window or confuse a project preference with an OS/tool grant.
- Add bounded workspace autonomy for reads and edits, preserving path/size limits,
  read receipts, stale-file checks, Stop and unsaved changes review. Device, shell,
  credential and MCP authorization remain independent.
- Add optional task-scoped Mac Accessibility grants, bound to a selected app,
  human task, model and chat. Expire after ten minutes or 25 actions and revoke
  when the task stops, completes, changes scope or the user revokes access.
- Keep standard features separate from Studio tools. Other agents can delegate
  inside user-granted scopes; autonomous permission escalation is not supported.

Live direct Chat verification passed with the bundled Qwen model: it identified
as local, denied the incorrect Anthropic claim, and answered 2 + 2 as 4. Three
requests were accounted in memory without creating a usage vault. Exact model
naming remains authoritative in the runtime UI, not model self-reports.
[Evidence](docs/desktop/evidence/studio-passwordless-local-identity-20261106.json).

Implementation and qualification continue below. This does not claim universal
app/device control, managed cluster orchestration, or production notarization.

## Separate main Chat shell redesign — 2026-11-06

The owner's reference and historical commit `0da68a9` require two independent
window roles. A Chat tab surrounded by IDE controls does not satisfy this UI.

1. Build the default main window around a full-height conversation surface and
   persistent navigation for Workspaces, Models, LLM settings, Agents, Bots,
   Devices, tools, Usage, and Settings. Retain the current IDE theme colors.
   Keep standard Chat/Models/Workspaces/Settings separate from the Studio tools
   group for agents, bots, device clusters, MCP and resource monitoring.
2. Remove editor activity bars, editor tabs, command-center search, and status
   chrome from that main window. Keep native window controls, dialogs, keyboard
   access, and visible errors. Keep existing model, usage, and tool controllers.
3. Open projects in separate full IDE windows. Each coding window keeps its own
   project chat and workspace agents; main Chat remains open and independent.
4. Keep navigation available while visiting Settings and other main-window pages.
   Show real open conversations and actual saved workspace paths only.
5. Refresh packaged file checksums after modifications, preserving installation
   integrity checking. Verify clean launch, settings navigation, new/switch/close
   chat, independent project window, and relaunch before calling the UI complete.

Source implemented; final packaged navigation checks remain in progress. Existing inference and physical sharding evidence
remains unchanged; it does not qualify this redesigned shell.

## Studio chat, workspace windows, and mixed-device compute — 2026-11-06

1. Keep General Chat on the direct completion path and retain the coding SDK
   for explicit workspace agent modes. Packaged startup and separate IDE window
   checks passed. Extend acceptance of Models, LLM settings, and the source
   picker in the shipped theme to saved choices, cancellation, and route failures.
2. Use the approved model API path for local inference, third-party providers,
   single-device model servers, and existing LAN or remote coordinators. Keep
   agent teams, reusable bots, and reviewed MCP connectors distinct from model
   routes. Qualify a trusted workspace with a cluster-backed coding session,
   a proposed edit, and an explicit review outcome.
3. Build on the authorized USB proof of Studio JVM inference through real Mac
   and Samsung CPU workers. Pair both on a trusted LAN, verify identity and
   runtime revision, and capture each worker's model hash, placement, usable
   memory, and thermal budget in a managed run. The earlier development
   Tailscale/LTE proof provides a second physical baseline. Test the managed
   Mac coordinator role separately from Android's path.
4. Prove a model that exceeds one node's measured usable memory fits the
   negotiated placement, including coordinator, context, and KV overhead.
   Record output, latency, peak memory, and behavior after a worker drops.
5. Qualify authenticated WAN operation, endpoint revocation, and interrupted
   project work. Specify independent gates for automatic takeover, live KV
   transfer, and custom hooks with explicit scope, audit, and revocation.

The direct chat, packaged startup, one-host RPC, and physical USB evidence are
recorded separately from managed qualification. [Progress](PROGRESS.md#studio-chat-and-cluster-routing--2026-11-06) ·
[Layer pipeline](docs/layer-pipeline-and-recovery.md).

## Meshlit continuation — 2026-11-05

Android now has coordinator CPU placement, owner-reviewed conversation recovery,
a reviewed hosted web search tool, custom agents and bot connections, transcript
review, and an on-device photo route. Studio adds a workspace changes panel and
file actions. The next qualification gates are managed two-device execution,
independent replica-owner recovery, live provider/bot interoperability, physical
microphone/photo use, and the packaged Studio workbench.
[Implementation and limits](docs/AGENT_CHAT_CHECKPOINT_2026-10-31.md#meshlit-continuation--2026-11-05).

## Explicit Android Chat / Agent continuation — 2026-11-04

Implemented saved per-conversation Chat / Agent choices, replacing English-keyword
activation. Selected groups alone cannot run chat tools; old chats default to Chat.
Settings and the composer expose the mode, final tool removal returns to Chat, and
device connection cards respect it. Existing grants and session-only exact-call
approval remain independent. Android unit tests, debug builds and lint subsequently
passed at the managed recovery checkpoint. Physical mode and tool acceptance
remain open.
[Details and next gates](docs/AGENT_CHAT_CHECKPOINT_2026-10-31.md).


## Agent and provider continuation — 2026-10-31

Complete the current agent/provider regression and device checks before expanding the harness. Next gates: capable-model evaluation, explicit agent mode, hosted tool-call adapters and verified action outcomes. [Checkpoint and repair guide](docs/AGENT_CHAT_CHECKPOINT_2026-10-31.md).


## Open resource network direction — 2026-10-31

The owner wants everyone to participate: individuals and organizations, including
clients that do not use Meshlit. Scope includes resource contribution and eventual
sales; single/local/multi-device, LAN/WAN and inter-cluster execution; USB-C,
Wi-Fi, Ethernet/fiber, Bluetooth/radio, sound/light/QR and other suitable pairing
carriers; smart sync/communication routing, load balancing and safe fallback.
[Architecture, sequence and boundaries](docs/OPEN_RESOURCE_NETWORK.md).
Current increment repairs admission, pairing and routing, and adds bounded
external-client resource discovery. Marketplace settlement, cross-owner leases,
new physical carrier adapters and universal WAN sharding remain planned.
[Progress and remaining gates](docs/RESOURCE_NETWORK_PROGRESS_2026-10-31.md).


## Saved desktop/Android integration checkpoint — 2026-10-31

Source checkpoint saved at the owner's low-credit request: desktop companion
integrations, encrypted project notes/context preview, grouped boxed settings,
Android live-voice diagnostics/UI, persistence and tool-selection fixes, agent
memory consent, engine/HyperL consistency and hook cancellation/revocation.
Final checks: 538 Android unit tests; 78 desktop passed/1 physical SSH skipped;
71 workbench tests. Both final Android source variants compile; V2 test APK assembles.
Full current-source APK/lint, portable desktop installation and actual human live
voice remain open. Upstream advisories are unresolved; no security clearance claimed.
[Checkpoint and next gates](docs/CHECKPOINT_2026-10-31.md) ·
[Plan](docs/DESKTOP_EVOLUTION_PLAN.md) · [Manual](docs/USER_MANUAL.md) ·
[Smaller-model repair guide, including mobile](docs/SMALL_MODEL_MAINTENANCE_GUIDE.md).


## Natural and free voice options — 2026-10-31

Added Android adapters for OpenLive Kokoro/Pocket and ElevenLabs, actual voice
discovery, saved selection, previews and local speech speed. Kokoro/Pocket run
on the approved host; native Android execution of those engines is not claimed.
ElevenLabs free-tier-only defaults on, checks the reported account tier/allowance
before synthesis and blocks paid/unknown/exhausted accounts. It is not a provider
billing cap; there is no automatic paid fallback. Credential/profile validity is
rechecked at every HTTP dispatch. Studio has real local speech previews and Stop.
Both host engines generated finite real speech and Studio previews. 802 core/app
tests passed (298 core; 252 per app flavor). ElevenLabs live account verification
requires the owner's key; none was requested in chat or used. Samsung SM-A207F/API30 playback qualification passed for both engines over the
owner USB tunnel. Both APK variants assembled; full app lints passed with zero
errors (399 warnings/19 hints each). The final V2 ARM64 debug APK was installed
without clearing app data. All 37 Studio tests and the pinned development-launcher
smoke check pass. Test services and the temporary USB reverse mapping were stopped.
[Evidence](docs/desktop/evidence/natural-voices-20261031.json). See [setup and model terms](docs/NATURAL_VOICES.md).


## Voice and vision tuning — 2026-10-30

Studio now has an optional OpenLive runtime/tuning dashboard with owned loopback
process start/stop, source selection and real model/vision settings read/save.
Local compatible servers and upstream provider configuration are supported through
OpenLive. Speech tuning uses the upstream engine settings. See
[setup, validation and remaining gates](docs/VOICE_AND_VISION.md). Physical
audio/vision, Android embedding, unified usage and optional Pipecat/HF adapters
remain open; this is a source increment, not a qualified installer release. Android
now also saves real PCM endpoint and Android TTS playback tuning. Both variant
test suites passed (252 tests each), plus 293 core-inference tests. Both APKs
assembled and both app lints passed with zero errors. Samsung offline speech and
local inference and microphone capture/cancel/reopen tests passed. Live human
conversation, acoustic barge-in, provider speech and camera/vision qualification
remain separate acceptance gates. The voice
review hardened cleanup, cancellation and PCM playback semantics.


## G1 contract hardening and math assessment — 2026-10-26

Durable ledger replay now rejects invalid domains, duplicate currency policies, inconsistent terminal costs and illegal event shapes. Any uncertain write disables the current authority until reopen. Android profile/key edits use one checked encrypted transaction; a failed commit blocks further credentials in that instance, and current profile/local-key bindings are rechecked before dispatch. Independent integer accounting and write-then-fail regressions pass; full regression, both debug assemblies and both lints passed. Samsung device follow-up is in progress. Studio durable credentials, all-adapter revocation, cross-device authority and live account/device qualification remain open. No automatic routing or provider billing-cap promise was added.

The [OpenAI math assessment](docs/MATH_RESEARCH_2026-10-26.md) reviews the pinned 719-manuscript catalog and selected compute/security candidates. Standalone HyperL gained bounded exact-arithmetic reduction tests; no theorem-derived speedup or cipher replacement is claimed.

## Optional Tailscale / VPN increment — 2026-10-26

The owner requested real VPN dashboards while keeping Basic settings minimal. Studio now reads optional installed Tailscale CLI status with bounded, cancellable local execution; Android reports its actual app-effective VPN transport separately from Tailscale package presence and hands management to the official client. Observations are opt-in, in-memory, timestamped and expiring. No unsupported connect toggle, paid API call, VPN reconfiguration or push is part of this increment. See [setup and evidence boundaries](docs/TAILSCALE_AND_VPN.md). Live tailnet and physical-device qualification remain open.


## Usage and optional gateway increment — 2026-10-26

Implement device-local encrypted usage and budget admission before automatic routing. Android online-provider text calls and Studio chat now use the shared ledger; optional OmniRoute/FreeLLMAPI profiles use existing compatible transports. Cross-device authority, other adapters, automatic invoices and live gateway qualification remain open. See [contracts and setup](docs/USAGE_AND_MODEL_BUDGETS.md); current checks are in PROGRESS.md.


## Implementation resumed — 2026-10-26

The owner authorized the first implementation step. [Baseline audit and dashboard increment](docs/desktop/STUDIO_BASELINE_2026-10-26.md) track source-backed Studio gaps, tests and the initial Basic/Advanced navigation cleanup. G1 financial/authority contracts and new gateway/VPN adapters remain separate work; this UI increment does not promote them to implemented.

## Studio and mobile LTS planning — 2026-10-26

The owner selected **plan first, then implement**. [The consolidated LTS plan](docs/STUDIO_MOBILE_LTS_PLAN.md) defines optional OmniRoute/FreeLLMAPI connections alongside direct free and paid providers, dedicated Models/Routing/Usage/VPN dashboards, minimal Basic settings, scenario automation, Studio parity and a gentle premium visual system. G0–G7 are proposed acceptance gates, not implemented features or a current LTS claim. Current source/device limitations remain open.

[Current paused checkpoint and ordered resumption gates](docs/PAUSED_CHECKPOINT_2026-10-24.md) · [Build/run](BUILD.md) · [Agent handoff](docs/SESSION_HANDOFF.md). Source publication does not resume application qualification.


## Execution record — 2026-10-24 continuation

**Paused by owner for HyperL-first delivery.** Preserve Meshlit source/docs; do not
continue APK builds, installs or voice qualification until resumed. The interrupted
final build is recorded in PROGRESS.md and is not a passed validation gate.

Carry out the ranked backlog without treating new menus as completed backends. The current implementation slice covers model reliability, origin-preserving navigation, shared persistent RAG, Studio model recommendations and HyperL admission/measurement. Each fix and failed device case is recorded in [PROGRESS.md](PROGRESS.md). Run changed contracts, then both APK/lint gates, then qualify on reserved Samsung. Keep physical multi-device, reverse relay, group delivery, operational cloud/training and monetary settlement acceptance separate.

The slice now also includes live turn-taking voice, captions, mute/interruption,
audio cleanup and eight shared defensive references. Stabilize the reviewed
source, run both flavor build/lint gates, then test Samsung controls, real offline
speech and knowledge ingestion. Keep online testing deferred and Xiaomi untouched.
The isolated SmolVLM probe is evidence for a possible CPU adapter, not a delivered
offline Chat feature. Full-duplex and cloud realtime voice remain future adapters.

## Priority order — 2026-10-24

The owner requested major-impact or complex work first. Follow the [high-impact backlog](docs/HIGH_IMPACT_BACKLOG_2026-10-24.md): reliable model/media chat first, physical multi-device execution/recovery next, then Internet relay/group chat, Studio agent parity and separately qualified operational/reward backends.

## Android and Studio workspace continuation — 2026-10-23

The [older Android Studio request audit](docs/OLD_ANDROID_STUDIO_REQUEST_AUDIT.md) now preserves untransferred requests as explicit gates. This iteration adds an embedded pairing radar, relay join-code countdown with automatic rotation, clearer direct/VPN/relay/web connection choices, narrower phone typography, responsive Settings shortcuts and direct Security Lab → VM/packages navigation. Rebuild, physical UI inspection, pairing and release checks still govern delivery.

[Detailed redesign and acceptance plan](docs/REDESIGN_PLAN_2026-10-23.md). Android source now has discovery Auto/On/Off, reviewed pairing, radar, device dashboard, menu ordering, Resources, Security & privacy, Chat target controls and bounded parallel agent prompt jobs. Studio source adds a saved-device dashboard, session revoke control and headless capability status. Current single-peer chat, Studio layer sharding and container/server daemon remain separate implementation gates. Validate the new Samsung APK, Xiaomi install, hostile enrollment/revocation and multi-device compute before a public release.

## Paused Android/desktop peering continuation — 2026-10-12

[Future build plan](docs/PEERING_UI_BUILD_PLAN.md) · [Exact handoff and validation](docs/PEERING_UI_HANDOFF_2026-10-12.md). Implementation is partial and paused at the owner's request; resume from the saved working tree. Both platforms are in scope. Android Studio is the reference, not the target.

## Rewards and responsiveness continuation — 2026-10-23

The owner resumed Android and Studio review. Local signed receipts remain the only implemented reward path; historical idle/referral bonuses require trusted host evidence and abuse controls before implementation. The current source reduces repeated receipt verification and long-reply redraw work. Next gate: collect baseline and changed timings on a physical Android host and the installed Intel Studio build, then profile the remaining inference, startup and UI costs before claiming an end-to-end speedup. See [progress](PROGRESS.md).

## Pairing and compute continuation — 2026-10-23

Android worker QR and manual approval now query a live worker through certificate-pinned TLS before storing it. Cluster groups may contain LAN and directly reachable Internet/VPN workers; automatic admission checks live offers, memory, thermal state and model fit at load. The new assessment score is an operational heuristic, not a measured speed or security grade. The existing HTTP membership relay still cannot carry native layer RPC; a bounded, authenticated reverse tunnel is required before built-in relay mode can serve workers behind NAT. Native ARM64 binaries are packaged in both debug APK flavors; two-phone execution remains a separate device gate. Studio adds bounded custom HyperL graph JSON on its verified native CPU backend; GPU/NPU and broader Android feature parity require individual backends and qualification.

## Reward metering and monetary boundary — 2026-10-23

[Reward metering and settlement plan](docs/REWARD_METERING_AND_SETTLEMENT.md) defines native usage units for inference, layers, compute, storage, relay and availability, then reciprocal signed evidence, fraud checks, offline reconciliation and a balanced settlement journal. Existing self-signed local receipts remain nontransferable. Purchases, rental, payouts and exchange require independently verified service, jurisdiction-specific legal review and a qualified payment path; no conversion rate or spendable balance is implemented.


Updated 2026-10-10. [Current progress](PROGRESS.md), [sequential gates](BUILD_MILESTONES.md), [requests](REQUESTS.md), [open work](TODO.md) and [documentation index](docs/DOCUMENTATION_STATUS.md) share the current tracking model. Older plans are retained under docs/history.

## Goals and channels

Build a capable private AI workspace for desktop/server and Android, with real local inference, permitted tools, owner-approved heterogeneous devices and genuine model-layer execution. Production candidates and Experimental features stay separate. The Android Core Candidate is a restricted candidate, not a production certification. Intel Studio is Experimental; a dashboard or library dependency never establishes a working backend.

No single project-wide phase number or completion percentage is used. The workstreams below have separate implementation and qualification gates. Historical phase numbers identify their original design snapshots only.

| Workstream | Current state | Next gate |
| --- | --- | --- |
| Local core engine | Intel bundled Qwen/CPU baseline and verified AVX2; lazy load, context/thread/batch/KV controls, memory admission and idle unload; both recovered installer launchers pass | Clean-machine installation, long-run lifecycle/leak/crash/quality cases; wider model and hardware qualification |
| Shared sharding | Portable GGUF and CPU worker admission shared by Android/desktop; capacity-bounded integer/manual layer proportions and weak-worker exclusion pass contracts | Desktop live-worker RPC controller, paired independent devices, measured allocation/latency/memory, oversized-model and peer-loss tests |
| Desktop management | Seven categories, distinct Basic/Advanced menus, bounded global/settings/chat/model/node search, real local monitoring, human shell, SSH/status listener and HyperL CPU | HF download/cancel/storage failures, actual process Stop, interactive SSH; finish pending category backends |
| Android reliability | Build44 passed its scoped gates; build45 source is saved with final build/lint and physical voice/RAG checks incomplete | Resume only on owner instruction, then qualify final source on available Samsung; preserve app data and phone ownership |
| Governed agents and fleet | Android typed controls/grants/audit and optional gateways are references; desktop shared agent dispatch remains pending | One common authority/controller layer, durable redacted audit, scopes/budgets/revocation/Stop, then MCP/A2A/hooks and controlled automation |
| Platform and release | Intel DMG/PKG recovered-payload qualification; source/review pushed; all eight public asset digests verified | Experimental release published with matching digests; Developer ID/notarization and independent OS/device acceptance precede production |

## Proposed stabilization priority — awaiting implementation decision

The owner selected detailed plan refinement before implementation. The [stabilization plan](docs/desktop/STABILIZATION_PLAN.md) defines S0–S6 acceptance gates; the [build guide](docs/desktop/STABILIZATION_BUILD_GUIDE.md) maps existing commands to evidence and identifies missing future harnesses. This documentation does not promote any runtime capability.

1. S0: freeze source, inputs, device inventory and repeatable baseline measurements.
2. S1–S3: durable offline chat/engine lifecycle, reliable model import/download and accurate monitoring with real process-control acceptance.
3. S4: shared action authority, protected credential references, durable audit and revocation; extend the session key-provider boundary introduced in S1.
4. S5: pair two independent compatible physical devices, execute positive model-layer contributions on both, measure capacity/performance and qualify safe worker-loss failure/retry.
5. S6: make separate local-desktop and cluster-Experimental release decisions, and admit bounded agent adapters only for already qualified actions. Local release assessment may proceed after S4; cluster acceptance requires S5.

Broader agent workflows, voice/personality, RAG, VM/container/Kubernetes, OS firewall/capture, cloud billing, new accelerators/platforms and transparent cluster failover remain in the existing backlog. The detailed plan defines stop conditions, device dependencies and evidence requirements without promising a calendar deadline or production certification.

## Core distributed acceptance

Require at least two independent approved workers, positive model-layer contribution, exact model/revision agreement, actual native allocation and output, and a single-device comparison. Measure first-token latency, authoritative native tokens, transfer bytes, resident/KV memory, thermal and battery where supported. Internet/P2P controls are separate from native model transport. Do not sum nominal RAM or device counts into a throughput estimate.

Replicated journal acknowledgements, fencing/quorum, idempotent claims and portable compatible KV recovery must precede automatic coordinator failover. Existing local encrypted checkpoints and retries do not establish fleet self-healing. Training needs a real licensed dataset, actual host job and evaluated adapter; a file import is not successful training.

## Update rule

For every change record request/feature IDs, source, commands and failures, model/device/backend, actual observations, remaining gates and artifacts/digests. Update the appropriate workstream, not every historical phase. Run scripts/update-doc-tracking.py --check and scripts/validate-feature-map.py before committing documentation. All 41 desktop/server requested areas remain individually tracked in docs/DESKTOP_FEATURE_TRACKER.md and docs/desktop-features.json.

## Prerelease continuation — 2026-10-26

Prepare Android and Intel macOS packages from validated source; build Windows/Linux Studio clients on their native CI runners. Preserve unavailable local runtime states and platform qualification limits. Publish an experimental prerelease only with successful artifacts and checksums.


## Optional DeepSeek Harness integration — 2026-10-30

The hosted workspace is implemented for Code OSS Studio and experimental Android.
The [H1–H5 integration plan](docs/architecture/deepseek-harness.md) separates this
usable upstream UI from future shared model/usage admission, scoped paired tools,
native mobile task controls and release qualification. The pinned private-home
launcher disables upstream session-log uploads and telemetry. Harness jobs retain
their own accounts and permission policy; disconnecting is not cancellation.

## Broader model formats — 2026-11-06

The owner requests more than the current GGUF bundle. Standalone HyperL is adding
a versioned format inspector and narrow SafeTensors conversion into its existing
JAX decoder. ONNX, LiteRT, Core ML and further adapters have separate execution
gates. Studio import/model-picker integration remains pending; do not label a file
runnable solely because its extension is recognized. Canonical plan:
[HyperL format support](https://github.com/sabbirimon/HyperL/blob/3de097c9ce2cdd9b118c23047dae4bfb317cf768/docs/MODEL_FORMATS_2026-11-06.md).

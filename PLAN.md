# Meshlit implementation and qualification plan
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](PLAN.md) · [Progress](PROGRESS.md) · [Document status](docs/DOCUMENTATION_STATUS.md).
Scope: cross-platform tracking; feature and device gates remain separate. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

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

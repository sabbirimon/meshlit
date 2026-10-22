# Meshlit implementation and qualification plan
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](PLAN.md) · [Progress](PROGRESS.md) · [Document status](docs/DOCUMENTATION_STATUS.md).
Scope: cross-platform tracking; feature and device gates remain separate. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

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
| Android reliability | Both current Full V1/V2 APK assemblies/lints pass; earlier Samsung build-37 local/UI checks are historical | Install and test current build on Samsung, then Xiaomi/Windows categories; preserve data and human permissions |
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

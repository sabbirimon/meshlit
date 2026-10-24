# Meshlit workspace redesign and build plan
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Status: active plan, 2026-10-23. This describes source-backed work and acceptance gates; it is not a claim that every feature is released.

## Current baseline

The Android workspace has separate screens for Models, Agent management, Agent Gateway/MCP, Hooks, Network, Logs and Chat. The model library handles real GGUF import/download/load; the model router runs up to three steps sequentially. `AgentCommandController` persists typed jobs, admits at most eight nonterminal jobs, and executes at most two at a time. The native local inference context serializes calls. An approved online profile may execute concurrently. Hooks are user-authored lifecycle scripts with a separate master switch. P2P text chat supports one foreground, manually paired WebRTC peer; relay membership is separate. Worker pairing uses pinned TLS and a private token.

## Product structure

| Destination | First screen | Detailed controls |
| --- | --- | --- |
| Chat | Current target, loaded model, connected peer state, Stop | Local/cluster/online/route choice; independent agent tool grants |
| Models | Installed/downloads, live memory and compatibility | Import, verified download, runtime options, sharding eligibility, route recipes |
| Agents | Independent job cards, queue/running count, Stop | 2–4 parallel task composer, delegated scopes, per-job output and cancellation |
| Devices | Observed vs saved vs approved vs live | Radar, identity preview, groups, metrics, revoke, worker and relay stop |
| Security & privacy | Discovery AUTO/ON/OFF, grant summary | Permissions, guardrails, identity, network, audit, logs, legal |
| Tools & automation | MCP registry and hook status | Gateway routes, per-tool scope, script editor, operation switches |

The drawer and Settings use the same user-ordered shortcut list. Basic covers everyday tasks, Advanced covers optional runtimes/integrations, and search indexes both. Every destination must open a working screen or show a factual unavailable state.

## Build sequence

1. **Make the existing paths coherent.** Add Device radar and management dashboard, direct Mesh chat entry, Security & privacy, shared shortcut editing and Chat target controls. Preserve distinct trust scopes. Default device discovery to AUTO while the app is visible, with saved ON/OFF and separately delegated agent changes.
2. **Secure enrollment and revocation.** A discovered advertisement stays unverified. Preview owner QR endpoint and full fingerprint before saving; a live challenge and completed handshake plus peer owner approval are required before compute trust. Failure cannot mark a device trusted. Dashboard revocation removes the local endpoint/session/trust; stopping relay, worker or pipeline reports the actual stopped component.
3. **Expose real device metrics.** Refresh pinned worker offers on demand, show observation age, online duration only when a start time is reported, available RAM/storage, CPU threads, thermal status, control RTT, estimated layer budget and admission risk. Saved devices and advertisements have unknown live capacity. Relay member join time and heartbeat age are separate from compute metrics.
4. **Parallel agent jobs.** Offer a bounded 2–4 prompt composer over the durable typed-job controller. Keep two execution slots, a queue limit of eight, independent IDs/results/Stop, provider agent opt-in and Models delegation. Local model inference remains serialized. Do not present queued work as simultaneous token generation.
5. **Models and routing clarity.** Add a capability/status view per model from real GGUF metadata and device plan, show load rejection reasons, installed-vs-loaded state and native worker prerequisites. Distinguish sequential model recipes from layer sharding and from multiple independent agent jobs. Avoid invented model benchmarks or paid-provider access.
6. **MCP and Hooks workspace.** Bring existing registry, Gateway and Hooks into one entry with links to their actual controls. Show tool origin, saved scope, enabled state and last real result where available; user-created hooks remain reviewable and off unless enabled. Never auto-run retrieved content or let an agent edit human grants through a tool description.
7. **Mesh chat next milestone.** Implement multi-member rooms only with an authenticated group protocol, independent per-device keys, real delivery/ack status, bounded encrypted message storage, revocation and offline queue semantics. Until then, label the current single live WebRTC conversation clearly. No screen should imply that a membership relay transports chat messages.

## Acceptance gates

- Both V1 and V2 assemble and lint with zero errors; relevant core/app unit tests pass. Run doc tracking, feature-map and HyperL licence/provenance checks.
- Samsung physical UI test: Basic/Advanced, drawer shortcuts, Security & privacy, radar, dashboard, Chat route settings, Agents, Models, MCP and Hooks open without crashes. Test a real model load and short generation; record failures separately.
- Pairing test: malformed/manual/no-key QR stays untrusted; mismatched identity, denied handshake and failed connection never set `trusted=true`; owner approval is visible. Test revoke and stop on physical devices without erasing unrelated app data.
- Multi-device acceptance needs two mutually reachable approved devices for direct chat and three devices for the current two-worker layer plan. Record actual route, worker offers, tokens and stop/recovery behavior. A build, emulator or one-phone UI test does not establish this gate.
- Update the offline illustrated guide and repository guide together. The public APK must be tested with Play Protect and required permissions reviewed before publishing release assets.

## Studio, server and container alignment

Studio now exposes saved device addresses and their last verified contact in a Device dashboard, local resource readings, Security & privacy with a session-access revoke action, and the existing model/host/guardrail/HyperL controls. Its CLI `--status` prints machine-readable *local host-client* capability status for operators running it on a server or inside a container. It does not start a server daemon or a container runtime. Saved addresses are not live worker membership. Nearby radio discovery, multi-member Mesh chat, layer sharding, agent jobs, MCP Gateway and Hooks still need real desktop/server implementations and qualification; keep those destinations marked pending.

## Cross-device security invariants

No advertisement, acoustic message, saved API address, user-defined label or agent-generated text grants device trust or command authority. Enrollment starts pending; a human must approve access scopes. The TLS control bridge accepts bounded JSON requests (64 KiB), at most four concurrent requests and a five-second handler deadline. An approved token is bound to one device entry, its scope is checked for each operation, agent delegation is checked again by the backend, and revocation cancels its pending jobs. Native worker trust uses its separate pinned TLS handshake; a failed handshake must not set `trusted=true`. Acoustic command frames remain short, unverified text and are never executed. Studio remote tokens are session-only and its host client bounds response bodies and prompt size. The Android reward ledger is size-bounded before and during reads and verifies signatures on load. These are source/test controls, not proof against a hostile physical network; conduct a two-device hostile enrollment/revocation test before release.
# Protected actions and weekly review (requested 2026-10-23)

Payments, purchases, banking, passkey enrollment/use, credential changes and account recovery require a separate protected-action approval at the moment of action, plus a visible target and audit record. A general Accessibility or agent app allowlist must never imply this approval. The current build has no payment or passkey execution adapter; the dialer opens a human-controlled dial screen and other agent capabilities have separate grants. Automated UI control cannot reliably classify every third-party payment or passkey flow, so do not describe it as safe full-phone control. A weekly security-review reminder should show active device/agent grants and recent reports, with explicit notification opt-in and a way to pause reminders. This reminder is not implemented in the current APK.

# Knowledge connectors and device audit (requested 2026-10-23)

The implemented one-shot device report is read-only and redacted; an agent needs the new AUDIT delegation. Saved local articles/search can index UTF-8 text or exported CSV after user file selection, but native Excel workbook connectors, call/message knowledge import, persistent vector RAG and remote/cluster RAG are pending. Phone dialer/SMS and Accessibility actions remain separate capabilities, not part of RAG. Background automation needs a visible running service, bounded task scope, app allowlist, per-action grant, stop and revocation checks. Full device takeover is not an authorized operating mode.
# Enterprise hardware naming

The proposed future enterprise **data center hardware** line is **MeshMinds** or **Meshlit Minds**. Keep the current Meshlit Android and Studio software name while the data center hardware branding and capabilities are designed and validated.

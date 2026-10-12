# Desktop stabilization milestone — proposed plan
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-10. **Planning only; implementation of this milestone has not started.** The owner requested a detailed refinement before implementation. Existing features and evidence keep their recorded status. [Build guide](STABILIZATION_BUILD_GUIDE.md) · [Root plan](../../PLAN.md) · [Current backlog](../../TODO.md).

## Outcome and scope

Deliver a dependable Intel desktop daily-use preview, then a separately qualified two-device Experimental inference path. Keep Android working as a peer and as a standalone app. The milestone does not promise production certification, universal hardware support, combined GPU/NPU execution or faster inference from adding a device.

The current baseline is source 8007783, with released Intel application payloads from 12bd20a. It has real local generation, CPU controls, monitoring and a shared placement planner. Desktop chat history is currently in memory; download resume, desktop worker execution and a common agent authority/audit bridge are incomplete. Existing installer smoke evidence does not satisfy the new soak, clean-machine or physical-cluster gates below.

## Delivery order

| Gate | Deliverable | Depends on | Exit evidence |
| --- | --- | --- | --- |
| S0 | Reproducible baseline and acceptance fixtures | Available Intel host and verified inputs | Exact commit, input hashes, command logs, baseline timings and defect list |
| S1 | Durable offline chat and engine lifecycle | S0 | Saved-session recovery, real generation, Stop/reload and lifecycle soak |
| S2 | Reliable model library/import/download | S1 stable lifecycle | Verified artifact activation and real interruption/storage-failure handling |
| S3 | Accurate monitor and process controls | S0; integrate after S2 | Timestamped host samples, responsive charts and disposable-process Stop |
| S4 | Common action permissions, secret references and audit | S1–S3 controllers | Scope/expiry/revocation tests and restart-safe redacted action records |
| S5 | Trusted two-device model execution | S2, S4, two compatible physical devices | Both devices contribute layers; memory/output/timing and peer-loss evidence |
| S6 | Release acceptance and bounded agent use | S1–S5, with separate local/cluster release decisions | Packaged UI workflows, clean-target results, qualified action adapters and rollback evidence |

These IDs belong to this milestone only. They do not renumber historical phases. S4 establishes the minimum authority foundation before remote jobs; broader agent workflows are admitted in S6 only for actions already qualified by humans.

## S0 — freeze a measurable baseline

- Record source SHA, dependency/native/model pins, OS/architecture, RAM/free disk, JDK/SDK, model context, CPU profile and sampling configuration.
- Run existing desktop contracts and actual packaged local/monitor/HyperL checks. Separate failures in application code from unavailable environment prerequisites.
- Use isolated test profiles and disposable data. Preserve the working installer and user conversations/models. Do not erase a phone to obtain a clean result.
- Record cold startup/load, first-token latency and end-to-end native token throughput separately. Five warm repetitions per comparison with identical model, prompt, context and CPU settings; report median, spread and temperature/power conditions where measurable.
- Agree fixture prompts and expected invariants before testing. Model quality cases are distinct from transport/lifecycle correctness; one arithmetic reply is not a general quality evaluation.

## S1 — offline chat that survives everyday use

Implementation slice: move conversation/request state out of the Compose screen into a lifecycle controller; add versioned local session storage and explicit completed/interrupted/failed turn states. Make session retention visible and user-controlled. Temporary sessions remain available. Persistent private content requires protected local keys/storage; implement the minimal macOS key-provider boundary in S1 and reuse it for S4 credentials. Never claim encrypted storage before recovery and key-lock behavior work.

Acceptance:

- Bundled model loads and replies with external networking unavailable. No network/provider fallback is introduced by an engine failure.
- A saved multi-turn chat survives app restart, OS restart and a crash during a reply. Completed turns remain intact; interrupted text is labelled and excluded from later context unless explicitly reused.
- Switching model/host cannot send a prior session to a new recipient without a visible user decision. Delete/export operations affect the selected session and preserve other sessions.
- Stop works during hashing/loading/generation; subsequent Load and Send work. Switching model during load cannot attach a stale process or reply.
- Run 20 load/generate/stop-or-unload cycles and a two-hour mixed idle/chat soak. No orphan model process/listener, lost completed turn, crash or steadily accumulating per-cycle retained memory after warm-up. Record raw memory data and investigate growth rather than masking it with a reset.
- Long formatted replies, context overflow, corrupt model and low-memory refusal have usable error/retry behavior. Unknown token usage remains unknown.

Proposed timing targets on the recorded Intel baseline: Stop acknowledgment within one second and owned engine termination within ten seconds. These are future acceptance targets, not current measured claims.

## S2 — model operations that finish correctly

Implementation slice: one model/download controller shared by the library and engine. Explicit queued/downloading/verifying/ready/failed/cancelled states; completed GGUFs become active only after immutable revision, full size/hash and native compatibility admission. Imported files retain their location and content identity.

Acceptance matrix:

| Scenario | Required result |
| --- | --- |
| Verified bundled and imported GGUF | Load, real reply and unload; no needless duplicate weight copy |
| HTML/LFS pointer, truncated GGUF, hash mismatch, unsupported architecture | Clear rejection; no ready badge or damaged replacement |
| Same filename with different content; file moved/changed after import | Distinct identity or explicit revalidation failure |
| Network loss and app restart during transfer | Recoverable partial transfer; never activate incomplete data |
| Resume with supported Range and unchanged ETag/revision | Correct offset and final full checksum |
| Changed validator or server ignores Range | Restart safely; never append a full file to a partial file |
| Cancel, read-only destination, full disk, missing source | Prompt cancellation/error, accurate state and bounded cleanup |
| Model in use during remove/delete | Protect active references; distinguish removing a reference from deleting user-owned weights |

Test one real public pinned HF download and one local import end to end. Exercise disk failures in a bounded disposable filesystem, not by filling the user's main disk. Gated/paid sources and provider-native library APIs remain later work; absence of credentials must be clear.

## S3 — monitoring that can be trusted

Retain the existing two-second sampler, bounded history and process table. Give each reading an observation time; mark stale after three missed sample intervals and show unknown rather than a fabricated zero. Match CPU normalization and memory definitions when comparing with OS tools.

Run monitoring throughout the two-hour S1 soak, including a known CPU/memory workload and repeated panel navigation. Sampling must stay off the UI thread; history/rows remain bounded. Compare aligned samples with an OS reference and explain differences. Check filtering, sorting, PID reuse and denied access.

Use an explicitly launched disposable same-user process for the real Stop test. Verify PID/start identity, confirmation, actual exit and the next sample. Do not test by killing unrelated applications. Remote telemetry must label device and freshness. Electricity, battery, temperature or GPU values remain unavailable where no qualified sensor exists.

## S4 — shared permissions and audit before autonomous work

Create a common controller boundary for human UI, agent, hook and MCP callers. Each action checks actor, target, operation, scope, expiry and budget; incoming model/tool/peer content cannot grant itself access. Preserve human-only credential/trust/policy changes and OS elevation.

Store credentials through a qualified OS secret provider; pass opaque references to actions and bind them to the intended host/purpose. Initial Intel implementation targets macOS Keychain. Other platforms report unavailable until their secret-provider adapter is qualified. Locked/unavailable storage must not fall back to plaintext. Do not put secrets in argv, transcripts or audit exports.

Persist redacted action intent, identity, result, time and cancellation/revocation state. Recovery reconciles incomplete work without automatically replaying side effects. State-changing automated actions must fail closed if mandatory audit recording cannot succeed.

Required negative cases: wrong host/scope/actor, expired grant, revoked active action, budget exhaustion, restart with stale authority, secret-store lock, audit disk failure and forged peer result. Revocation must cancel active owned work and prohibit subsequent steps; already completed external effects require an explicit compensation path, not an invented rollback.

## S5 — two physical devices, measured execution

First target pair: this Intel Mac plus the Samsung Galaxy A20s over an owner-approved LAN, conditional on actual CPU/ABI/model compatibility and free memory. Both must execute a positive portion of the same model; the coordinator may count as one contributor only when native evidence shows it executes layers. An API client forwarding an entire request to one host does not pass.

1. Inventory OS/ABI/RAM, native revision, model hash, available ports and connection type. Pair identities visibly and revoke them through S4. SSH login alone is not worker enrollment.
2. Add the desktop worker/coordinator transport around the existing planner. Carry typed job IDs, exact artifact identity, deadlines, cancellation and authenticated status; do not expose unauthenticated native RPC directly to LAN/Internet.
3. Run a small compatible model first. Record actual per-device placement/allocation and outputs. Then test a model/configuration that exceeds every participating device's standalone admitted budget but fits the validated split. A configuration that fits entirely on the Mac cannot prove an aggregate-capacity gain. If available hardware cannot support this stronger capacity case, record that subgate as blocked and do not claim pooled-memory qualification.
4. Compare one-device and two-device executions using the S0 protocol. Report first-token latency, native throughput, memory per device and transfer bytes. A slower distributed result may still prove capacity; it cannot support a speedup claim.
5. Disconnect a worker, suspend the phone, revoke trust and exceed a request deadline. The job must terminate visibly, preserve completed chat, cancel owned work and permit an explicit clean retry after revalidation.

Initial recovery means safe failure and explicit restart from a known completed turn. Transparent continuation, replicated KV, coordinator failover and automatic fleet repair are separate later milestones requiring replicated journal/fencing evidence.

The old Windows PC and Xiaomi phone are follow-up categories after obtaining their model/OS/ABI/RAM details. No HarmonyOS NEXT device is currently available. A second process on the Mac can test protocols but cannot substitute for this gate. Lack of devices leaves S5 blocked while other gates can continue; never lower the gate to a simulation.

## S6 — two release decisions and bounded automation

**Local desktop stabilization preview:** S0–S4, actual DMG and PKG installation workflows, persisted-chat/model/monitor scenarios from the installed app, regression checks and one independent clean Intel target. Package evidence stays separate from source tests. Signing/notarization remain necessary before a production claim; missing credentials or hardware are named blockers.

**Cluster Experimental preview:** additionally requires S5. Enable only qualified model/monitor/paired-job actions through typed agent/MCP/hook adapters. Verify human and agent callers reach the same controllers, with separate authority. Do not expose arbitrary shell or process termination merely because inference works.

For both: retain one previous working installer, define backward-compatible data migration or a checked restore path, verify release asset digests and document known issues. Use PRs with the five required CI checks and resolved conversations. Do not merge or publish a new build during this planning task.

## Deferred scope and stop conditions

Keep new platforms, GPU/NPU backends, VM/container/Kubernetes, packet capture/OS firewall, cloud billing, full autonomous PC control, voice/personality, RAG, fine-tuning and transparent cluster self-healing in the existing backlog. This milestone can preserve existing interfaces without implementing all of those products.

Stop promotion on data loss, secret exposure, unauthorized side effects, an unbounded operation or orphan inference process, corrupt model activation, reproducible core crashes, or unsupported performance claims. Ordinary model answer mistakes are tracked through the quality suite and must not be confused with transport failures.

No calendar deadline is promised before S0 and hardware availability are known. Estimate each slice after its dependencies are verified. Track statuses as planned, implementing, blocked, acceptance-failed or acceptance-passed; attach source/build/model/device/command/result evidence for every passed gate. Update PLAN, TODO, PROGRESS and the phase ledger at each gate.

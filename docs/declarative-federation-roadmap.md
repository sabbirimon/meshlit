# Declarative deployment and federated clusters
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-06. User-authorized direction: Android first, phone-first genuine
layer execution, human and autonomous-agent operations, mixed devices, portable
configuration and multiple clusters exchanging tasks over authorized internet
connections. This document is a build plan, not a claim that federation or vendor
accelerators already execute workloads.

## Current deployable boundary

`ConfigurationTransfer` imports/exports strict JSON schema v1, previews changes,
preflights local device constraints, and applies supported settings. It is suitable
for copying a reviewed baseline to another device. Keys, enrollment trust,
autonomy/root consent and agent scopes do not travel in that file. JSON is data;
imports never execute hooks, shell, Terraform or Pulumi programs.

Current cluster negotiation gathers signed/pinned approved RPC offers, plans a
coordinator and memory-weighted CPU layer workers, and launches native llama.cpp
execution. Host two-worker evidence is recorded separately from physical-phone
and oversized-model acceptance. Device groups and role plans do not implement
consensus or a replicated task log. SSH command execution is real, while automated
SSH worker deployment remains planned. Web/API enrollment is real, but does not
make a browser or switch a compute node.

## Desired-state contract, following the IaC model

Introduce schema v2 only when the following resources have real apply/read/delete
implementations. Unknown resources must be rejected, not silently ignored.

| Resource | Identity and desired state | Actual observations / authority |
| --- | --- | --- |
| Device | Stable public-key ID, label, OS/ABI, requested role set | Approved identity plus signed/time-bound capability evidence |
| Cluster | Cluster ID, membership, namespace and deployment policy | Cluster trust root, membership epoch, ready members |
| ModelArtifact | Immutable revision, GGUF hash, file size, license acknowledgment | Verified content hash; installed/partial/unavailable status |
| ModelRuntime | Backend, context, KV precision, threading and memory ceiling | Loaded artifact, effective native settings and observed limits |
| StorageHolder | Namespace, quota, replication factor, artifact retention | Real free space, committed object hashes, health/heartbeat |
| Agent | Model policy, tool scopes, cloud opt-in, per-host commands | Owner-signed delegation, token/energy budget and revocation |
| Task | Idempotency ID, executable typed command, limits and priority | Durable lease, attempt, state, output/checkpoint references |
| Network | Approved transports/endpoints, certificate pins, port policy | Reachability/auth status; no implicit NAT exposure |
| Provider | API endpoint/model/template and pricing provenance | Secret references resolved locally; real API availability |
| BackendPlugin | Platform/ABI/runtime artifact hashes and operator support | Plugin installed, probe succeeded, real generation benchmark |

Expose `inspect`, `validate`, `plan`, `apply`, `status`, `cancel`, and explicit
`destroy` contracts. Planning compares desired and actual state and produces an
immutable plan hash, risks, resource admission and required permissions. Applying
must use that same plan revision and detect drift. Do not infer approval from a
stale plan. Deletes/revocations need explicit resource targets, not broad cleanup.
Add a CLI and an API adapter over the same typed core, then optional Ansible
module, Terraform provider and Pulumi component. Provider plugins must read
actual state, support import and explain changes requiring replacement. Never
claim a Terraform wrapper before an actual provider and acceptance tests exist.

## Phone-first memory and crash recovery

1. Replicate compact task/event journals across approved phones; large GGUF files,
   activation/KV checkpoints and datasets live on selected storage holders.
2. Journal entries include cluster/epoch/task/attempt, monotonic sequence, creator,
   payload digest, timestamps and authenticated acknowledgments. Encrypt data and
   separate storage permissions from execute permissions.
3. Use a reviewed consensus/lease design rather than ad-hoc automatic master
   election. Define quorum behavior for partitions, two-node ties and battery loss.
4. Checkpoint only at defined runtime-safe points. Record model hash, tokenizer,
   engine build, layer assignment, context/KV formats and generation position.
   Incompatible native KV snapshots must fail restoration explicitly.
5. On node loss, revoke/fence the old lease, inspect actual task side effects, and
   restore only compatible committed state. External effects may need human review
   or an idempotent target API. Exactly-once external effects cannot be assumed.
6. Retain tombstones/revocation epochs to prevent a returning stale phone from
   reviving abandoned ownership or replaying commands.

Acceptance: power off one and two named physical devices during real generation;
record which committed tasks/checkpoints survive, duplicate prevention, recovery
time, memory/transfer/thermal behavior and any output divergence. Demonstrate a
model exceeding every individual device's measured usable RAM before claiming
large-model-across-phones success. Host proof alone does not meet this gate.

## Multiple clusters over the internet

Separate control and bulk data planes. Use approved VPN/private overlays first;
current private-IPv4 listener rules are not a WAN publish feature. A future broker
or relay uses authenticated TLS, device/cluster trust roots, certificate renewal,
per-namespace authorization, replay protection, request limits and encrypted blobs.
Enrollment is an invitation plus owner approval, not automatic trust from discovery.

Task handoff uses a durable outbox/inbox, deduplication ID, origin cluster/task ID,
expiry, maximum attempts, declared side-effect class, accepted delegation and a
lease/fencing token. Destination advertises capacity and must accept or reject
admission; an origin timeout does not prove rejection. Poll acknowledged status
before retrying. Cancellation propagates with an authenticated event and records
already-completed effects. Test duplicate deliveries, disconnect/reconnect,
clock skew, network partitions, lease expiry, revocation and corrupted objects.

Layer execution across internet links is a separate feasibility gate: measure
activation transfer, bandwidth, RTT, synchronization, privacy and memory overhead.
Prefer task-level federation unless the planner has real evidence that a particular
model split meets the required latency/energy budget. Phones remain primary; NAS
may store artifacts, routers/switches may provide connectivity/telemetry, servers
may contribute verified CPU/GPU capacity. These roles are independent.

## Hardware/plugin contract and major platforms

All backends use common request/result/cancellation and artifact interfaces; OS
permission, filesystem, secret store, process and USB drivers live in adapters.
Support probing returns one of observed, unavailable, unsupported or unverified
with evidence, OS/API/ABI, runtime build, device IDs, precision/operator limits,
usable memory and measured performance. A chipset name is not readiness.

- Android: RunAnywhere SDK and optional native CPU are implemented. Advertised
  OpenGL ES/Vulkan features are observations. Implement Vulkan/OpenCL/QNN/NPU
  adapters only with compatible native libraries and real model execution tests.
- Linux/Windows: future CPU, NVIDIA CUDA/TensorRT, AMD ROCm/HIP and Intel
  OpenVINO/oneAPI adapters; negotiate driver/runtime compatibility and licenses.
- macOS/iOS: future Metal/Core ML adapters; do not equate an Android build on a
  Mac emulator with a native macOS port.
- HarmonyOS/Huawei: future native OS integration plus CANN/HiAI/MindSpore Lite
  adapters appropriate to the actual device; Android compatibility is not assumed.
- Other vendor NPUs: RKNN, NeuroPilot, Samsung ENN and future accelerator plugin
  contracts. Do not download vendor drivers or grant root automatically.
- External devices: storage through SAF now; future serial, Ethernet, UVC cameras,
  sensors, FPGA/accelerator bridges and network storage adapters require actual
  driver ownership, detach/reconnect handling and resource accounting.

## Additional design ideas and acceptance gates

| Idea | Purpose | Required evidence before enabling |
| --- | --- | --- |
| Capability evidence / expiry | Stop scheduling to stale or invented hardware | Signed identity, actual probe and generation; refresh/revocation tests |
| Cost and energy budgets | Bound autonomous resource use | Reported provider usage, timestamped rates, per-call output limits; unknown costs cannot be treated as zero |
| Human override / emergency stop | Revoke tool/control authority predictably | Cancel owned work, acknowledge completed effects, tested OS/lifecycle behavior |
| Model canary and rollback | Prevent a download/update making the app unusable | Verified artifact, small actual generation, retain last-known-good model when space permits |
| Resource-pressure scheduler | Keep low-end phones responsive | Measured RAM/thermal/battery admission and real foreground responsiveness |
| Offline operation queue | Preserve intent through network loss | Durable encrypted queue, expiry/idempotency, reconciliation before retry |
| Diagnostics bundle | Make failures reproducible without leaking keys | Redacted export, version/config/artifact hashes, optional owner-selected traces |
| Plugin trust policy | Avoid executing arbitrary discovered binaries | Pinned manifest/hash, license review, sandbox boundaries and revocation |
| Tutorial/recovery drills | Explain model and cluster limits honestly | Replayable walkthrough with real states; demos explicitly isolated from production |
| Cluster topology and energy view | Explain compute/storage/routing roles | Actual live offers and metrics; no synthetic throughput or billing |

## Delivery sequence

1. Finish real bundled-model generation, both-flavor validation and device UI checks.
2. Validate current source features: downloads/import/load, local/native context,
   online adapters with owner credentials, USB/SSH/power and reviewed profiles.
3. Complete physical phone layer proof and oversized-model memory/thermal evidence.
4. Implement authenticated compact journals, leases and safe checkpoint recovery.
5. Build a real mixed-device companion and desired-state API; add IaC adapters.
6. Add internet task federation and controlled relays; test partitions/revocation.
7. Add platform/vendor runtime plugins one at a time behind actual capability proof.

Keep these steps in `PROGRESS.md` with named tests/devices. No unsupported overall
completion percentage, universal hardware support, automatic recovery or paid/free
account entitlement should be inferred from a plan, UI control or installed APK.

# Layer execution, heterogeneous devices and recovery
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

User direction, 2026-10-06: implement genuine layer/pipeline sharding, local
RunAnywhere inference by default, optional VM, a hive of mixed devices and
capability-based coordinator/worker negotiation. Historical documentation
prohibiting layers across phones is superseded. Tensor parallelism is separate.

## Current implementation

The pinned native llama.cpp coordinator uses `--split-mode layer`, `--rpc`,
explicit remote devices and negotiated tensor-split weights. Worker CPU backends
execute native transformer graphs. A complete GGUF stays on the coordinator's
disk; this implementation streams selected tensors to workers rather than
writing separately loadable GGUF layer fragments. The coordinator still uses
memory for embeddings, scheduling and some graph work. Do not claim zero local
weights, pooled RAM, or that every large model will fit.

Android native binaries are installed executable ELF files in nativeLibraryDir,
not downloaded code executed from writable app storage. Native GGML RPC binds
loopback. A TLS transport pins the worker certificate and authenticates a
separately approved pairing token before forwarding. This protects transport;
the upstream RPC interpreter is experimental and is not a hostile-code sandbox.

`NodeOffer` and `ClusterNegotiation` elect the highest-budget eligible coordinator
with a verified model identity, deterministic identity tie-breaking, runtime
revision matching, 30-second offer freshness, thermal exclusion, two-worker
minimum and conservative memory estimates. Device labels do not grant compute.
The app currently activates the local coordinator that owns the selected GGUF,
queries manually paired worker offers and derives split weights automatically.
Manual and QR worker approval both require a live pinned-TLS capability response;
the coordinator assigns each approved worker to a selected group. A group may
mix LAN and reachable Internet/VPN addresses. The worker QR contains a private
token; stopping the worker and rotating it revokes old QR credentials. The first
12 hex digits of the certificate pin are shown as a short visual identity code
for out-of-band comparison; that code alone neither discovers nor authorizes a
worker. An offline LAN, hotspot or preconfigured virtual network needs no relay.
A live
assessment reports the worker's offered resource fields and capability-query RTT,
with a heuristic operational risk score. RTT is not an inference benchmark or
bandwidth measurement. The existing HTTP membership relay does not transport
native layer RPC, so NAT-only workers require a VPN until an authenticated reverse
layer tunnel is implemented and tested.
Remote coordinator activation, shared election membership, leases, automatic
peer discovery, hardware reservation and transparent failover are NOT implemented.
Do not describe the current planner as a distributed consensus protocol.

## Phone-first shared memory

The primary hive is a phone cluster, not a server-centric deployment. Every
consenting enrolled phone should retain a bounded replicated task/session ledger:
job IDs, ownership/lease epochs, assignment state, completion offsets, model and
checkpoint references, compact replay state, integrity metadata and acknowledgements.
One designated phone may act as the memory-bank leader; all phones keep recoverable
replicas so that it is not a single point of failure. NAS/server storage is optional.

Selected artifact holders, including phones with sufficient storage, serve heavy
GGUF files, datasets and larger context/KV snapshots by content hash. Do not broadcast
full weights or large histories to every phone. Define replication factor, per-node
quota, retention/eviction, energy/thermal cost, transfer resumption and availability
requirements. The lightweight ledger references heavy content-addressed objects.
Use idempotent journal events and a fenced ownership protocol; do not resolve
conflicting active-task owners by last-write-wins timestamps alone. Checkpoint
commits need explicit acknowledgements before promising one/two-device recovery.

Required tests: kill the memory-bank phone, restore from another phone's committed
ledger, lose one artifact holder and fetch a verified replica, rejoin an old phone
without resurrecting finished jobs, and confirm bounded storage/network use on all
members. This durable phone-to-phone ledger is a requested next milestone, not
implemented by the current conversation file or worker-capability query.

## Enrollment without the Android app

Keep explicit enrollment methods and truthful availability. App QR/manual pairing
and native worker TLS pairing currently exist as separate paths; BLE discovery already exists; Bluetooth bonding
is not compute enrollment. SSH needs host-key pinning and a real authenticated
runtime/deployment connector. A web companion needs authenticated server-side
capability and storage APIs; a browser tab alone does not imply native compute.
NSD/Wi-Fi Aware/VPN discovery must not grant trust automatically. Separate states:
discovered, enrolled, approved, reachable, capability-verified, ready, degraded,
revoked. Revoke credentials and cancel assignments when a node leaves.

## Device capabilities

| Device | Eligible contribution | Evidence required |
|---|---|---|
| ARM64 Android phone | Local SDK inference, CPU RPC worker, coordinator | Native ABI matches, usable RAM, thermal/battery budget, user opt-in |
| x86_64 Android/emulator | Same after building its native ABI | Installed executable and a real generation test |
| ARMv7 phone | Existing compatible SDK features if supported | Separate native build needed for RPC; never launch ARM64 binary |
| Laptop/desktop/server | CPU worker/coordinator; GPU with separately built backend | Runtime version, backend enumeration, available RAM/VRAM and model support |
| Linux NAS | Storage and checkpoint service; compute only with installed matching worker | Filesystem capacity, authentication, CPU ABI and measured budget |
| Router/switch/firewall | Routing, scoped tools, monitoring; compute only if runtime actually installed | Owner enrollment, permissions, runtime probing; product name is insufficient |
| NPU/GPU hardware | Acceleration only through a working compiled driver/backend | Do not infer support from chipset brand or presence of an accelerator |

Snapdragon/Adreno, MediaTek/Mali, Exynos/Xclipse, Tensor and older ARM families
need runtime probes, not hardcoded performance promises. Start with portable
CPU execution. Benchmark optional Vulkan/OpenCL/CUDA/Metal backends independently.
Advertise precision, GGUF architecture, RPC revision, ABI, measured performance,
free memory, thermal/power state and permissions. Storage nodes must remain
eligible even when they cannot execute layers.

## Durable memory and recovery: required next milestone

A NAS is a durable checkpoint holder, not automatically extra addressable RAM.
A network cache can reduce local storage demand but cannot replace the resident
weights/KV budget of the current native worker. Kubernetes/Docker are lifecycle
references; they do not provide transformer KV restoration by themselves.

Implement a versioned `CheckpointStore` with local and authenticated remote
implementations, e.g. an S3-compatible NAS service. No automatic uploads without
user enrollment. Store model SHA-256, tokenizer identity, runtime revision,
context size, sampling parameters/seed, conversation/session ID, prompt/token
history, sequence position, assignment plan, checkpoint generation, lease epoch,
creation time and integrity digest. Encrypt sensitive state; keep recovery keys
outside the stored blob. Use bounded multipart upload, integrity verification,
atomic publication and retention limits. Replicate to at least two independent
storage failure domains if the recovery policy promises survival of two failures.

First provide checkpoint-and-replay: abandon a failed native context, elect a
fresh approved coordinator with a current lease, plan available workers, validate
model/runtime identity, restore acknowledged session history, rebuild context,
and continue. Mark any interruption visibly and avoid duplicate displayed tokens
using sequence offsets. Partial token streaming is not a committed checkpoint.
Retries must not replay external tool effects without idempotency keys and audit.

Direct KV-cache snapshots require native export/import that the current app does
not expose. Persist layout version, model/tokenizer hash, layer mapping, KV dtype,
position/RoPE settings, backend compatibility and per-layer checksum. Prove reload
on the same runtime first, then cross-device portability; reject mismatches. Do
not copy opaque GPU addresses or claim crash recovery from conversation JSON.

A master crash requires actual ownership coordination: lease duration, heartbeat,
fencing epoch and split-brain prevention. A worker crash requires cancelling the
current generation and building a fresh plan; no automatic whole-model fallback
onto a phone that cannot fit it. Losing storage quorum must surface degraded or
blocked recovery state rather than fabricated continuity.

## Acceptance evidence

1. Compare fixed-prompt/seed output on one native CPU with two RPC workers;
   record native per-device layer, model-buffer, KV and graph placement.
2. Run Android native worker/server on supported ABIs, then two physical phones
   with authenticated tunnels. Name devices, OS, ABI, model digest and parameters.
3. Demonstrate a model exceeding each compute phone's safe budget using only
   bounded local resident memory. Record RSS/PSS, worker peak memory, context/KV
   budget, throughput, transport latency, thermal behavior and cancellation.
4. Deny wrong token, changed certificate and incompatible runtime. Disable peer
   consent and verify removal. Kill a worker and confirm explicit failure.
5. For recovery, terminate coordinator and one/two workers; restore from committed
   remote checkpoint under a new lease. Verify offsets, no duplicated tool side
   effects, no stale-master writes, corrupted checkpoint rejection and retention.

Desktop two-worker output equivalence is demonstrated in this checkout; physical
phone, oversized-model, remote checkpoint and automatic failover acceptance
remain pending. Consult the handoff validation record for exact results.

## Typed command admission and local recovery
`core-mcp/control/AgentCommands.kt` and `app/control/AgentBackend.kt` add a shared
versioned command/job interface: appearance settings, model list/download/import/load/
unload/delete, cluster status/planning/start/stop and recovery status. Agents submit
stable request IDs, poll jobs, cancel and explicitly retry with a new ID. Jobs are
persisted encrypted before execution. A process restart marks nonterminal entries
INTERRUPTED and does not replay uncertain actions automatically. Saved scopes are
checked at execution. Agents cannot expand their own grants. Existing human screens
and this facade use the same underlying repositories/coordinator/cluster host.

This is a working local job journal, not a replicated phone ledger or portable KV
checkpoint system. `RECOVERY_STATUS` reports those capabilities separately as false.
Imported model URIs must already carry a persisted Android read grant; arbitrary
filesystem paths and new permission grants are not accepted by this facade.

## General nodes, IoT and current priority

Phone-first genuine model layers remain primary. Raspberry Pi/Linux compute
requires a matching real runtime/ABI and measured memory. ESP32/Arduino nodes may
contribute sensors, actions, radio gateways, preprocessing or monitoring; they
are not layer workers merely because they enroll. Current enrollment supports
RASPBERRY_PI/MICROCONTROLLER/SENSOR/RADIO categories and requested non-LLM roles,
with owner approval separate from compute readiness.
Read `media-iot-and-radio-nodes.md` for media, DSP and owner-paired two-way radio
requirements. Read `declarative-federation-roadmap.md` for desired-state deployment
and cross-cluster task leases. These adapters and distributed recovery are plans,
not evidence of execution. SSH exec now uses pinned JSch host keys; automatic
remote worker install/enrollment remains planned.

# Meshlit implementation plan

Previous upstream records are preserved in `docs/history/PLAN-before-2026-10-06.md`; their old build/device claims are historical, not validation of this checkout.
Updated: 2026-10-09. Follow `AGENTS.md` and `AGENT_BUILD.md` before implementation.
`PROGRESS.md` is the evidence ledger; `BUILD.md` contains reproducible commands.
User requirements outrank historical restrictions on layer/pipeline sharding.

## Build 40 continuation

Studio appearance, English/简体中文 contracts, the desktop client, native NEXT
source project, Colibri host selection, actual ggwave audio, local cryptography
and foreground paired WebRTC/SSH command paths are implemented. Keep these
experimental adapters separate from Android Core Candidate and the current
local chat engine. See the four adapter guides and the current evidence ledger.

- Finish final source packaging/lint and inspect the actual desktop renderer.
- Reconnect the Samsung through an owner-started USB ADB server; preserve app
  data, require human acceptance of changed notices, and test current UI/native
  codec, foreground audio and Stop/revocation. PCM round trips do not establish
  speaker-to-microphone delivery.
- Pair Samsung/Xiaomi on LAN and then distinct Internet networks; verify actual
  direct/relay routes, replay rejection, receiver grants and cancellation. A
  WebRTC chat channel is separate from native model-layer transport.
- Test a licensed compatible Colibri model on an explicitly selected host;
  registry tests and HTTP fixtures do not establish real model generation.
- Build the native NEXT 26.0.0 project using DevEco/SDK and test a NEXT device
  before claiming HAP, native-runtime or UI parity. Qualify Windows/Linux clients
  separately and retain unknown token usage when the host does not report it.
- Preserve the production priorities below: local Android reliability, genuine
  multi-device inference/recovery, then governed Internet/heterogeneous fleets.

## Priority 1 — reliable Android foundation
- Finish both-flavor compilation, unit tests, lint and emulator UI inspection.
- Validate GGUF download/resume/import/hash, actual SDK load/unload and generation on physical phones.
- Exercise Settings search/Basic/Advanced, persisted dynamic colors, log export and lifecycle cancellation.
- Validate encrypted OpenClaw gateway profile and opt-in phone model endpoint.
- Prove accessibility actions in an ordinary installed APK, not only instrumentation.
- Expose clear model/runtime/permission/error state; no successful stub results.

## Priority 2 — real phone layer execution
- Enroll at least two physical Android workers with TLS pins and explicit grants.
- Compare deterministic output to a single-device baseline with the same model SHA and runtime revision.
- Record nonzero layer/tensor allocation on each worker and coordinator memory.
- Demonstrate a model larger than the coordinator's feasible local RAM budget.
- Measure throughput, first-token latency, network volume, thermal throttling and battery on heterogeneous phones.
- Stop/kill worker, coordinator and service; verify bounded failure and cancellation.
- Separate layer/pipeline placement from tensor parallelism and independent job distribution.

## Priority 3 — hive durability and autonomy
- Replicate a bounded task/compact-memory journal on every enrolled phone; NAS optional.
- Use selected holders for heavy model files/checkpoints with hashes, quotas and leases.
- Add fencing epochs, authenticated heartbeats, quorum-aware election and idempotent task claims.
- Recover by replay first. Portable KV recovery requires genuine native export/import and compatibility checks.
- Integrate OpenClaw paired operator + node WebSocket sessions and persisted device identities.
- Expose delegated Android controls through an authenticated gateway bridge with revocation and audit.
- Add model-appropriate structured function calling; do not advertise it until actual model tests pass.
- Validate emergency stop, target scope and protected/password surfaces. Accessibility grants do not confer root.

## Priority 4 — devices, transports and platforms
- Improve QR/manual/BLE enrollment UX, distinguish discovery from authorization and compute capability.
- Validate implemented TLS web/API enrollment and local groups; implement verified SSH enrollment adapters; routers/switches/firewalls may contribute connectivity only.
- Optional rootless/Linux VM/desktop and terminal remain off until requested or delegated.
- Keep portable core DTOs and adapters for Linux, Windows, macOS and HarmonyOS.
- Probe and benchmark optional CUDA, HIP, SYCL, Metal, Vulkan, OpenCL, MUSA and CANN backends.
- Reserve adapters for Huawei/HarmonyOS, Hygon and other Chinese vendors without fabricated support badges.

## Acceptance and changes
Each milestone must name changed files, build/test commands, results and remaining device checks.
Do not estimate a whole-project percentage from source presence. Track acceptance cases instead.
Update this plan when the user changes scope; preserve previous evidence in `PROGRESS.md`.

## Later — user guide and replayable tutorial
Requested by the user after core/model/backend work. Add a first-run walkthrough and
Help → Replay tutorial. Cover permissions and denial recovery, model download/import/
startup load, chat/stop, QR pairing, layer workers versus discovery, phone groups,
OpenClaw gateway/node/provider boundaries, code workspace and log export. Keep a skip
option and resume points. Use simulated data only in a clearly marked demo mode; never
fake model tokens, worker health, system permission grants or successful execution.

## Current task-manager acceptance
- Validate manual task creation/edit/subtasks/search/filter/sort/bulk completion.
- Verify real human/agent job start, queue, Stop, results and explicit retry.
- Later: recurrence, dependencies, assignments, distributed claims and replicas.

## Latest continuation priorities (2026-10-06)

Keep genuine phone layer sharding/model execution and committed recovery as the
main product priority. Nodes also contribute storage, tools, capture, sensors,
actuation, preprocessing, DSP, routing and monitoring according to real evidence.
ESP32/Arduino/IoT membership does not imply transformer execution.
Read `docs/online-power-peripherals-and-configuration.md`,
`docs/declarative-federation-roadmap.md` and `docs/media-iot-and-radio-nodes.md`.
These record offline/online selection, free/public/gated model distinctions,
actual pricing provenance, power readings, hardware/vendor backend limits, open
source SSH, configuration transfer, media sources and owner-paired radio carriers.
Keep unsupported adapters visibly unavailable; never invent devices, samples,
throughput, tokens, costs, account entitlements or recovery success.

## Scenario routing and media continuation (2026-10-06)

Read `docs/model-router-and-media.md`. Scenario recipes, sequential chains/comparisons,
chat text attachments and explicit online vision/image/speech/video adapters are
wired in source. Validate real execution; paid provider media outputs and generic
music/sound/on-device diffusion/video remain separate gates. Do not substitute
text event counts for native tokens. Keep phone layer execution/recovery primary.

## Custom models and training continuation (2026-10-06)

Read [docs/local-model-behavior-and-training.md](docs/local-model-behavior-and-training.md). Custom local prompt behavior is default-off;
compatible custom weights are imported through Models. No toggle changes learned
refusals or hosted-provider rules. Settings → Fine-tuning drives the optional
Soup 0.75.0 POSIX host companion via pinned SSH, with real job/status/log/cancel
contracts. Android synthetic gradients are removed; phone autograd reports
unavailable. Actual Soup training, adapter quality/evaluation and GGUF deployment
remain acceptance gates. Keep phone inference layer sharding/recovery primary.

## Guide, file archives and autonomous browser continuation

Implemented source: offline illustrated guide/eight reading lessons, persisted UI
fonts/surfaces, bounded streaming ZIP/unzip and visible approved-origin local-model
browser sessions. Execution walkthroughs, physical large-storage tests, browser
real-site model evaluations and external Chrome/Samsung Internet automation remain
acceptance work. Read PROGRESS and docs/runanywhere-browser-and-llama.md.

## Audit follow-up

Local encrypted metadata/filter/export and optional OTLP/HTTP source are implemented;
validation is tracked in PROGRESS.md. Next: full adapter coverage, durable exporter
outbox, fleet query API, signed remote anchors, and operator-tested Grafana/Loki alert
paths. Fix vendor SDK telemetry opt-out with supported API evidence. Keep physical
phone sharding and replicated/fenced recovery the main product gates.

## Distribution and agreement continuation (2026-10-07)

The owner authorized GitHub beta assets, a separate Play review APK/AAB, and
first-use Terms/Privacy acceptance. Read docs/PLAY_DISTRIBUTION.md (relative to
repository root) and the public/offline policy copies. `playReview` is a restricted
review candidate; no Play approval/submission is claimed. Policies are versioned,
both checkboxes are mandatory, and app-owned SDK/bootstrap work waits for acceptance.
Optional permissions/telemetry remain independent. Preserve the original open-source
license rights and third-party notices. Production release signing must never fall
back to a debug certificate; owner keys remain external to source.

## Update and bounded repair roadmap

No general app auto-updater or autonomous self-repair service is implemented.
Existing resumable model downloads, durable local jobs, retry/cancel and encrypted
native CPU checkpoints must not be described as fleet self-healing. Future work:

1. Separate GitHub beta and Play update channels; compare real signed release
   versions, verify APK checksum and signing identity, and require Android install
   consent. Production Play builds should use Play's supported update flow. Never
   download and silently execute replacement code or weaken the signing boundary.
2. Use typed health states for model artifacts, runtime, storage, tools, transport,
   worker leases and checkpoints. Record actual errors and recovery outcomes.
3. Bound retries/backoff, prevent crash loops, respect battery/thermal/network
   policy, and expose Stop plus separate human/agent repair settings.
4. Allow reversible repairs (restart an opted-in adapter, resume an owned download,
   revalidate an artifact); request approval before deleting user data, rotating
   credentials, changing firewall policy, enabling root or resetting configuration.
5. Add fenced coordinator failover and compatible replicated task/checkpoint recovery
   before claiming cluster healing. Never auto-replay irreversible tools from an old
   journal. Validate update and repair failure cases on physical phones and the
   intended signed distribution before enabling unattended policies.

## Hardware access and Security Lab

Read docs/SECURITY_LAB_AND_HARDWARE_ACCESS.md. Owner-requested Blue Team/Red Team
profiles are planned around approved engagements, actual capability probes,
separate human/agent permission, bounded work, evidence and emergency stop.
Root/kernel access may enable additional diagnostics and isolation on compatible
hardware; it does not create missing KVM, radio or vendor GPU/NPU support. Keep
physical rooted-device/VM execution and broader security tooling as open gates.

## Optional distro and security-tool provisioning

Read docs/LINUX_DISTRIBUTIONS_AND_LAB_TOOLS.md. The owner requested Alpine, Kali,
Parrot, Ubuntu, licensed RHEL/custom environments and optional Metasploit/other
lab tools. Implement verified artifact provisioning, architecture/resource probes,
explicit disk persistence and approved networking before promising one-click
installation. Current QEMU snapshots discard guest changes and restricted networking
prevents general package downloads. Physical guest/tool execution remains unverified.

## Owner direction — 2026-10-08

Meshlit targets dynamic AI software across different environments: servers,
workstations, clusters, cloud, embedded and edge systems. Phones are one deployment
and human control client. Security, vision, auth, networking and lab tooling serve
AI-related workflows. Current APKs and lifecycle services remain Android-specific.

Follow `docs/CONTINUATION_2026_10_08.md` for local implementation and remaining areas
2–8, and `docs/PLATFORM_ADAPTER_PLAN.md` for qualified platform/SDK/memory/native
networking work. Direct hardware/driver/kernel/bare-metal execution requires a
trusted installed backend, exact OS/ABI/artifact proof, owner privilege grants,
isolation and cancellation. HFT-inspired latency is a measurement target for AI
communication; no physical NIC, RDMA, hardware timestamp or HFT qualification is
claimed from same-host HTTP RTT. Mixed devices prioritize stability; compatible
islands need measured total benefit before turbo activation.

HyperL began on `cdb06b6`, based on `77ac214`. The owner merged
its CPU/reference/source-emitter source through PR #1 at `851576b`; it remains
experimental within `core-gpu`. Standalone packaging, app workflows, native runtime
loaders and hardware/performance qualification remain future work. Open-source/free
research components take priority; vendor/runtime licenses remain distinct.
Physical-device tests are still paused. Live cloud/provider/SSH account tests
remain pending; local contract/MockWebServer results do not satisfy those gates.

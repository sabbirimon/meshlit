# Current architecture and evidence boundaries
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-07. Source commit `baa8bcd`, baseline
`ff0cd771c12f3daa63cb8fe45e1476245ab7b01b`. The upstream architecture text is
preserved at `../history/current-state-upstream-baseline.md`; its phase, module,
model, SSH and native-support assertions are historical.

## Product and runtime

Android Kotlin/Compose/Koin, two build flavors with the shared modern UI. The
structured inventory has 33 Gradle modules, 65 feature areas and 38 durable typed
command operations. `../../FEATURE_MAP.md`, `../feature-map.json` and
`../../PROGRESS.md` are the current navigation/implementation/evidence ledgers.
No percentage is inferred from feature counts.

On-device inference uses pinned RunAnywhere 0.20.12 and its actual native backend.
The APK includes the verified real SmolLM2 135M Instruct Q4_K_M asset, not the
historical 360M assertion. ModelLibrary validates extraction, reconciles artifact
state, manages SDK or verified HTTPS downloads and SAF imports, and owns startup
selection and load locks. Context/quantization/KV controls honestly distinguish
SDK-managed settings from optional native CPU CLI controls.

Optional llama.cpp local CPU and layer-RPC coordinator/workers are ABI-specific
packaged native executables. Native worker sockets are loopback; the public worker
transport requires pinned authenticated TLS. Offers negotiate eligibility and
memory-weighted layer splitting. Current coordinator activation is local; durable
consensus, shared task leases, remote KV restoration and physical oversized-model
proof remain open gates. A complete artifact stays on the coordinator disk.

Online chat is explicit per conversation and uses encrypted provider profiles.
Adapters support OpenAI/compatible Chat Completions, Claude Messages and Gemini
text generation. Public HF model/free/price discovery is separate from hosted
inference access and billing. No paid automatic fallback or invented model list.

## Human and agent control

AgentBackend and durable encrypted typed job controllers provide idempotent
submission, bounded queues, observable status/cancel/retry and saved scopes.
Real task-board/workspace operations have their own persistence. Agent management
shows actual jobs and registered MCP tools. The bootstrap fake Healthy agent
runtime was removed; an MCP lifecycle adapter still wraps the real server.

OpenClaw has separate gateway, signed Android-node and phone-model-provider paths.
Android autonomy/accessibility is opt-in and allowlisted; root remains human-only.
Network/control companions enroll with invitations and owner approval. Device
kind and requested roles do not authorize compute. Raspberry Pi, microcontroller,
sensor and radio categories may contribute non-LLM capabilities once verified.

ConfigurationTransfer provides strict non-secret schema-v1 settings export/import
with preview/local preflight and partial-apply reporting. It does not carry device
trust, OS grants, agent scopes, secrets or automatic enrollment.

## Device, power and tools

DeviceRuntimeProbe reads real Android/API/ABI/RAM/storage/thermal/hardware state;
DeviceRuntimePolicy admits bounded local workloads. PowerRepository reads actual
battery/network metrics and enforces saved download/new-load policies. Costs are
labeled estimates, with unknown sensors/usage/prices retained as unknown.

ExternalDevices reads USB descriptors and mounted volumes; Android prompts and
SAF grants authorize access. Discovery does not install GPU/serial/camera drivers.
Acceleration preferences choose supported new-model defaults and native CPU
thread caps. Major GPU/NPU/vendor/OS plugins remain unavailable until installed
and generation-tested, rather than being successful-looking switches.

SSH uses maintained JSch 2.28.7 with mandatory host-key pins, encrypted credentials,
bounded exec and cancellation; not a configuration-only SSH scaffold. Listener
firewall policy is validated/persisted and gates new legacy inference, HTTPS
control and RPC worker connections. It is not OS-wide/outbound filtering.

Optional terminal/Linux VM/VNC require their real runtime artifacts. Crawl4AI is
a scoped separately installed companion. The code workspace is a real bounded
offline editor, not a full IDE compiler/debugger. Existing voice wrappers and
bounded image/camera input are exposed in Settings; VLM native support can report
unavailable. Live CCTV/UVC/DSP/radio adapters are planned explicitly.

## Continuation documents

- `../device-aware-model-runtime.md`: device admission and native/SDK tuning.
- `../layer-pipeline-and-recovery.md`: genuine execution and phone recovery gates.
- `../online-power-peripherals-and-configuration.md`: implemented source contracts.
- `../declarative-federation-roadmap.md`: IaC-style resources and multi-cluster tasks.
- `../media-iot-and-radio-nodes.md`: non-LLM nodes, media and two-way radio gates.
- `../../AGENT_BUILD.md`: build/port instructions for the other agent.

Validation must name artifacts, runtime, tests and physical devices. An APK or
compile pass is not proof of inference, recovery, vendor acceleration or radio
transmission. Read PROGRESS for the latest actual results and outstanding work.

## Custom models and training continuation (2026-10-06)

Read [../local-model-behavior-and-training.md](../local-model-behavior-and-training.md). Custom local prompt behavior is default-off;
compatible custom weights are imported through Models. No toggle changes learned
refusals or hosted-provider rules. Settings → Fine-tuning drives the optional
Soup 0.75.0 POSIX host companion via pinned SSH, with real job/status/log/cancel
contracts. Android synthetic gradients are removed; phone autograd reports
unavailable. Actual Soup training, adapter quality/evaluation and GGUF deployment
remain acceptance gates. Keep phone inference layer sharding/recovery primary.

## Cloud, browser and file continuation

Read `../cloud-and-credential-management.md`, `../USER_GUIDE.md` and
`../runanywhere-browser-and-llama.md`. Dedicated Cloud has fixed read adapters,
source-stamped resource/cost observations and purpose/origin-bound encrypted
credential environments. Human and agent actions, environment use and enrolled
device scopes are independent. Deployment/IaC and full vendor service coverage
remain future adapters.

The visible WebView broker serves typed status/run/stop commands. Exact approved
origins, foreground lifecycle, per-step authorization and action/timeout caps
constrain actual local-model reasoning; failed model parsing is an error, not
a scripted substitute. External browsers use the separate generic Android
Accessibility tools and detected-browser app scopes. Both paths need real task/
physical evidence. AI asset inspection and streaming bounded ZIP/unzip operate
on user-granted storage; archive extraction is not a model import/runtime.

## Audit telemetry

Opt-in metadata collection samples actual Android device state, model/library/task/
pipeline transitions and typed human/agent operation outcomes. An encrypted bounded
journal provides retention, filters and JSONL/CSV export. Optional OTLP/HTTP exports
private metadata traces and metrics with endpoint-bound encrypted auth. Closed
export sanitization excludes prompts, URLs, paths, credentials and exception bodies.
See ../AUDIT_TELEMETRY.md for actual coverage/loss limits and collector/dashboard
setup. Vendor SDK telemetry opt-out, full fleet coverage and a durable remote outbox
remain unverified/unimplemented; no hosted Grafana proof is implied by loopback OTLP.

## Distribution and agreement continuation (2026-10-07)

The owner authorized GitHub beta assets, a separate Play review APK/AAB, and
first-use Terms/Privacy acceptance. Read docs/PLAY_DISTRIBUTION.md (relative to
repository root) and the public/offline policy copies. `playReview` is a restricted
review candidate; no Play approval/submission is claimed. Policies are versioned,
both checkboxes are mandatory, and app-owned SDK/bootstrap work waits for acceptance.
Optional permissions/telemetry remain independent. Preserve the original open-source
license rights and third-party notices. Production release signing must never fall
back to a debug certificate; owner keys remain external to source.

## Final beta checkpoint and owner stop (2026-10-07)

Read `docs/SESSION_HANDOFF.md` (repository root relative) and PROGRESS.md. Final
combined Full/Review APK/AAB build passes; 639 unit tests and one API35 x86_64
Android audit test pass. Full lint has zero errors (348 warnings/17 hints each),
Review zero errors (351 warnings/17 hints). CI passes at 2e37dcd. Review static
manifest/policy/23-library ELF alignment checks pass; Play approval and actual
16 KiB runtime proof remain open. Final emulator Models shows the starter loaded;
normal cold startup took 7.406s after Gradle stopped, with two ANRs during lint.
The owner stopped for sleep, then requested progress saving/local release asset
preparation. The owner has since authorized continued work and Intel publication; see the current handoff.
Beta.1 publication is historical and verified; current Intel Studio distribution is tracked in ../RELEASE_EVIDENCE.md. Keep physical phone sharding/recovery, hosted
Grafana, vendor SDK telemetry opt-out and optional integrations explicitly open.

Gateway continuation: see ../AGENT_GATEWAY_AND_SECURITY_LAB.md. Protocol endpoint implementation is Meshlit Kotlin, distinct from the pinned upstream Rust companion.

## Current desktop core boundary — 2026-10-10

Desktop-engine reuses the portable GGUF/model/negotiation sources, desktop-ssh reuses scoped SSH sources, and desktop-hyperl keeps its separate licence. Intel local inference is an owned pinned CPU process with verified SSE4.2/AVX2 dispatch, memory/native-context admission, bounded streaming/options, human lazy load and idle unload. Recovered installers execute it with a private Java runtime. The seven-category management shell has real bounded local controllers and separately marked pending backends. A shared layer planner is not a desktop distributed executor. Production admission, independent device execution, agent authority/audit, OS firewall/VM/container/cloud and other platform backends remain tracked gates. See ../DOCUMENTATION_STATUS.md and ../DESKTOP_FEATURE_TRACKER.md; older numeric phase records are design/history, not global current status.

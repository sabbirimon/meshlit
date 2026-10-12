# Meshlit progress and evidence
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](PLAN.md) · [Progress](PROGRESS.md) · [Document status](docs/DOCUMENTATION_STATUS.md).
Scope: cross-platform tracking; feature and device gates remain separate. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-10. Historical 2026-10-07 validation ran 2026-10-06 UTC. Historical baseline `ff0cd771c12f3daa63cb8fe45e1476245ab7b01b`.
Checkout: `/Users/code/Documents/Codex/2026-10-06/re/outputs/meshlit`.
Current review branch: `codex/hyperl-production-library`; previous publication branch
`codex/meshlit-ui-pipeline-openclaw`. Owner IMON authorized source publication
to `sabbirimon/meshlit-v2` on 2026-10-07, followed by GitHub beta APK release assets
and a separate Play review build. No force push or Play submission is authorized.
Historical upstream records in `docs/history/` are not current test evidence.

`FEATURE_MAP.md` and `docs/feature-map.json` current inventory **88 feature areas,
38 durable command operations and 39 registered Gradle modules**. Feature counts do not establish
an overall completion percentage. Source implementation, automated contracts,
emulator observations, host runtime proof and physical-phone proof are separate.

## Peering, wallet and UI continuation — 2026-10-12 (paused)

Owner clarified the target as `/Users/code/Documents/Codex/meshlit-v2-dev`, for both Android and desktop; Android Studio `mllm` remains another agent's reference checkout. Portable security, discovery/settings fixes, Android peering routes and desktop wallet/connection/UI source are in progress and uncommitted. Owner requested a pause and future build plan. See [handoff](docs/PEERING_UI_HANDOFF_2026-10-12.md) and [future acceptance gates](docs/PEERING_UI_BUILD_PLAN.md).

Current Documents-checkout evidence: core-trust 12 tests and core-discovery 20 tests report zero failures/errors. The combined run was interrupted during desktop Kotlin compilation (exit 130); no current desktop/app/full-lint qualification is claimed. The earlier 154-test Android Studio repair is separate evidence. No push/release or scheduled continuation.

## Stabilization planning refinement — 2026-10-10

The owner requested a detailed plan and build guide before implementation. [S0–S6](docs/desktop/STABILIZATION_PLAN.md) now defines durable chat/lifecycle, model transfers, accurate monitoring, shared action authority/audit and physical two-device acceptance. [The build guide](docs/desktop/STABILIZATION_BUILD_GUIDE.md) documents current source commands, input verification, Intel packaging, Android checks, evidence and environment blockers. Initial worker-loss recovery means visible failure and explicit retry; transparent distributed failover remains a separate gate. Local desktop and cluster preview release decisions are independent.

This is documentation refinement only. No new runtime, installer, device qualification or production promotion is claimed. Milestone implementation awaits the next implementation decision. Current documentation coverage is generated in docs/DOCUMENTATION_STATUS.md.

## Desktop/server continuation — 2026-10-10

The owner requests desktop/server management dashboards, Android feature parity,
containers/Kubernetes, security/firewall tools, recovery, permitted full-auto
agents/hooks/MCP, terminal, Ghostty and both Dolphin speech/chat families.
[Full requested scope](docs/DESKTOP_FEATURE_TRACKER.md),
[43-old/49-current Android menu comparison](docs/ANDROID_DESKTOP_PARITY.md) and
[build update ledger](docs/DESKTOP_BUILD_LOG.md) now distinguish real code,
contract checks, package/runtime qualification and missing backends.

Working desktop sources add grouped Basic/Advanced menus, model references/HF,
saved nodes, live local OSHI monitor/process controls, crypto, scoped bidirectional
SSH, native CPU HyperL adapter, human shell and optional external Ghostty.
The system-role serialization bug was corrected and passed both serializer
contracts and actual offline generation. Global scoped search and the compact
seven-category management overview are implemented and rendered. The shared
sharding planner admits integer whole-layer counts against per-worker memory,
with bounded manual placement and weak optional worker exclusion. Desktop local
context/CPU/batching/KV/idle options, memory admission and native context identity
are implemented. Native f16/q8_0/q4_0 controls produce text and verify context;
q4_0 produced a poor arithmetic reply, so it is excluded from normal settings.
Auto AVX2 generates a real 82-token reply at 16.047 end-to-end tokens/sec during
background lint; this is not a matched speed comparison. Authentication rejection
and owned-process Unload pass. Targeted current
JVM executions total 324 (30 desktop, 12 desktop shared engine, 4 SSH, 4 workspace,
274 Android inference), without failures/errors/skips on this Intel Mac; duplicate
shared-source executions are not independent device proof. Actual OSHI local
sampling, HF metadata and all 12 HyperL JNI CPU recipes pass. Broader agent automation,
OS firewall, container/Kubernetes, VM, cloud vault/billing and full repair remain
pending. Intel Studio 40.1 DMG/PKG now pass recovered-payload checks with their own Java runtime, real local generation/authentication/unload, matching signed app/native/model hashes and all HyperL CPU recipes. The PKG additionally passes actual monitor sampling and inspected management rendering. The first trimmed-runtime failure and correction are recorded in the build ledger. These remain experimental, ad-hoc/unsigned and not notarized; clean-machine installation is unqualified. Intel Studio release is public with all eight remote asset digests matched: https://github.com/sabbirimon/meshlit-v2/releases/tag/v2.0.0-macos-intel-build40.1. Source target c1f2d60 includes documentation reconciliation and the portable CI fixture; application/native/model packaging source remains 12bd20a. Documentation tracks 153 Markdown files with 118 active guides and separate named workstream gates. About 5 GiB duplicate generated scratch was reclaimed. Current CI is observed separately from the 521 local JVM results.
Android starter/local defaults are retained. Shared planner and inference module
API-24 desugaring/voice permission-error handling changed; Both Android Full V1/V2 debug assemblies and lints pass in the serial regression
run (13m34s): zero errors, 374 warnings each; core inference lint zero errors,
7 warnings. The combined desktop/shared/MCP/network/sandbox valid JVM result
files contain 521 executions without failures/errors/skips; cached unchanged
suites and shared re-executions are not separate hardware proof. Desktop RPC worker execution, independent-device speed,
production acceptance and other platform hardware remain unqualified.

## Build 40: Studio, host clients and scoped connected tools — 2026-10-09

The current change adds shared Kotlin workspace contracts and a Compose desktop
host client, native HarmonyOS NEXT project source, compact Studio styling, optional
Colibri hosting, actual ggwave JNI with English transcripts, offline cryptography
and scoped peer/node tools. New connected routes remain Experimental and off by
default. Core Candidate remains separately installed and does not admit those
routes. Android's normal local model/prompt/history defaults are retained.

All **805 affected JVM cases** pass, as do three APK assemblies and Android lints
with zero errors/fatal. Actual host JNI PCM round trips, native desktop rendering,
bundled-runtime trusted/untrusted HTTPS checks and a same-Mac WebRTC browser
fixture pass within their limited scopes. Corrected APKs verify signatures,
versionCode 40, full pinned starter, four-ABI native alignment, Core boundaries
and current help/notices. A discovered stale policy-asset mismatch is corrected;
the new pre-build guard checks byte identity and version 2026-10-09.2 agreement.

Samsung remains absent from ADB, so build 40 has not been installed or phone tested.
DevEco/NEXT compilation and hardware, Colibri model generation, physical acoustic
communication, Internet NAT/relay and independent-device execution remain gates.
See [build-40 evidence and exact hashes](docs/VALIDATION_BUILD_40.md). Earlier
ledger entries below preserve their own versions and qualification scopes.

## Samsung, local tools and permission controls — 2026-10-08

The owner resumed single-phone tests on Galaxy A20s SM-A207F, Android 11/API 30,
ARM64, Adreno 506. Both USB and owner-paired TLS wireless ADB reach this device.
Full V2 installed over USB and Full V1 over TLS wireless, preserving the existing
signing identity and app data. Five scoped host-to-shell requests per transport
passed after a warmup; median wall times were 89.748 ms and 172.200 ms, including
Mac process/ADB overhead during builds. They are not network-only latency.

The standalone HyperL Vulkan runner passed all thirteen listed cases separately
over both transports: eleven exact CPU-verified finite outputs and two expected
overflow rejections, followed by temporary-file cleanup. Meshlit's earlier V2
app-UID CPU suite passed all twelve recipes and bounded benchmarks; its partial
UI run and failed model-admission harness are retained in the
[device record](docs/DEVICE_TESTING_2026-10-08.md). These are different backends;
the app's HyperL screen does not dispatch mobile GPU inference.

Current source adds always-visible permission Manage controls, explicit Android
11 notification status, bounded human-confirmed package administration, and
default-off local chat web/phone tool loops. Local inference and internet access
are separate. Saved delegation, target scope, OS grants and emergency controls
remain independent; in-app ADB, silent installation and root adapters remain
unimplemented. [Local tool setup and boundaries](docs/LOCAL_MODEL_TOOLS.md).

A fresh model UI test passed over both USB and TLS wireless on build 13: full
105,454,432-byte pinned hash verification, actual SDK Load, non-empty Chat Send
and Unload. Native CPU/TLS/checkpoint smoke passed on both transports. The short
reply regression was traced to the test leaving its 16-token chat selected; new
source persists/restores owner selection and retains normal 1,024-token defaults.
The owner confirmed the reinstalled recent build works. Model answer quality
remains model-dependent; longer output alone is not quality proof.

Targeted local evidence now totals **832 JVM cases**, zero failures/errors/skips:
common 27, GPU 36, MCP 133, sandbox 14, network 46, inference 266 and app 155 per
Full flavor. Common/GPU suites retain their unchanged prior evidence; required
changed module/flavor checks reran. New contracts cover scoped client keys, chat
restoration, optional memory, speech pack integrity, WAV bounds and transcription
requests and native float PCM normalization. Twelve benchmark-wire and nine
crawler checks pass. APKs contain the exact real starter and synchronized help
assets. Final required unit/APK/lint checks passed in **18m11s**. Full lint has
zero fatal/errors, 364 warnings and 18 hints per flavor; Play Review lint has
zero fatal/errors, 367 warnings and 18 hints. Review static permission/policy and
all 23 ARM64 ELF alignment checks pass; runtime 16 KiB testing, signing and Play
submission remain unqualified. Final Full APK hashes match device-tested build 28.

The workspace exposes Monitor/Networking/SSH/Labs, cards/rows and adaptive/focus
layouts. Model details separate declared tags from unknown quality and modality
adapter requirements. Client keys are scoped/expiring and loopback/screen-bound;
[remote deployment limits](docs/CLIENT_HUB.md) remain. Optional encrypted
[memory/personality/recovery and offline/online voice](docs/MEMORY_AND_VOICE.md)
are separate from the current LLM. Offline speech uses imported checked ONNX
weights; hosted audio is explicit. A build-28 prerecorded USB speech test passes
actual recognition/synthesis plus local LLM generation with both speech models
loaded and separate successful cleanup. Live microphone/provider sessions, unlocked
UI/engine acceptance, sustained thermal tests, two-phone sharding, configured
crawler/cloud/SSH and signed production distribution remain separate gates.

## Earlier HyperL app port validation — 2026-10-08

Subsequent owner-authorized **single-phone GPU experiment** passes on Samsung
Galaxy A20s SM-A207F, Android 11/API 30, Adreno 506 / Vulkan 1.1.128. HyperL's
separate native ADB-shell runner executes eleven finite-output cases matching
its CPU reference and two expected overflow rejections; temporary device files
are removed. [Hardware/build record](https://github.com/sabbirimon/HyperL/blob/codex/production-library/docs/ANDROID_VULKAN.md).
At that earlier checkpoint no Meshlit APK had been installed/run and no app
JNI/GPU backend was wired. The subsequent app installations and tests are recorded
above. Portable source hashes remain unchanged. Sustained lifecycle/thermal and
two-phone cluster tests remain pending. The build evidence below predates these
subsequent device tests.

The review branch adds Settings → HyperL libraries to both flavors: twelve actual
CPU recipes, strict JSON, complete-graph memory admission, editable examples,
Stop, Metal/Vulkan source generation and explicit clipboard copying. Source
port provenance pins six Apache-2.0 files to standalone HyperL tag source d330d2b;
namespace-normalized hashes match. Runtime/ABI remain version 1. The human-only
controller is bounded and shares HYPERL per-function/global emergency controls.

Local validation passes: 36 core-gpu, 27 core-common, 120 core-mcp, 14 sandbox,
46 network, 260 inference and 133 app tests per flavor: **769 cases, zero
failures/errors/skips**. The first app/core/build/lint run passes in 24m40s;
the required regression and final help/APK/lint run passes in 13m35s. Both Full
flavors build ARM64, x86_64 and universal debug APKs. ARM64 payload inspection
finds the real 105,454,432-byte starter model and HyperL controller classes.
Fatal lint remains enabled: **zero errors/fatals, 350 warnings and 17 hints per
flavor**. Nine crawler unit checks pass using the existing crawler virtualenv;
system/bundled Python initially lacked FastAPI. No live crawler/device proof is
inferred. Offline HTML help and its APK asset are byte-identical; the feature
map validates 75 areas, 38 durable operations and 33 modules.

At that checkpoint no APK had been installed on a physical phone; single-phone
tests have since resumed as recorded above. App GPU/NPU execution, distributed
HyperL jobs, SDK publishing, signing,
installation/lifecycle/thermal acceptance and independent security review remain
gates. Standalone desktop installers and Radeon evidence are separate from this
app port. [Use cases and boundaries](docs/hyperl/APP_AND_LIBRARY.md) explain it.

## Current publication validation (2026-10-07)

Final combined validation **passes** at source `2e37dcd` (Full production code
unchanged from `db3aaed`; Play Review overlay fix at `53ac70c`). Both Full flavors
and Play Review APK/AAB build. All **639 targeted unit tests** pass: 8 audit,
65 cloud, 46 network, 260 inference and 130 app per Full flavor. No failures,
errors or skipped tests in these suites. Android audit instrumentation passes
on API35 x86_64 (1 test, 9.343 seconds).

Full lint: **0 fatal/errors, 348 warnings and 17 hints per flavor**. Play Review:
**0 fatal/errors, 351 warnings and 17 hints**. `abortOnError=true` remains enabled.
Final log: `../meshlit-validation/release-final-validation.log` (BUILD SUCCESSFUL,
6m32s). Static Review checks pass for removed permissions/services, policy-copy
agreement and 23 ARM64 libraries with 16 KiB ELF LOAD alignment. Actual 16 KiB
runtime/AAB testing and Play approval remain open.

Earlier failure logs are retained: unavailable `android-37` package (fixed to
`android-37.0`), stale duplicate incremental Review resources (cleared), source-
analysis interruption, Review overlay telephony lint (fixed explicitly) and
transient test port bind conflicts (bounded fixture allocation/reuse, TLS/pin/
authentication assertions preserved). These failed runs are not final evidence.

Final V1 Chat, Models and Monitor were inspected on the emulator; the real bundled
starter was loaded and the runtime ready. Normal cold launch succeeded in 7.406s
once Gradle stopped; two launches during concurrent lint had startup ANRs. A visual
loader-animation capture and physical startup performance remain unverified.
See `../meshlit-validation/beta-ui-results.json` and `reference-ui/beta-*.png`.

The source includes actual-state boot animation/loader, compact Models/Monitor
status cards, both-build legal acceptance, encrypted optional audit export and
corrected PCAPdroid consent intents with bounded classic-PCAP imports. The legacy
built-in VPN dropped traffic without forwarding: it is disabled in Full and removed
in Play Review; companion live capture is still untested. Blue Team,
forensic, root/Magisk and other Security Lab tool sources were reviewed and pinned;
the companion assessment adapters remain plans. No listed security tool was
installed or run. Native phone sharding/recovery and hosted Grafana proof remain
open. Physical Samsung was not attached; only API35 x86_64 emulator is available.

## Audit telemetry and publication continuation

Source wires Settings → Audit and telemetry: opt-in encrypted bounded history,
source/actor/outcome/time filters, JSONL/CSV export, real process-lifetime Android
samples and typed human/agent command outcomes. Optional OTLP/HTTP traces/metrics
use an explicit HTTPS base URL and encrypted endpoint-bound headers. SDK exporters
close on replacement; collection/export boundaries exclude content/secrets and
strip exception events. A collector recipe and Grafana dashboard template are in
docs/observability/. No hosted Grafana account or complete fleet coverage is claimed.

Eight core observability tests pass, including actual loopback HTTP/protobuf trace
and metric receipt, private-text exclusion, retention and committed-write failures.
The final Android encrypted-journal test passes; see
`../meshlit-validation/audit-android-final.log`. Final full lint passes in all three variants. This is bounded best-effort
observability, not signed/transactional compliance auditing. Native phone sharding,
replicated recovery and vendor SDK telemetry opt-out remain separate open gates.

Publication includes a rewritten evidence-based README with real screenshots,
live repository badges, IMON credit, contribution forms and llms.txt discovery.
Source stays Apache-2.0; third-party notices remain. Repository metadata/topics are
published. Source is pushed to `sabbirimon/meshlit-v2/main`; destination history
is preserved as an ancestor. The About line and banners say “Many nodes. One mind.”
Hosted CI passes at `2e37dcd` (run 37536692867), using the actual
`android-37.0` SDK package. Codex by OpenAI is credited in README/AUTHORS and new
commit co-author trailers; GitHub sidebar account association is independently
controlled by GitHub. Ranking is not guaranteed;
no external promotional posts or invented adoption is claimed. Beta release
assets are published at [https://github.com/sabbirimon/meshlit-v2/releases/tag/v2.0.0-beta.1](https://github.com/sabbirimon/meshlit-v2/releases/tag/v2.0.0-beta.1) as prerelease **v2.0.0-beta.1**,
targeting `ad2d3a86f5060ace52c1b46c7a5501253dcc01f7`. All eight uploaded assets have matching GitHub SHA-256 digests:
four Full/Review ARM64/x86_64 APKs, Review AAB, notes, metadata and checksums.
`../meshlit-validation/github-beta-publication-results.json` records verification.
The owner stopped for sleep, then explicitly requested saving and APK upload.
That upload is complete; all other work stops until the owner resumes.
Read `docs/SESSION_HANDOFF.md` before resuming.

## Agreements and Play review continuation

Both flavors now have an installation-local, versioned Terms/Privacy gate and
persistent offline policy pages in Settings. Application-owned SDK initialization,
startup loading, bootstrap and audit startup wait for acceptance. Android grants,
agent scopes and optional telemetry remain separate. Agreement preferences are
excluded from backups; old versions require renewed acceptance. Tests cover both
choices and persistence/version invalidation.

`playReview` supplies a separate debug-signed, non-debuggable APK and AAB candidate.
The narrower manifest removes autonomous Accessibility/VPN services, SMS, broad
storage/media grants, battery-exemption requests and Termux command permission.
Runtime controls cannot re-enable Android autonomy. Missing operator release keys
now produce unsigned release output rather than silently using debug signing.
`docs/PLAY_DISTRIBUTION.md` tracks AI reporting/prevention, SDK data flows, private
privacy contact, production identity/signing and actual Play/device review gates.
No Play compliance certificate or store upload is claimed.

## Latest verified UI checkpoint

The reference layout removes duplicated Settings headers/insets and the Appearance
banner. Chat uses a centered welcome, pale blue/cyan glow, compact model selector,
rounded composer above the keyboard and real Copy/Share actions. Models puts actual
models/import/search before optional device/startup details. Font and color controls
remain persisted; retained legacy tools still need individual redesign.

Both debug variants build; **128 app tests per variant pass**, with **0 lint
errors, 342 warnings and 17 hints per variant**. Evidence:
`../meshlit-validation/reference-ui-final-build.log` and `reference-ui-results.json`.
Final APK screenshots cover Chat, Settings, Appearance and Models. Earlier UI
iterations exercised light/dark keyboard placement, 320dp width/150% text,
real bundled-model generation and Android Copy/Share (no recipient selected).
Physical Samsung UX and live generated-media sharing remain unverified.

Actual SDK generation attempted its development telemetry endpoint; DNS failed.
No successful transmission or payload is established. This remains tracked in
`docs/remaining-engine-bugs.md`; SDK development mode is not a proven opt-out.
The audit continuation below adds local metadata history/export and optional
OpenTelemetry collector/Grafana integration.

## Previous verified backend continuation

Both debug flavors and instrumentation APK build. Full lint passes with **0 errors,
337 warnings and 17 hints per flavor**, with fatal errors still blocking builds.
Logs: `../meshlit-validation/final-private-browser-validation.log` (shared core)
and `final-bounded-browser-validation.log` (final app/browser).
Unit checks: inference **260**, MCP **107**, cloud **65**, app **128 per flavor**;
SSH **6 passed / 1 live-host test skipped**. No unit failures/errors.

The final API35 x86_64 emulator ran **7 tests in 82.671 seconds, 0 failures/ignored**:
real native worker authentication/context/KV checkpoint tests; three real model/
router tests; Android encrypted-vault repository reopening; a real credential-free
OpenRouter catalog; and actual WebView DOM test pages. Native snapshot occupancy
was 18 tokens, actual prompt reuse **2 tokens**; q8 K-cache snapshot 318,572 bytes
versus f16 415,772 bytes. SDK token/TPS counts remain unknown, not text-chunk counts.
The public catalog returned 80 bounded records; it is not authenticated vendor
account proof. Read `../meshlit-validation/final-emulator-proof.json` and its logs.

The browser has approved-origin bounded sessions, typed status/run/stop commands,
foreground/revocation checks, on-device-only dispatch and suppression of legacy
chat/history broadcasts. Viewport observations are bounded to 80 controls/24,000
serialized characters; oversized links are omitted. DOM fixtures verified stale
controls, password pauses, scrolling, bounds and navigation without site click
handlers. **No successful real-model real-site browser task or physical external
browser session is claimed.** Installed-browser Accessibility scope selection is
separate from the WebView loop. The 135M starter is not a proven browser planner.

Cloud/vault read adapters and reuse, AI text inspection/streaming ZIP tools,
persisted fonts/solid/glass choices and offline illustrated guide/tutorial are
wired. The guide has 19 chapters, 3 conceptual SVG diagrams and 8 reading lessons;
asset/export match. Local HTML visual preview was blocked by Browser Use's file
protocol policy and remains unverified. Large SAF/provider archive tests, actual
credentialed cloud/SSH/OpenClaw/VM/Soup tests, managed IaC and physical-phone
oversized layer execution/replicated recovery remain open gates. No percentages
or production successful stub data are used. The other Android checkout is untouched.

## Implemented in source

| Workstream | Source behavior | Current boundary |
| --- | --- | --- |
| App UI/settings | Shared chat/history, Models, Monitor, Network, Tasks, Logs, Settings/Tools; Basic/Advanced search; saved colors, fonts, text size and constrained-device solid/glass surfaces | Every retained legacy tool is not fully redesigned; final device UX checks remain |
| Model management | Actual pinned 135M starter, SDK/verified HTTPS downloads, SHA/atomic install, resumable transfers, multi-SAF import, remembered startup/load/unload | Real bundled emulator load/generate/unload now passes; physical phones pending |
| Model tuning/device identification | Real Android OS/ABI/RAM/storage/thermal/hardware readings, resource admission, GGUF metadata, native CPU context/KV/thread controls | SDK-managed context/GPU controls stay explicit/unknown; vendor plugins unavailable |
| Offline/online chat | Saved conversation options; encrypted OpenAI/compatible/Claude/Gemini profiles, real discovery/requests and usage-based cost estimate | Paid calls need operator credentials; online replies currently buffered |
| HF models/pricing | Revision-pinned Hub artifacts, encrypted access token, live router offers with source/time, search/free filter and provider-pinned price selection | Public catalog does not grant free/gated model inference or account entitlements |
| Human/agent control | 38 typed operations, encrypted durable local jobs, idempotent admission, scopes, bounded execution/status/cancel/retry; actual tools inventory | Fake Healthy AgentRuntimeStub removed; external-agent interoperability pending |
| Tasks/workspace | Durable tasks/subtasks, priority/tags/dates/search/bulk changes; bounded offline CodeMirror files and SHA write preconditions | No distributed claims/recurrence, full compiler/debugger or task execution from metadata |
| Pairing/web/API | Native worker QR/manual verification; optional TLS enrollment/approval/scopes/revocation; approved control groups | Control membership does not authorize native compute; physical LAN checks pending |
| Cloud/vault | AWS/Azure/DigitalOcean/GCP/OpenRouter/custom read adapters; resource/cost dashboard; encrypted environment references; independent human/agent controls | Authenticated accounts, OAuth, deployment and broader service adapters pending |
| Native recovery | Actual encrypted native CPU KV snapshots; committed local job/task records | Replicated journal, fenced failover and portable/RPC KV pending |
| Native layer execution | Actual CPU coordinator/workers, fresh offers, memory-weighted layer placement and authenticated pinned TLS | Desktop two-worker proof exists; physical-phone/oversized-model proof pending |
| Power/costs | Actual battery/thermal/network sensors, session estimates/export and saved transfer/new-load policies | Whole-device estimate; unknown sensors remain unknown; physical power validation pending |
| USB/OTG/storage | Actual descriptors/classes/permissions, mounted storage and SAF grants | No universal serial/GPU/camera driver; hotplug tests pending |
| SSH | JSch 2.28.7, mandatory host-key pin, encrypted credentials, bounded exec/cancel and per-host agent opt-in | Live server tests, PTY/SFTP and automatic deployment pending |
| Firewall | Validated persisted port/address policy wired to new legacy/control/RPC connections | Private IPv4 inbound app listeners; no OS-wide/outbound firewall |
| Portable settings | Strict non-secret schema-v1 export/import, preview, local preflight and partial-apply reporting | No trust, secrets, permissions, delegation or distributed transaction transfer |
| OpenClaw/autonomy | Gateway client, scoped signed Android-node adapter, optional loopback model provider, opt-in allowlisted Accessibility actions | Live gateway and physical consent/stop/revoke tests pending |
| Media/IoT | Main media settings; bounded image import/phone-camera thumbnail; existing voice wrappers; Pi/MCU/sensor/radio categories/role claims | Compatible media models/backends required; CCTV/UVC/MCU/radio adapters planned |
| Browser | Real local-model observe/act loop in visible WebView, 38-command backend browser controls, origin/site/agent permissions, deadline/limits/Stop; installed external-browser Accessibility scope selection | Real-model real-site task and physical Chrome/Samsung Internet tests pending; sensitive workflows stay human |
| Files/guide | Granted-storage browsing; bounded AI text inspection; streaming ZIP/unzip with safe paths, limits, cancellation and cleanup; 20 offline illustrated chapters and eight reading lessons | Large SAF/provider tests and interactive execution walkthroughs pending; browser preview of local HTML blocked |
| Terminal/VM/crawler | Optional bounded terminal/runtime/QEMU/SSH/VNC paths; scoped Crawl4AI companion | Real binaries/guest/physical runtime tests still required |
| Future platforms/recovery | Capability interfaces and detailed vendor/OS, journal/lease/checkpoint and internet federation plans | Linux/Windows/macOS/HarmonyOS apps, portable KV and automatic cluster failover not implemented |

## Latest completed validation

Sequential native/checkpoint and Cloud/vault validation is in progress. The updated
source, contracts and acceptance boundaries are recorded in BUILD_MILESTONES.md,
docs/native-checkpoints.md and docs/cloud-and-credential-management.md. The entries
below are historical checkpoints until the current evidence is appended.

- `../meshlit-validation/media-configuration-validation.log`: **BUILD SUCCESSFUL in 2m 49s**.
  Both app flavors and V1 instrumentation APK assembled.
- Core inference: **250 tests**; core MCP: **103**; firewall: **17**; SSH: **6**;
  training: **27**; app: **115 per flavor**. All report zero failures/errors/skips. Machine-readable
  result: `../meshlit-validation/test-results-2026-10-06.json`.
- Earlier sandbox **8**, network **40** and crawler **9** checks passed. They are
  earlier checks, not a new full-module run after every current source addition.
- Map validation: **52 features, 28 operations, 33 modules**, referenced paths checked.
- Desktop native RPC: `../meshlit-validation/native-rpc/proof.json` records two
  real worker processes, nonzero allocations and 32 deterministic tokens matching
  the local baseline. `physical_phone_proof=false`, `oversized_model_proof=false`.
- Actual public HF router fetch: **136 models, 317 provider offers, 206 priced
  offers, zero explicit free offers** in this captured catalog. No fake free row.
  Evidence: `../meshlit-validation/hf-public-catalog-evidence.json`.
- Real bundled asset: **105,454,432 bytes**, SHA-256
  `2e8040ceae7815abe0dcb3540b9995eaa1fa0d2ca9e797d0a635ae4433c68c2d`.
  APK contents were hashed, and the updated x86_64 APK installed on the API35
  emulator. Binary model/APKs are ignored by Git.

## Resolved startup regression and emulator proof

The earlier bundled test timed out at **180 seconds**. Buffered bounded GGUF
metadata parsing now resolves the observed startup delay: tokenizer-sized arrays
no longer require many tiny storage operations. Three regression tests cover
later metadata, truncated scalars and oversized arrays. Both flavors and the
instrumentation APK rebuilt (**BUILD SUCCESSFUL in 5m 23s**).

The same real bundled model now passes load → generate → unload in **8.723 seconds**
on the Mac's API35 x86_64 emulator. SHA-256 matched the pinned manifest; native
logs identify the actual 0.13B model and context 2048. The generated text was
`Hello, hello, hello.`. Evidence: `../meshlit-validation/bundled-model-emulator-proof.json`,
`bundled-model-emulator-pass.log` and `bundled-model-native-evidence.log` in that
same validation directory. Earlier failures remain saved; this is emulator proof,
not a physical-phone, model-quality or oversized-model result.

Native logs reported **6 generated tokens**, while the existing wrapper reported
**3 streamed text events as tokens** in that earlier wrapper. Chunk counting has
now been removed. SDK terminal counts still lack reliable provenance and are
reported as unknown; standalone native counts use actual metadata. See `docs/remaining-engine-bugs.md`. Current UI inspection confirms optional
permission setup, navigation and main Settings render; screenshots are under
`../meshlit-validation`. This is not acceptance of every legacy screen or feature.

Historical environment/media lint finished (**BUILD SUCCESSFUL in 17m 47s**) with
**51 errors, 330 warnings and 17 hints per flavor**, `abortOnError=false`.
`docs/lint-findings.md` and `../meshlit-validation/lint-summary-2026-10-06.json`
record its scope/results. The small buffered-parser follow-up came afterward;
that report predates the current source corrections. A subsequent full fatal-gate
lint pass found zero errors, 334 warnings and 17 hints per flavor. Cloud/vault
changes require their own final lint verification.

## Remaining implementation and acceptance

1. Preserve the resolved startup behavior, obtain trustworthy SDK-native token usage
   and prove physical-phone generation/stop/unload/reboot. Verify public/gated
   download, multi-import, interruption, storage,
   memory and power-policy failures on named phones.
2. Execute a genuinely oversized model across at least two physical phones:
   allocations, token correctness, latency, thermal/power, cancellation, loss and
   reconnect evidence. Desktop workers cannot substitute for this gate.
3. Build replicated compact task/session journals, fenced coordinator epochs,
   task leases, selected heavy-file holders and committed checkpoint replay.
   Portable KV export/import needs native compatibility checks. Automatic recovery
   after one/two node crashes must be demonstrated before it is advertised.
4. Validate current SSH, web/TLS/QR, OpenClaw, accessibility, permissions and
   optional native terminal/VM paths against real external hosts/devices.
5. Implement verified Linux/Pi companions, MCU telemetry/actuation firmware,
   CCTV/UVC/live audio and two-way radio adapters. Record drivers, actual hardware,
   scopes, rate/RTT, loss, revocation and stop results. RX-only is not TX support.
6. Implement actual accelerator adapters and platform apps one target at a time.
   SoC names and future settings reserves do not prove CUDA/NPU/vendor execution.
7. Implement cross-cluster task handoff and desired-state deployment after the
   real resource, durable lease and network-partition contracts exist.
8. Complete UI inspection and legacy tool modernization, richer settings indexing,
   chat export, interactive execution tutorials and IDE toolchains; reduce remaining lint warnings and validate release gates.

No production fake responses/devices/transfers/metrics/costs are acceptable.
Unsupported paths must report unavailable rather than fabricate success.

## Other Android checkout and continuation

`/Users/code/AndroidStudioProjects/mllm` source/Git were not edited. Only the
requested `MESHLIT_BUG_AUDIT.md`, `MESHLIT_FIX_GUIDE.md` and
`MESHLIT_UI_REDESIGN_GUIDE.md` were written there; copies are in `../meshlit-handoff`.
`docs/old-android-reuse-review.md` records reuse decisions.

Read `AGENT_BUILD.md`, `docs/layer-pipeline-and-recovery.md`,
`docs/online-power-peripherals-and-configuration.md`,
`docs/declarative-federation-roadmap.md` and `docs/media-iot-and-radio-nodes.md`.
Record date/commit, changed contracts, exact checks and evidence paths after work.
Never convert a UI control, DTO, unit pass or host proof into physical-device proof.

## Router/media execution evidence

Scenario rules, single/chain/compare modes, chat recipe selection and scoped MCP
route calls are wired. Real instrumentation executed two distinct installed local
models in a sequential chain and verified bundled/imported model generation:
three tests passed in 13.823 seconds (`../meshlit-validation/router-and-bundled-emulator.log`).
This proves single-device model routing; physical phone sharding and paid media
outputs remain separate gates.

## Latest router, media, behavior and training checks

Both flavors and V1 instrumentation assembled with the latest settings/router/media
source (`behavior-training-validation.log`: BUILD SUCCESSFUL in 2m 22s). Follow-up
training availability/gradient checks rebuilt both APKs successfully in 29s/10s;
`training-final-tests.log` passed 27 core-training checks. Current totals are 250
inference, 27 training and 115 per app flavor, zero failures/errors/skips. Earlier
MCP/firewall/SSH totals remain 103/17/6. The feature map has 52 referenced features.

The updated API35 emulator passed **three real model tests in 13.823s**: configured
TinyStories → bundled SmolLM2 chain, actual bundled load/generate/unload, and actual
imported model execution. Evidence: `../meshlit-validation/router-and-bundled-emulator.log`,
`router-real-model-outputs.log` and `router-and-bundled-emulator-proof.json`.
This is one-device sequential multi-model routing, not phone layer sharding.

Custom local model instructions are saved encrypted and default off. The optional
Soup 0.75.0 SSH host companion/UI provide actual-process job supervision/status/logs
and acknowledged cancellation. Ten Python companion contract tests passed.
The legacy synthetic-gradient/benchmark path is disabled; fake loss/peer deltas
and silent missing-desktop-gradient success are removed. Android training remains
unavailable. The local Soup diagnostic reports the package is absent; **no real
Soup training or trained-quality result exists**. Read
`docs/local-model-behavior-and-training.md` and `companions/training/README.md`.
Online paid vision/image/speech/video outputs and physical SAF behavior still
need operator/device acceptance. Their offline protocol checks are not live proof.

## Samsung physical test preparation

Owner authorized testing a connected Samsung. USB discovery identifies Samsung
and an ADB interface, but ADB 37.0.1 lists only the emulator. Its Mac log reports
`Unable to create an interface plug-in (e00002be)`. Owner restart/authorization or
wireless pairing is pending. No physical-phone tests are claimed yet. Test scope,
preservation rules and exact model evidence gates are in
`docs/physical-device-test-guide.md`. The updated APKs are in `../meshlit-install`.

## AI-assisted repair request

The owner requested diagnosis and evolution using local/online AI and web evidence.
`docs/AI_REPAIR_AND_EVOLUTION.md` records the bounded repair and isolated source-patch
workflow. This is a design milestone, not an implemented autonomous repair service.
Existing resume/retry/local checkpoint features must retain their narrower labels.

## Boot loader, hero cards and distribution split

The UI continuation adds an animated Android 12+ launch icon and an actual-state
Compose loader across supported Android versions. SDK initialization and startup
model loading drive the status; no timer or fabricated percentage marks completion.
Open app while loading bypasses the overlay without declaring the model ready.
Models and Monitor gain compact theme-aware hero cards with observed status;
Appearance and chat keep their compact reference layouts. These additions require
final rebuild/device verification before the beta assets are uploaded.

The owner explicitly wants two distributions: GitHub Full retains implemented
advanced features with their separate grants; Play has a narrower review manifest
and must meet its documented remaining gates before production publication.

## Hardware and authorized Security Lab request

The owner requested deeper hardware/core access and Blue Team/Red Team capabilities.
The capability-probed, scoped lab design is in
`docs/SECURITY_LAB_AND_HARDWARE_ACCESS.md`. Existing bounded DNS/TCP checks and runtime
plans are distinct from a working penetration-testing suite. Rooted physical-device,
VM, eBPF/perf, driver and process-tree cleanup acceptance remains unverified.

## Linux distribution and lab tool request

Alpine/Kali/Parrot/Ubuntu/licensed RHEL/custom profiles and optional Metasploit/lab
packages are documented in docs/LINUX_DISTRIBUTIONS_AND_LAB_TOOLS.md. These are
provisioning requirements, not installed APK assets or verified guest executions.
Current runtime plans require supplied compatible binaries/rootfs/images, with
restricted QEMU networking and ephemeral disk snapshots.


## Agent gateway and VM-gated lab continuation — 2026-10-07

Implemented an authenticated, manually started loopback gateway with buffered LLM,
stateless MCP JSON-response and A2A model-task subsets. Four Settings destinations
provide gateway configuration, manual commands/instructions, security assessments
and guest package lifecycle operations. Details and explicit outstanding milestones
are in `docs/AGENT_GATEWAY_AND_SECURITY_LAB.md`.

Security Lab and package actions require an SSH-ready VM on both root/non-root
phones. Root/PRoot/chroot alone cannot unlock the lab. Assessment grants bind to the
VM session; agents have separate expiring grants. Original read-only APK/PCAP
companions and optional tshark are implemented; other cyber tools remain planned.
Manual guest package managers and limited delegated pip are implemented; guest-root
is explicitly human approved. Guest internet is opt-in while stopped and applied
on next boot. No bundled Linux distro, Android Rust gateway or physical-device
qualification is claimed. Source, APKs and final validation notes belong to the
2026-10-07 continuation outputs, separate from the frozen earlier beta.


## Remaining-build continuation and GitHub push — 2026-10-07

The initial gateway/lab source was published to sabbirimon/meshlit-v2 main at
65eedca, preserving the newer contributor README commit. This next increment adds
explicit persistent qcow2 writes (ephemeral remains default), checksum/size-verified
VM setup artifact import from storage/HTTPS, a human-only bundled-companion setup
action, strict VM SSH port/session binding, and a bounded read-only fsstat adapter.
Imports never execute/unpack content. Tool process output/deadline failure contracts
and staging corruption/truncation/cancellation are tested separately from actual
phone/guest/provider acceptance, which remains open. Final build evidence is in the
2026-10-07 remaining-build outputs; do not claim the full protocol/cyber roadmap done.

## Continuation source and scope — 2026-10-08

Source is based on `77ac214`; physical-device testing remains paused and no live
cloud/provider/SSH account profile exists. Remaining areas 2–8 have additional local
implementation and explicit acceptance boundaries in `docs/CONTINUATION_2026_10_08.md`.
New work includes fixed signed crash-fault replica journal/manual metadata, authenticated
JSON MCP/A2A connectors and unified route dispatch, persisted managed stop/capacity
controls, original cyber metadata adapters, optional Terraform/storage/DSP companions,
SDK/topology observations and platform/driver/kernel qualification contracts.

Meshlit is intended as dynamic AI software across hosts/environments; phones are one
client. Current Android APKs remain Android applications. Hardware/OS installers,
privileged backends, GPU/NPU/FPGA execution and native transport integration require
actual implementation and qualification. HyperL originally lived on
`codex/hyperl-experimental` based on `77ac214`. The owner merged its experimental
source into `main` through PR #1 at `851576b`. The earlier `cecd2d9` APKs predate
that merge; subsequent merged-source artifacts include the CPU library in
`core-gpu`, without an app workflow or native accelerator loader.

Real CPU native-host comparison uses the existing pinned binaries and a 19,077,344-byte
model (SHA-256 `66967fbece6dbe97886593fdbb73589584927e29119ec31f08090732d1861739`).
CPU baseline and two actual RPC worker buffers produced identical output. This is
same-host loopback evidence: no TLS, oversized model, independent-node or phone proof.

The optional node companion supports HTTP keep-alive and TCP_NODELAY. A real
authenticated same-host loopback benchmark on macOS/x86-64 used 20 warmups and 200
samples: p50 **425.779 µs**, p95 **520.665 µs**, p99 **643.717 µs**, maximum
**960.535 µs**, population deviation **61.015 µs**. This includes application/auth/JSON
and actual OS/storage probe work. No physical NIC, one-way, load, TLS, RDMA, hardware
timestamp or HFT performance is qualified. See `scripts/benchmark-node-network.py`.

Original Python suites: **30 pass** (13 cyber, 5 node, 3 actual installed Terraform
local plan/apply tests, 9 crawler). Terraform tests exercise only disposable local
`terraform_data`; no cloud account or infrastructure deployment is implied.

Final namespace/protocol revalidation **passes in 26m03s**. It rejects ambiguous
trailing-underscore route IDs and classifies by enrolled protocol. Both Full APK
flavors and Review APK/AAB build. Across the targeted Android suites, **869 pass,
one opt-in SSH fixture test skips, zero failures/errors** (870 total cases). The
MCP suite has 120 cases; both app flavors have 130 each. The preceding combined
check covered unchanged common/GPU/federation/SSH/cloud/sandbox/network/inference
suites; the final check rebuilt/retested MCP and both app flavors and all artifacts.

Full lint: **0 fatal/errors, 350 warnings and 17 hints per flavor**. Review lint:
**0 fatal/errors, 353 warnings and 17 hints**. The two new KTX style suggestions
concern preference writes whose commit result is explicitly checked for durable
policy saves. Fatal lint remains enabled. Final Review static checks pass for
permission/service removals, legal asset/source agreement and native ELF 16 KiB
alignment; runtime/device/AAB/Play acceptance remains open. Older APK/lint evidence
above is historical and must not be assigned to this source revision. Live SSH's opt-in fixture check remains explicitly skipped
without `MESHLIT_LIVE_SSH_DIR`. No physical or live-provider acceptance is inferred.

Final check logs: `continuation-current-final-build2.log` (combined pre-fix source)
and `federated-protocol-final-build2.log` (final affected source). Failed/interrupted
attempts remain in the local work directory and are not success evidence. Final
source did not change after validation except documentation. The separate HyperL
branch `cdb06b6` passed 27 core-gpu checks, including actual host Clang execution;
that pre-merge validation does not qualify GPU/NPU/RISC-V/kernel hardware. The
owner subsequently merged its source into `main` through PR #1 at `851576b`.

## Owner-merged HyperL revalidation and standalone delivery — 2026-10-08

Merged source `851576bdd0c9daaea71f32f5daac8720671c6453` passes the combined
local JVM/APK/AAB/lint check in **1h 2m 52s**. Across 11 targeted suites:
**878 cases, 877 pass, one SSH fixture skip, zero failures/errors**. Core-gpu now
has 32 cases including the eight HyperL foundation checks; the actual host Clang
check passes without a skip. Both Full variants and Review APK/AAB build. Full
lint remains zero errors, 350 warnings/17 hints each; Review zero errors,
353 warnings/17 hints. Final static Review manifest/legal/ELF checks pass.

The fresh artifact set identifies source `851576b` and includes the merged HyperL
CPU library without an Android app workflow or accelerator loader. The earlier
`cecd2d9` counts/artifacts above are historical. GitHub merge CI run 37691300013
also passes; the owner's earlier pending PR status is not a final failure.

Standalone [HyperL](https://github.com/sabbirimon/HyperL) is separate, with a
published CLI/GUI alpha, memory-aware CPU admission and encrypted streaming
datasets. Its source `0203267` passes local 24 JVM, one C CTest and two installer
checks, skipping one actual GPU check because the host has no available OpenCL
GPU. Standalone CI run 37697211217 passes Ubuntu, Windows and macOS build/test
jobs. The owner requested a more polished UI and built-in developer tools; those
workbench/editor changes and detailed IDE plan evolve in the standalone repo.
Full SDK, real native mobile/accelerator, profiler/debugger, tensor/model and
distributed/telecom execution remain later gates. Physical-device tests stay paused.

## Reply presentation, token controls and search continuation — 2026-10-08

The owner resumed physical Samsung testing. The shared modern interface removes
the four app bottom tabs while retaining the searchable sidebar destinations.
Native bounded Markdown blocks, full reply reader, themed emphasis, preserved
emoji, selectable/wrappable code, tables and explicit-data charts replace the
single large reply card. The existing default local model, prompt/history and
streaming engine remain in place. Token settings persist per conversation; rates
use authoritative runtime counts and unknown SDK usage stays unknown.

Build 35 passes both Full app suites/APKs/lint and Play Review APK/lint. The
retained targeted JVM set has 856 cases with no failures/errors/skips. Two actual
USB UI tests pass in 27.701 seconds, covering reader/rendering controls plus
Cancel, Save, persistence, reopen and restoration. A separate fresh real model
UI test on build 34 passes in 92.336 seconds, including full pinned 105,454,432-byte
download/checksum, Load, Send and Unload. Details, hashes, individual wall times
and remaining gates are in `docs/DEVICE_TESTING_2026-10-08.md`.

Global/local chat search, imported article indexing, separate human/agent web
grants, fixed Brave API search and read-only approved device settings are added
in source. Manual remains the output default; Automatic cluster budgeting uses
only a recent authoritative native whole-cluster rate, the human ceiling and a
bounded context allocation. No rate is inferred from characters, memory, GPU
inventory or added device counts. Search and cluster contracts are passing in
build 36; final affected-source/UI validation is still recorded separately.

Standalone HyperL `dev` points to `67b30e0`. The owner's open PR #2 into `main`
has 28 successful checks, three skipped publication checks and none pending or
failed as observed at 15:29 UTC. It is unmerged; installer build success is
distinct from signing, notarization and actual platform installation.

Final build 37 passes the affected-source JVM/APK/lint set in 27m25s. The retained
targeted set totals 887 cases without failures/errors/skips, with unchanged
common/GPU evidence explicitly retained. Three actual Samsung USB UI tests pass
in 41.072s, including eleven local search checks; the clipped Web category from
the failed build-36 attempt is corrected with wrapping chips. A fresh real model
UI test on the same V2 build passes in 87.193s, verifying all pinned model bytes,
Load, non-empty local generation and Unload. Review static checks pass; live web,
remote settings, physical cluster, microphone and new wireless tests remain
separate gates. Exact artifacts, timings and limitations are recorded in the
device ledger. Human grants and the owner's selected chat/options are preserved.

## HyperL alpha.6, Core Candidate and bidirectional SSH — 2026-10-09

Build 38 integrates the separately licensed `core-hyperl` alpha.6 module with four-ABI
native C99 preprocessing, precise sum, twelve recipes and bounded encrypted
dataset import/verification/rekey/export. The human-only workbench is optional;
ordinary local model, prompt/history, streaming and modern reply UI remain in place.

Core Candidate installs independently from Experimental and blocks advanced
operations, hosted routes and model-directed tools. Shared inactive dependencies
remain, and development SDK network behavior is still a production privacy gate.
The owner's ordered three-stage/device-category qualification plan and supplied
architectural evaluation review distinguish implemented behavior from proposals.

Experimental supports both SSH directions: encrypted persistent host identity,
public-key scopes, private/loopback human-started lease and bounded JSON commands;
outbound profiles retain mandatory pins and explicit agent action allowlists.
Committed default-off VM agent permission, immediate revocation, registered work
cancellation, global/profile/remote checks and bounded local tool plans are wired.
Agents cannot configure keys/permissions or use human root/app-diagnostic paths.
A compatible installed runtime, trusted bootable guest and verified guest identity
remain necessary; no VM is bundled or automatically installed.

Final affected-app validation passes in 16m18s; final doc/notice packaging in 2m04s.
Retained targeted JVM evidence totals 894 cases: 893 pass, one older optional
external SSH fixture skip, zero failures/errors. Real TCP SSH denial/revocation/Stop
tests pass, as do host JNI, sanitized C contracts/4,096 seeded cases, nine crawler
cases and provenance/feature checks. All three APKs/lints pass with zero errors;
warning/hint counts and exact hashes are in docs/VALIDATION_BUILD_38.md. Core is
non-debuggable, with separate package/consent/keys and the expected three services.
Developer signatures, full pinned model/notices/policy/guide equality and four-ABI/
24 ARM64 ELF plus ZIP 16 KiB packaging pass. Production signing and device runtime
acceptance are not asserted. Notices version 2026-10-09.1 require fresh human
acceptance without changing optional grants.

ADB has no connected device: build-38 instrumentation compiled but was not run.
Samsung build-37 proof remains historical. Windows/Xiaomi configuration, physical
SSH/VM/lifecycle/thermal/privacy, multi-phone recovery and Internet client tests
remain gates. Final universal installers/test APK are kept outside the checkout;
0.963 GiB of duplicate generated ABI installers was reclaimed. No source, owner
keys, model assets or earlier retained fallback installers were deleted.

## Paused source backup — 2026-10-12

Owner authorized a GitHub checkpoint on `codex/paused-peering-ui-2026-10-12`, separate from main. This saves the incomplete Android/desktop continuation and future build plan; it does not qualify or release the current changes. See docs/PEERING_UI_HANDOFF_2026-10-12.md.

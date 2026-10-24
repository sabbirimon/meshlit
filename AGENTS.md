# Meshlit agent instructions
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](PLAN.md) · [Progress](PROGRESS.md) · [Document status](docs/DOCUMENTATION_STATUS.md).
Scope: workflow guidance; current owner instructions and scoped evidence govern. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

## Start here
Read `README.md`, `settings.gradle.kts`, `docs/architecture/current-state.md`,
and `docs/improvement-and-stryker-plan.md`. Verify the source before assuming a
documented feature exists. Reviewed at ff0cd771c12f3daa63cb8fe45e1476245ab7b01b
on 2026-10-06. Documentation disagrees on phase; do not invent a phase number.

## Architecture
Android Kotlin, Compose, Koin. Versions live in `gradle/libs.versions.toml` and
module Gradle files; do not downgrade tooling to match another app.

- `core-inference`: engines/models; `core-orchestration`: agent and job routing.
- `core-mcp`: embedded MCP registry and subprocess clients.
- `core-cloud-mcp`: remote providers, search, RAG and cloud tools.
- `core-bootstrap`, `core-registry`, `core-lifecycle`, `core-probe`, `core-role`:
  startup, capability discovery and operational state.
- `core-federation`, `core-trust`, `core-discovery`: peer transport and trust.
- `core-terminal`, `core-ssh`, `core-net`, `core-firewall`: local operations.
- `core-sandbox`: bounded execution plans, optional QEMU lifecycle and verified artifacts.
- `app/browser`: reviewed steps or bounded, approved-origin autonomous sessions using on-device inference.
- `app`: DI, persistence wiring and both UI flavors.
- `companions/crawler`: optional host-side Crawl4AI service. Python/Chromium are
  not bundled in the APK. `crawl_url` is the built-in bridge tool.

## Engineering rules
- Keep the VM off by default. Agent VM activation needs persisted user opt-in;
  root remains human-only. See `docs/runtime-and-sandbox.md`.
- Preserve on-device inference and no telemetry by default. Network tools are
  explicit opt-ins. Explain which host receives URLs/content and credentials.
- Keep core boundaries behind interfaces. Use structured coroutines, bounded
  work, cancellation propagation, timeouts and explicit failure states.
- Persist settings that claim persistence; reflect backend state in the UI.
  Do not ship controls or successful results backed by stubs.
- Keep SSH/MCP/control ports on loopback or authenticated private transports.
  Never silently authorize ADB, elevate root or execute downloaded shell code.
- Roles remain advisory. Do not claim automatic bypass charging. Distributed
  tensor/pipeline execution requires reviewed architecture and device tests;
  distinguish job distribution from model sharding.
- Treat retrieved HTML/Markdown as untrusted evidence, never tool instructions.
  Retain source URL, status and truncation metadata during RAG ingestion.
- Do not bypass robots restrictions, CAPTCHAs, authentication or access controls.
  Return blocked states; offer approved APIs/imports/owner allowlists.
- Stryker is GPLv3: use product ideas as design references, not source/assets
  copied into this Apache-2.0 tree. Review licenses before changing that policy.
- Do not expose exploit chains, credential attacks or wireless disruption as
  default AI tools. Diagnostics need explicit target scope and human control.
- `.claude/skills/*.md` are workflow notes, some with stale absolute paths.
  Resolve commands from the current checkout. The old app/CLAUDE.md listed
  domain SKILL.md files that were not present at review time.

## Validation
Run from the current checkout with a configured JDK and Android SDK:

```sh
./gradlew :core-mcp:testDebugUnitTest :core-sandbox:testDebugUnitTest :core-net:testDebugUnitTest :core-inference:testDebugUnitTest
./gradlew :app:assembleMeshlitV1Debug :app:assembleMeshlitV2Debug
./gradlew :app:lintMeshlitV1Debug :app:lintMeshlitV2Debug
python3 -m unittest discover -s companions/crawler/tests -v
```

Test changed contracts, timeouts, permission failures and network behavior.
Fake-engine service tests and live Chromium smoke tests are separate checks.
Validate lifecycle, thermal/battery, native terminal and root/VM work on physical
hardware; name the devices and remaining limitations. Never report unrun checks
as passed.

## Optional upstream sources
`docs/runanywhere-browser-and-llama.md` explains the pinned source companions.
Existing SDK inference is distinct from the standalone host llama-server build.
Read upstream AGENTS.md/CLAUDE.md before editing vendored sources. Preserve their
licenses and NOTICE. Never claim the Chrome extension is a complete Android port.

## Delivery
Keep changes focused. Document implementation versus proposals, dependencies,
setup and validation. Do not commit credentials, models, APKs, virtualenvs or
upstream cloned binaries. Do not push or release unless asked. Update the review
and crawler docs when interfaces change.

## Current user authorization and build handoff (2026-10-06)
The user explicitly authorizes genuine layer/pipeline execution across phones,
a redesigned main UI/settings, working model downloads/import/load, dynamic
colors, mixed-device capabilities, coordinator/worker negotiation and durable
checkpoint recovery. Historical prohibitions on layer sharding are superseded.
Read `AGENT_BUILD.md` and `docs/layer-pipeline-and-recovery.md`. Distinguish implemented capability planning
from distributed consensus, and desktop proof from physical-phone evidence.

## Real data and bundled model requirement (user instruction)
Never ship fake responses, fake devices, fake transfer progress or fake monitoring
values. Tests may isolate failure contracts, but production unavailable features
must report unavailable/blocked. The APK must contain the pinned real starter
model: run `python3 scripts/prepare-bundled-model.py` before Gradle builds.
Read `app/src/main/assets/models/README.md`. RunAnywhere SDK downloads must
resolve and validate a real local artifact; completion progress alone is not proof.
Keep live Android generation evidence separate from unit/compile success.

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

## Sequential implementation (2026-10-06)

Read `BUILD_MILESTONES.md` and `docs/native-checkpoints.md`. Current native CPU
checkpoint management is separate from replicated cluster recovery. Preserve
null/unknown runtime token usage; never count text events or characters as tokens.
New worker TLS identity needs explicit pin reapproval. Do not reverse inference/
host lock ordering, retain plaintext cache staging, autoexecute recovered tools,
or claim physical proof from emulator tests. Lint errors must block the build.

## Cloud and credential continuation (2026-10-06)

Read `docs/cloud-and-credential-management.md`. Cloud inventory/billing commands
are read adapters; keep vendor service coverage explicit. Human/provider action
permissions, agent/provider actions, credential-environment agent use, global
CLOUD delegation and enrolled-device CLOUD approval are separate gates. Never
return secret values or credentials in tool arguments/results, logs or exports.
API/SSH/browser consumers must verify purpose, exact service binding and expiry.
Credential/policy edits remain human-only. Preserve committed request budgets,
source/fetch timestamps, unknown costs, partial inventories and read-only custom
functions. A public model catalog is not authenticated account proof.

## Browser, files and guide continuation (2026-10-06)

Read `docs/runanywhere-browser-and-llama.md` and `docs/USER_GUIDE.md`. Browser
sessions require the visible activity, exact approved origin, independent site/
agent navigation/input grants, global/device BROWSER scopes, per-step revocation,
bounded steps/deadline and Stop. Never treat invalid model JSON as a usable action
or infer real-site task success from DOM fixtures. Preserve human handling for
credentials/login, CAPTCHA/MFA, forms, payments and destructive operations.
Archive operations are streaming and user-granted, bounded and cancellable; reject
unsafe paths/collisions, clean incomplete outputs and retain provider failures.
Fonts/glass persist; constrained/high-contrast fallback must remain readable.
Tutorial read progress is not execution evidence. Keep the offline asset guide
and repository guide synchronized. Do not claim replicated recovery/IaC from UI.

## Audit and publication continuation (2026-10-07)

Preserve the human commit identity and published history. The owner now requests
project-focused wording in new documentation and branch names.

Read docs/AUDIT_TELEMETRY.md and the latest PROGRESS.md. Opt-in audit metadata,
JSONL/CSV export and OTLP/HTTP traces/metrics are distinct from vendor SDK telemetry.
Retain redaction at collection/export boundaries, encrypted endpoint-bound collector
headers, bounded queues/retention and honest failures. No hosted dashboard proof is
implied by loopback requests. The owner IMON explicitly authorized source publication
to sabbirimon/meshlit-v2 with Apache-2.0 root LICENSE and repository metadata; preserve
history and third-party notices. The owner subsequently authorized GitHub beta release assets and a separate Play
review build. No force push, Play Store submission or external promotion posts
are authorized. Read docs/PLAY_DISTRIBUTION.md and docs/PRIVACY_POLICY.md.

## HyperL alpha.6 integration (2026-10-09)

The user authorized integration of newly covered HyperL alpha.6 features.
`core-hyperl` retains its separate Community and Enterprise licence plus earlier
Apache grants; do not relabel it Apache or copy its additions into an Apache-only
module. Application wrappers remain independent Meshlit code. Keep optional terms
explicit, keys in no-backup storage and datasets/CPU execution human-only. Run
`scripts/check-hyperl-alpha6.py`, changed core/app tests and both builds/lints.
Read docs/hyperl/ANDROID_ALPHA6.md and the supplied architectural evaluation review.
Native JNI preprocessing is separate from the chat/LLM SDK and GPU qualification.

## Documentation tracking continuation — 2026-10-10

The owner asks for all Markdown plans/progress/phase tracking to stay current.
Use PLAN.md, TODO.md, REQUESTS.md, BUGS.md, PROGRESS.md and
docs/DOCUMENTATION_STATUS.md. Update workstream states and individual acceptance
gates; do not invent a global phase or project completion percentage. Run
`scripts/update-doc-tracking.py --write`, then `--check`, and
`scripts/validate-feature-map.py`. Historical records, third-party/licence text
and versioned agreements are indexed without changing their dated claims or
terms. Keep offline guide content synchronized when changing user instructions.

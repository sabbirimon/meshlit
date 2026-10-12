# Claude Code instructions
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](PLAN.md) · [Progress](PROGRESS.md) · [Document status](docs/DOCUMENTATION_STATUS.md).
Scope: workflow guidance; current owner instructions and scoped evidence govern. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Read and follow `AGENTS.md` at the repository root, the canonical shared guide.
Read `docs/improvement-and-stryker-plan.md` for the review and feature priorities,
and `companions/crawler/README.md` for crawler setup.

Use the current checkout and product-flavor Gradle tasks. Verify implementation
and test output before describing a feature as working. Preserve local-first
inference and explicit external-tool consent. Do not maintain a duplicate phase
tracker here.

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

## Audit and publication continuation (2026-10-07)

Read docs/AUDIT_TELEMETRY.md and the latest PROGRESS.md. Opt-in audit metadata,
JSONL/CSV export and OTLP/HTTP traces/metrics are distinct from vendor SDK telemetry.
Retain redaction at collection/export boundaries, encrypted endpoint-bound collector
headers, bounded queues/retention and honest failures. No hosted dashboard proof is
implied by loopback requests. The owner IMON explicitly authorized source publication
to sabbirimon/meshlit-v2 with Apache-2.0 root LICENSE and repository metadata; preserve
history and third-party notices. The owner subsequently authorized GitHub beta release assets and a separate Play
review build. No force push, Play Store submission or external promotion posts
are authorized. Read docs/PLAY_DISTRIBUTION.md and docs/PRIVACY_POLICY.md.

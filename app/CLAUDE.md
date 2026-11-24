# App-specific instructions

Read `../AGENTS.md` and `../CLAUDE.md`. The previous file referred to a missing
BUILD_GUIDE.md, nonexistent domain skills, and a stale phase.

- Keep Koin wiring in `src/main/kotlin/com/meshlit/di/CoreModule.kt`.
- Both meshlitV1 and meshlitV2 flavors must compile; shared screens affect both.
- Store tokens through core-trust encrypted storage. Never log bearer tokens,
  query secrets, prompts or retrieved page content.
- Disabling crawling must prevent future requests even when its MCP tool stays
  registered. UI state must describe persisted backend behavior.
- Foreground-service, root, native, thermal and battery changes need physical
  device validation. Report pending checks accurately.

## Current user authorization and build handoff (2026-10-06)
The user explicitly authorizes genuine layer/pipeline execution across phones,
a redesigned main UI/settings, working model downloads/import/load, dynamic
colors, mixed-device capabilities, coordinator/worker negotiation and durable
checkpoint recovery. Historical prohibitions on layer sharding are superseded.
Read `../AGENT_BUILD.md` and `../docs/layer-pipeline-and-recovery.md`. Distinguish implemented capability planning
from distributed consensus, and desktop proof from physical-phone evidence.

## Latest continuation priorities (2026-10-06)

Keep genuine phone layer sharding/model execution and committed recovery as the
main product priority. Nodes also contribute storage, tools, capture, sensors,
actuation, preprocessing, DSP, routing and monitoring according to real evidence.
ESP32/Arduino/IoT membership does not imply transformer execution.
Read `../docs/online-power-peripherals-and-configuration.md`,
`../docs/declarative-federation-roadmap.md` and `../docs/media-iot-and-radio-nodes.md`.
These record offline/online selection, free/public/gated model distinctions,
actual pricing provenance, power readings, hardware/vendor backend limits, open
source SSH, configuration transfer, media sources and owner-paired radio carriers.
Keep unsupported adapters visibly unavailable; never invent devices, samples,
throughput, tokens, costs, account entitlements or recovery success.

## Scenario routing and media continuation (2026-10-06)

Read `../docs/model-router-and-media.md`. Scenario recipes, sequential chains/comparisons,
chat text attachments and explicit online vision/image/speech/video adapters are
wired in source. Validate real execution; paid provider media outputs and generic
music/sound/on-device diffusion/video remain separate gates. Do not substitute
text event counts for native tokens. Keep phone layer execution/recovery primary.

## Custom models and training continuation (2026-10-06)

Read [../docs/local-model-behavior-and-training.md](../docs/local-model-behavior-and-training.md). Custom local prompt behavior is default-off;
compatible custom weights are imported through Models. No toggle changes learned
refusals or hosted-provider rules. Settings → Fine-tuning drives the optional
Soup 0.75.0 POSIX host companion via pinned SSH, with real job/status/log/cancel
contracts. Android synthetic gradients are removed; phone autograd reports
unavailable. Actual Soup training, adapter quality/evaluation and GGUF deployment
remain acceptance gates. Keep phone inference layer sharding/recovery primary.

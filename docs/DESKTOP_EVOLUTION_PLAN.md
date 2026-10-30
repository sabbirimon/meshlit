# Desktop evolution, settings and cross-device plan
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Requested and planned 2026-10-31, before implementation. This plan extends the
voice work; it does not replace the native inference and device-compute priorities.
Each acceptance gate is independent. A source change, health response, unit test,
installed app and real device execution are different evidence.

## Why, where and how

| Need | Why it fits Meshlit | Where | Implementation and acceptance |
| --- | --- | --- | --- |
| Persistent useful memory | Avoid repeatedly sending entire histories; make retained context inspectable | Studio workbench, existing protected knowledge architecture; mobile PersonalMemory | Default-off, project-scoped encrypted notes, bounded search, provenance, preview, forget and explicit local/remote recall. Test corruption, persistence, isolation and revocation. Optional claude-mem connector preserves upstream capture/search features. |
| Agent computers and multi-agent work | Let desktop companions supply environments Meshlit does not bundle | Studio Services and existing MCP dispatch | Configure owner-installed OpenMausBot/OpenBot, check exact health contracts, open their complete UI, attach supported MCP through existing per-call review. Preserve upstream auth, setup and licensing. Do not invent an OpenBot MCP or confuse generic health with authenticated readiness. |
| Understandable settings | Current workbench Settings opens generic editor preferences; mobile Advanced is a long flat list | Dedicated Studio Settings hub; shared Android modern settings | Group by task, searchable descriptions, distinguish editor/product settings, reveal advanced controls without deleting access. Keep existing routes and saved settings. Test every catalog route and trace changes to persistence/backend. |
| Context preview | People should see what memory may accompany a request and where it goes | Studio memory/chat | A model-independent context preview with source/character bounds; actual runtime token counts only when available. It is inspired by controlled conversation state, not OpenAI proprietary compaction. |
| Action receipts | Review should lead to an understandable result and Stop | Existing reviewed agent tool dispatcher | Record bounded non-secret action metadata and outcomes, including denied/failed/cancelled. Do not persist raw prompts, keys or tool arguments in audit metadata. No automatic replay after restart. |
| Engine and HyperL harmony | Devices need consistent admission and honest capabilities | shared-workspace, desktop-engine, core-inference, core-hyperl, desktop-hyperl | Audit existing portable contracts, run both platform tests and native CPU checks, fix mismatches. Keep LLM inference distinct from HyperL numerical kernels. Unsupported GPU/NPU/OS/device paths remain unavailable. |
| Vendor choice | Models, speech and tools should not require one company | Existing local and compatible provider adapters | Include Meta Llama, OpenAI, Google, Anthropic, Mistral, DeepSeek and open runtimes as clearly described routes. Provider protocol, model format, license, installed backend and modality each require compatibility. No claim that every model works or that public weights imply free hosted inference. |
| Complete distributable builds | The newer workbench currently depends on absolute development paths | Studio packager, Gradle builds, platform CI | Preserve existing packaged Studio, build current workbench explicitly as development until portable dependencies/licenses are bundled and relocation-tested. Produce current macOS and Android artifacts; Windows/Linux/ARM qualification requires target runners. No false universal installer label. |
| Detailed manual and settings verification | A visible switch is not proof of implementation | User guide, offline help, audit evidence | Document setup, data destinations, persistence, prerequisites, failure recovery, Stop and limits. Inventory all settings destinations and mark source-reviewed/tested/device-tested/unavailable separately. |

## Upstream decisions

Reviewed sources and revisions (runtime installation is separate):

| Project | Reviewed revision | License at revision | Use |
| --- | --- | --- | --- |
| [claude-mem](https://github.com/thedotmack/claude-mem) | `70aa40042cfb8157a0d84954b9c0d2b30b50af80` | Apache-2.0 root license | Optional installed worker/MCP, persistent observation search/timeline/details and upstream viewer. No automatic cloud observer or paid fallback. |
| [OpenMausBot](https://github.com/milind-soni/OpenMausBot) | `21628af229abe8100cdd1f8761ccca391ff56bff` | Apache-2.0 | Optional full UI and documented installed `openmausbot mcp`; preserve paired token/port and upstream approvals. |
| [OpenBot](https://github.com/CopilotKit/OpenBot) | `743649efc3f21d7bf0badbe6af2bcd5daedd97ce` | MIT | Optional full source-template UI/API. Docker, Bun, Postgres, models and CopilotKit Intelligence configuration remain upstream prerequisites. AG-UI is not MCP. |
| [Impeccable](https://github.com/pbakaus/impeccable) | Guidance read 2026-10-31 | Apache-2.0 | Apply Operate-mode hierarchy, native controls, readable state and keyboard access. No downloaded hooks/telemetry needed. |

These are integration boundaries, not a promise that a settings link reproduces
every upstream feature. Full upstream applications remain optional and independently
configured; individual Meshlit adapters must state which operations they implement.
Future upgrades must revalidate protocols and licenses against a new pin.

## Review findings and feature decisions

- Keep native Android inference, existing optional local desktop backend, usage
  ledger, permission gates, SSH, MCP, paired hosts and real voice adapters.
- Reuse protected knowledge and explicit personal memory. Do not advertise the
  unused in-memory core-agent-memory scaffold as durable production memory.
- Consolidate vendor navigation under Models/Services. Preserve commands and
  settings; remove misleading duplication, not user data or working features.
- Keep standalone agents and phone/browser automation under their existing
  grants. Installing a companion does not grant it device or workspace authority.
- Distinguish editor preferences from Meshlit settings. Distinguish saving a URL
  from connecting to a service and an endpoint response from model readiness.
- Correct stale architecture/build documentation, but retain dated historical
  evidence. Do not change third-party license text or overwrite previous voice work.

## Desktop/mobile contract

Desktop is the primary surface for memory, editor workflows and optional isolated
computer companions. Android receives grouped searchable settings, manual updates,
existing encrypted memory and supported paired-host operations. Docker, Electron,
desktop SDKs and desktop VM images are not Android capabilities. A phone can be a
client or approved node without executing every host feature.

Cross-device compatibility is capability-based: OS/ABI, installed runtime revision,
model artifact/format, available memory, operation support, trust and permission.
No device name implies compute eligibility. No automatic backend substitution or
claim of combined GPU/LLM execution follows from HyperL kernel support.

## Distinctive model-independent features

1. **Context preview:** see bounded project memory selected for a request; separate
   local and online permission. Keep unknown token counts unknown.
2. **Reviewed action history:** inspect actual outcomes after approval and stop
   ongoing work through existing authority. No recovered side effects auto-run.
3. **Capability-first setup:** routes explain on-device, local-host or online
   execution and the prerequisite preventing an unavailable option from working.

OpenAI references are inspiration for conversation state and human review:
[conversation state](https://developers.openai.com/api/docs/guides/conversation-state),
[guardrails and approvals](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals).
Meta models follow their own model terms and backend compatibility:
[Llama models](https://github.com/meta-llama/llama-models).
Portable runtime references include [llama.cpp](https://github.com/ggml-org/llama.cpp)
and [ONNX Runtime](https://github.com/microsoft/onnxruntime). These references do not
add a new runtime, account entitlement or hosted provider by themselves.

## Ordered implementation and verification

1. Inventory source/docs/settings and record defects and coverage. Architectural
   review is not a claim of reading or executing every line of the repository.
2. Implement searchable Studio Settings/Services and grouped Android settings.
3. Add protected project memory, context preview and reviewed-action receipts.
4. Add optional upstream connections with bounded health checks and supported
   MCP attachment. Keep unavailable prerequisites visible and non-destructive.
5. Run affected contracts, all desktop/workbench regressions, core-engine/HyperL
   checks, both Android test/build/lint gates and mandatory bundled-model integrity.
6. Build current desktop/Android artifacts, inspect rendered settings and verify
   native execution where available. Record hashes, target, signing and limits.
7. Update detailed manual, offline help, PLAN/PROGRESS/TODO/REQUESTS and tracking.

## Acceptance limits that must stay visible

All-settings runtime verification needs configured providers, OS grants and actual
devices; absent prerequisites remain untested or unavailable, never passed. Live
paid services, full upstream installation, clean-machine portability, Windows/Linux
and additional GPU/NPU/phone hardware require their own evidence. No credentials
are requested in chat, no model weights/binaries enter git, and no release is
published by this work. Exact ChatGPT allowance/remaining chat tokens are not
available to this agent; compact updates and saved progress reduce repetition.

## Native implementation choices

The owner additionally requested Rust/C++/C# where useful. Existing llama.cpp and
HyperL native CPU paths already use C++. Preserve their narrow ABI and testable
contracts; do not rewrite them based on a language-performance assumption. Rust
is a candidate for future bounded host/transport services with measured need.
C# is a potential Windows-specific adapter, not a new cross-device requirement.
Changes must be benchmarked with real workloads and retain model/license boundaries.

## Reported defects and owner priority

The owner reports chat tools cannot be enabled and Android live conversation starts
but does not hear or reply. These are tracked independently from playback tests.
Settings/routing audit follows the requested repository integrations; previous
voice playback success does not close the microphone-to-reply acceptance gate.

# Current user requests and implementation tracking
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](PLAN.md) · [Progress](PROGRESS.md) · [Document status](docs/DOCUMENTATION_STATUS.md).
Scope: cross-platform tracking; feature and device gates remain separate. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

## Save source and documentation — 2026-10-24

The owner requested saving Meshlit progress and pushing it to Meshlit GitHub, then asked for documentation coverage comparable to HyperL. [The paused checkpoint](docs/PAUSED_CHECKPOINT_2026-10-24.md) links build/run instructions, implemented fixes, incomplete evidence, prioritized pending work and agent handoff. Existing PR #4 is the review destination. No new voice work, APK build, device operation or release is authorized by this save step.


## Owner continuation requests — 2026-10-24

- Latest direction: pause voice/Meshlit builds and ship HyperL first. Preserve
  current fixes and documentation; final APK/lint/device acceptance stays pending.

- Natural voice input/output and audio conversation subtitles: Live / One turn,
  visible activity, mute, tap-to-interrupt and phrase captions are in source;
  physical acceptance and native realtime/full-duplex adapters remain separate.
- Claude-Red native skills usable by people or supported LLMs: eight independent
  defensive references now share Android/Studio content and Android read-only MCP
  tools. Full upstream attack-skill import and blanket firewall/security weakening
  were not implemented. Existing scoped lab execution grants stay in force.

- “Do all”: continue the ranked unfinished feature/qualification backlog; no blanket completion claim.
- “Also improve HyperL”: review execution, resource admission, cancellation and measured performance. The current source adds backend-correct admission and bounded repeated CPU measurements; mixed accelerator execution remains a separate workstream.
- “Note every progress and how do u fix in proper documents”: keep cause → implementation → test evidence → remaining gate in PROGRESS.md, with matching feature docs and offline user guide. Record failures as well as successes.
- Samsung is reserved for this Meshlit testing session; do not interfere with the concurrent Kandari phone work.
- The owner selected both offline and online vision, then deferred online testing: “ill test online later.” Continue offline qualification now; leave actual provider vision acceptance open for the owner. Do not request credentials in chat or count deferred online testing as passed.

## Major-impact-first continuation — 2026-10-24

The owner additionally requested natural live voice input/output similar to current voice assistants, where hardware supports it. Existing `HandsFreeVoice` performs bounded sequential recognition → chat → synthesis and has manual Stop; it does not yet implement simultaneous listening/speaking or acoustic interruption. Acceptance must include visible state/input activity, microphone ownership, immediate stop, interruption and echo handling, local-model memory/latency checks, lifecycle cleanup and a real spoken conversation. Preserve an offline turn-taking fallback; online live adapters require separate provider configuration and acceptance. The owner's online vision test remains deferred.

The owner requested remaining work ranked by impact/complexity and a simpler UI like Kandari. Implementation and remaining gates are recorded in the [high-impact backlog](docs/HIGH_IMPACT_BACKLOG_2026-10-24.md). Photo/model selection and Send/Stop are being unified before claiming general media reliability or Studio parity.

## Added owner requests — 2026-10-23

The owner also asked to reconcile the older Android Studio `mllm` plan/request/TODO files. [The request audit](docs/OLD_ANDROID_STUDIO_REQUEST_AUDIT.md) maps older R-01–R-23 plus open Auto Pilot, voice animation, model import, persona/weights, Ghostty and training requests to this checkout's source and remaining acceptance. An older checkout's “done” status is not inherited by this APK.

The owner asked for Android and Studio/desktop/server/container feature alignment, a dedicated multi-device chat, device radar and approval dashboard, customizable Auto/On/Off privacy controls, parallel agents, improved Models/MCP/Hooks, illustrated help, and cross-device takeover resistance. Source-backed Android and Studio changes plus open acceptance gates are listed in [the redesign plan](docs/REDESIGN_PLAN_2026-10-23.md). An experimental host-client CLI status report is not a server/container daemon; single-peer P2P is not multi-member chat. Public GitHub release and a fully tested app remain requested delivery gates.

Updated 2026-10-10. Historical request text/task IDs remain in docs/history/REQUESTS-before-studio-2026-10-10.md. This ledger maps the current combined desktop/server and Android direction to active plans and acceptance.

| Request group | Implementation and evidence | Remaining scope / tracker |
| --- | --- | --- |
| Strong, fast local core; Ollama-style lifecycle | Owned Intel CPU runtime, verified baseline/AVX2, memory/context identity, configurable CPU/batch/KV, lazy loading and idle unload; real native generation and four timing cases | Native Ollama lifecycle API, more backends and quality/lifecycle qualification; docs/desktop/ENGINE_AND_SHARDING.md |
| Better sharding across devices/memory tiers | Shared automatic/manual whole-layer proportions with per-worker headroom and fresh CPU offers; Android selected groups, live pinned-TLS approval and operational assessment | Built-in reverse relay, desktop RPC controller, actual heterogeneous/physical/Internet execution, memory/storage hierarchy and replicated recovery |
| Powerful desktop/server app with Android parity | 41-area tracker compares 43 old and 49 current Android menu destinations; seven categorized management groups and source reuse | Full desktop ports remain separately labelled; docs/ANDROID_DESKTOP_PARITY.md |
| Compact customized UI and search everywhere | Basic/Advanced differ; compact management cards and local global/settings/chat/model/node scopes; opt-in browser handoff | Durable article/RAG/remote-settings and agent browsing; docs/desktop/SEARCH_AND_MANAGEMENT.md |
| Models/HF/free/paid providers; compact built-in model | Desktop Qwen2.5 1.5B Q4_K_M, validated imports, HF metadata and bounded single-GGUF download source, compatible provider hosts | Resume, verified gated/paid entitlement, full model capabilities/lifecycle and Dolphin qualification |
| Devices, clusters, SSH, VPN/P2P/overseas | Desktop saved-node references, pinned outbound SSH, status-only inbound SSH and optional Ghostty; Android layer QR/manual approval with visual identity code and offline LAN/virtual-network addressing | Actual independent-device tests and automatic discovery; no full desktop inbound shell, automatic NAT reachability or native layer relay |
| Health, full task manager, power and cost | Real local CPU/RAM/process samples, charts/gauges/history and human scoped Stop source | Actual Stop acceptance, remote sensors, authoritative power/cost/billing and complete workload manager |
| Agents, permissions, hooks, MCP/A2A, full-auto PC control | Existing Android typed controls are reference; human desktop tools implemented | Common desktop authority/controller/audit bridge and real jobs; no privilege escalation from model output |
| Crypto/security/firewall/capture/VM/container/Kubernetes | Local crypto, SSH/TLS boundaries and human terminal; separate pending categories | OS-qualified native controllers, permissions, isolation, rollback and actual runtime tests |
| Memory/personality/voice/GibberLink | Android source/earlier native PCM evidence; desktop local response instructions | Desktop adapter ports, actual ASR/TTS/live conversation/audio delivery and user/agent grants |
| HyperL built in | Separately licensed native CPU recipes and precise reduction pass both recovered installers; Studio custom bounded graph editor compiles | Custom graph installed UI/device check and GPU/NPU/full-model/distributed execution remain separately unqualified |
| Rewards, fraud defense and monetary rental/exchange | Existing local self-signed hosting receipts remain nontransferable; [new plan](docs/REWARD_METERING_AND_SETTLEMENT.md) specifies service meters, reciprocal proof, offline reconciliation and fraud/settlement stages | Native metering, independent verification, durable balanced ledger, legal/payment provider review and real transaction acceptance remain pending |
| Production and Experimental separated | Restricted Android Core Candidate and Experimental connected paths remain separate | Candidate privacy/signing/device/fleet/OS qualification; no production certification |
| Intel DMG/PKG, GitHub, storage cleanup | Both installers built/extracted/payload-tested; source pushed, assets uploading; about 5 GiB duplicate scratch reclaimed | Remote hashes/publication and clean-machine acceptance; docs/RELEASE_EVIDENCE.md |
| Update every Markdown plan/progress/phase record | Active records reconciled and all tracked Markdown classified in documentation index | Maintain generator/check at each future change; docs/DOCUMENTATION_STATUS.md |

Plans and phase labels do not establish completion. See PLAN.md, BUILD_MILESTONES.md, TODO.md, PROGRESS.md and docs/DESKTOP_BUILD_LOG.md for current facts and next gates.

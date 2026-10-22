# Current user requests and implementation tracking
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](PLAN.md) · [Progress](PROGRESS.md) · [Document status](docs/DOCUMENTATION_STATUS.md).
Scope: cross-platform tracking; feature and device gates remain separate. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

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

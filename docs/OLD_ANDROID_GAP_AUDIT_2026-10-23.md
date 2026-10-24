# Old Android Studio project to current Meshlit: gap audit
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Source: `/Users/code/AndroidStudioProjects/mllm` (`PLAN.md`, `REQUESTS.md`, `TODO.md`, code) compared with this checkout on 2026-10-23. The old files mix implemented code, unfinished UI, and future proposals. A row below describes delivery in the current app, not proof that the old APK performed the feature correctly. See [reuse review](old-android-reuse-review.md), [current feature map](../FEATURE_MAP.md), and [acceptance backlog](PENDING_FEATURES.md).

| Area requested or present in old project | Current app | Remaining work |
| --- | --- | --- |
| Local chat and models | RunAnywhere local chat, verified starter, managed GGUF imports/downloads and loading | Device quality, lifecycle and large-font tests; no guarantee every advertised model fits or answers well |
| Model sources | Device files, HTTPS and Hugging Face GGUF discovery exist | Original requests for GitHub code/model imports, FTP and arbitrary provider formats are incomplete; gated entitlements and paid download workflows need proof |
| Model personalities and tuning | Saved chat behavior and host LoRA job controls exist | Per-model persona/skill library, local adapter merge/load and phone training are unfinished |
| Voice, camera and media | Offline voice-pack import and sample, image/camera paths, explicit online adapters | End-to-end speech turns on Xiaomi, live camera understanding on supported VLMs, speech model choices and media-device qualification |
| Agents and MCP | Saved agents, typed jobs, MCP registry/gateway and scoped remote routes | Full tool approval UX, durable runs/logs, revocation during work, account connectors and Studio parity; common-server catalog is host endpoint setup, not automatic installation |
| Phone autonomy | Accessibility actions, app allowlist, OpenClaw Android node and foreground notification | Physical phone action tests, Android background/restart behavior, per-operation consent and durable auditing; root/protected surfaces remain outside delegation |
| RAG and memory | Local manual recall/search; older in-memory vector store and placeholder remote adapter retained | Durable user-selected source ingestion, provenance, connector permissions and actual retrieval quality; a mode switch alone does not enable RAG |
| Cyber tools, Linux VM and terminal | Scoped Security Lab, QEMU artifact/guest configuration and bounded commands | Bundled/proven guest image, real boot/SSH/VNC on target phones, optional tool installation and complete terminal workflow; no turnkey Stryker-style chroot |
| Files, code and hooks | SAF file manager, offline code workspace and owner-authored hooks | Full IDE/debugger, provider-specific file edge cases, hook authority and audit on every execution path |
| Device discovery and pairing | LAN/BLE advertising, QR/manual paths, pending inbox, saved endpoints and history | Two-phone pairing acceptance, IPv6 hosting, fresh remote status, simultaneous requests, direct Internet relay and wider LAN device inventory |
| Mesh chat and native model sharding | Foreground peer chat and layer worker implementation | Durable group rooms/offline delivery, end-to-end Internet transport and two independent phones executing one heavy model across actual layers |
| Cluster planning and recovery | Resource offers, worker assessment, limited role planning, local checkpoints | Live remote device telemetry, owner-controlled auto resource use, replicated jobs/KV, failover/election and safe restart proof |
| Rewards and energy | Opt-in local signed receipts, contribution view, live battery readings and estimates | Monetary-grade measurement, cross-signed service proof, fraud/dispute handling, app-attributed energy and provider invoice reconciliation |
| Cloud management | Read-only AWS, Azure, GCP, DigitalOcean, OpenRouter and custom HTTPS adapters with encrypted credentials | More provider coverage, deployment controls, account billing acceptance, Terraform/Pulumi plan/apply workflows and mobile/Studio parity |
| Network diagnostics and repair | Some retained diagnostics, packet capture, firewall and configuration tools | Accurate capture state, HTTP observer, fleet configuration application, remediation/rollback and physical network tests |
| UI, settings and guides | Modern menus, Basic/Advanced, themes, search, device/radar/map views, offline guide | Screen-by-screen Samsung/Xiaomi layout and accessibility review; old animation/design requests and account flow parity are not all qualified |
| Studio/server/container | Intel Studio preview and host CLI status | Android feature parity, MCP/agents, live clustering, real container/VM/Kubernetes control, installers and other OS validation |

The old project's `PLAN.md` mentioned a power monitor controller and gauges that were absent from its checked source snapshot. `TODO.md` also marks several gateway and training items as planned. Do not describe those old notes as transferred features. The current beta APK is debug-signed and is not a production or Play Store release.

# Meshlit current implementation and acceptance queue
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](PLAN.md) · [Progress](PROGRESS.md) · [Document status](docs/DOCUMENTATION_STATUS.md).
Scope: cross-platform tracking; feature and device gates remain separate. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

## Paused Android/desktop peering continuation — 2026-10-12

[Future build plan](docs/PEERING_UI_BUILD_PLAN.md) · [Exact handoff and validation](docs/PEERING_UI_HANDOFF_2026-10-12.md). Implementation is partial and paused at the owner's request; resume from the saved working tree. Both platforms are in scope. Android Studio is the reference, not the target.


Updated 2026-10-10. This list tracks current user requests; older task IDs and phase claims are preserved in docs/history/TODO-before-studio-2026-10-10.md. A checked item is qualified only within the scope named here.

## Landed and locally checked

- [x] Capacity-aware shared CPU sharding admission, complete integer/manual layer proportions and weak optional-worker exclusion.
- [x] Intel SSE4.2/verified AVX2 engine selection, context/thread/batch/f16-q8_0 key-cache controls, memory admission, lazy human loading and idle unload.
- [x] Bundled Qwen2.5 1.5B starter; real recovered DMG/PKG generation, authentication denial and owned listener unload.
- [x] Seven compact desktop management groups, distinct Basic/Advanced menus and bounded global/settings/chat/saved-model/node search.
- [x] Real local monitor sampling/charts/process rows, bounded human batch terminal, scoped SSH source/contracts and native HyperL CPU recipe checks.
- [x] Current Full Android V1/V2 assemblies/lints and 521 valid JVM executions; existing warnings retained. Nine crawler contracts pass separately.
- [x] Source pushed and draft PR #2 updated; installer publication tracked independently in docs/RELEASE_EVIDENCE.md.

## Proposed stabilization milestone — planning complete, implementation not started

[Detailed acceptance plan](docs/desktop/STABILIZATION_PLAN.md) · [Build guide](docs/desktop/STABILIZATION_BUILD_GUIDE.md). These unchecked gates supersede the broad queue as the proposed next priority; prior evidence remains scoped to its original build.

- [ ] S0: freeze baseline inputs, measurements and disposable acceptance fixtures.
- [ ] S1: durable protected sessions and interrupted-turn recovery; engine Stop/reload; 20-cycle/two-hour lifecycle acceptance.
- [ ] S2: real import/download/verify/activate, resume validation, cancellation and storage failures.
- [ ] S3: timestamped/stale monitor data, bounded charts and real disposable-process Stop.
- [ ] S4: common action scopes, secret references, durable redacted audit, expiry/revocation and denied-action cases.
- [ ] S5: two independent devices with actual layer contribution, measured allocation/output/timing, peer loss and explicit safe retry.
- [ ] S6: installed-app workflows, separate local/cluster release gates and qualified bounded agent adapters.

## Immediate acceptance

- [x] Intel Experimental release published; all eight GitHub asset names/sizes/states/SHA-256 digests match local files.
- [ ] Clean-machine Intel installation/Gatekeeper acceptance, lifecycle/leak/crash/cancel/idle stress and wider model quality tests.
- [ ] Real HF verified download/cancel/storage failure acceptance; resume/gated entitlement work is separate.
- [ ] Actual desktop process-stop and remote interactive SSH/Ghostty acceptance.
- [ ] Reconnect owner-approved Samsung; test current Android build, then Xiaomi and Windows device categories.

## Core and desktop/server implementation

- [ ] Desktop live-worker RPC controller and explicit worker enrollment; independent-device/oversized model/memory/latency/peer-loss proof.
- [ ] Replicated task/session journal, fencing/quorum and compatible distributed KV recovery.
- [ ] Common desktop authority, durable audit and real agents/MCP/A2A/hooks/runners.
- [ ] Optional long-term memory/personality and both offline/online voice adapters, including separate Dolphin speech/chat qualification.
- [ ] Durable chats and document/RAG workspaces; exact sources and independent embedding backends.
- [ ] Native Ollama tags/pull/show/create/ps/keep_alive lifecycle and versioned LM Studio APIs; current compatible hosts are not complete app parity.
- [ ] OS firewall/packet capture, qualified VM/container/Kubernetes controllers and governed agent operations.
- [ ] Cloud credential vault/billing, measured power/electricity/hardware costs, bounded repair/reset/rollback and signed updates.
- [ ] Windows/Linux/Apple Silicon hardware packages, actual NEXT DevEco/HAP/device and iOS implementation/qualification.
- [ ] Production-channel privacy, signing/notarization, dependency review, clean-target and physical fleet gates.

All detailed categories, statuses, source anchors and missing controls: docs/DESKTOP_FEATURE_TRACKER.md. Do not mark an entire category done from a menu, unit fixture, same-host trial or dependency.

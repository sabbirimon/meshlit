# Current regressions, limitations and verification
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-06: [Plan](PLAN.md) · [Progress](PROGRESS.md) · [Document status](docs/DOCUMENTATION_STATUS.md).
Scope: cross-platform tracking; feature and device gates remain separate. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

## HyperL engine priority — 2026-11-06

The owner moved the custom model engine into standalone HyperL; Studio will be
its application client. [Engine plan](docs/STUDIO_HYPERL_ENGINE_PLAN_2026-11-06.md)
separates specialist-model routing, native MoE expert paging, layer sharding and
cloud API calls. Plan-first, bounded implementation and focused tests take priority
to conserve credits. Agreement onboarding is explicitly deferred until engine work.

HyperL's first increment extends its existing residency planner with exact total/
active expert metadata, shared/router weights and cache accounting. All experts
remain counted; this does not execute expert paging. Standalone checks: 13 focused,
84 Python passed / 5 optional JAX skipped, Gradle distribution build successful,
5 native tests passed. Existing HyperL native CPU/RPC model evidence is separate.

Studio now has compact SVG navigation, a native Studio menu and source support for
a second desktop-only Qwen3-4B Q4_K_M reasoning model. The verified artifact is
2,497,280,256 bytes; Qwen2.5-1.5B stays the default. Integration checks: 225 Node,
46 packaged Python, 17 focused desktop backend tests passed. Native Qwen3 loaded
and answered 2+2 correctly after idle build daemons released memory; preserve the
memory admission guard. Native Thinking off returned 4 for 2+2; Thinking on returned 8 for 3+5;
Stop closed a further active request. Package provenance/launch evidence is
recorded separately. No Android model/default change.

An isolated AMD560X prototype passed 11 numerical cases and produced the same
short text as CPU with 4/29 layers on GPU. It was slower (0.75 versus 11.23 decode
tokens/s in that short probe), so it is not promoted into the app. See
[hardware evidence](docs/desktop/evidence/studio-amd560x-partial-offload-20261106.json).
Actual tiered MoE inference, efficient GPU kernels and the new Studio→HyperL client
remain pending; no general throughput or production claim is made.

Obsolete QA bundles, an old QA DMG, the abandoned Qwen3.5 partial and regenerable
Android intermediates were removed. Current/rollback builds, verified models,
final APKs/reports and user data were preserved. APFS shared blocks/snapshots mean
logical deleted bytes do not equal immediately reclaimed space; use the
[cleanup record](docs/desktop/evidence/build-storage-cleanup-20261106.json).

## Verified delivery checkpoint — 2026-11-06

Bots is a primary General Chat sidebar destination. The dashboard creates and
edits reusable bot profiles, assigns local or saved cloud/device/cluster model
connections, and opens per-bot Details in a separate native window. Starting
chat from detached Details keeps that chat in the detached window; General Chat
remains open. Details shows actual model/source, instructions, capabilities and
links to workspace agents, teams, companions, connectors and permissions.
Native window creation and chat placement passed. Saved notification handling
was corrected so it cannot block subsequent bot actions.

Management groups models, devices/clusters, network, health, credentials, SSH
and usage. All 36 Settings destinations are registered; ten main pages passed
native navigation checks. Credential controls and reviewed built-in/system
Terminal SSH handoff have contract coverage; live key rotation and SSH login
are not claimed. The native bundled Qwen reply correctly identified local
Qwen2.5 inference. Copy and Markdown export passed using a synthetic chat.

Final extension suite: **221 passed**. Android V1/V2 settings tests passed
(seven each), both debug variants assembled and both lint tasks passed. The final
APK rebuild passed with the synchronized offline manual verified in all six APKs. Samsung
UI acceptance remains blocked by its locked/off screen; no data was replaced.
This is a development candidate, not a production/notarized release. Native
voice playback, hosted media, persistent browser bots, training/RL, hardware-
bound full-app lock, iOS and HarmonyOS qualification remain open.

[Native evidence](docs/desktop/evidence/studio-chat-settings-native-20261106.json) ·
[Current feature matrix](docs/CHAT_STUDIO_REQUEST_RECONCILIATION_2026-11-06.md).

## Running-preview and feature discoverability gaps — 2026-11-06

- The owner again reproduced the incorrect Anthropic self-description in the
  old running preview. An isolated updated-host test passed, but source changes
  do not update a running process. Final package identity acceptance stays open.
- Messages lacked visible Copy/share actions. Copy, selectable text, local chat
  export and read-aloud integration are underway; public publishing is separate.
- Bots was buried in collapsed Studio tools. A visible main-navigation dashboard
  is being added; current reusable chat profiles are not always-on tool workers.
- Personality/voice modules do not establish full Android settings parity.
  Native inline live voice, media/sound generation, persistent bot workers and
  real training/RL have explicit open gates in the [request inventory](docs/CHAT_STUDIO_REQUEST_RECONCILIATION_2026-11-06.md).

## Local unlock, model identity, and permission scope — 2026-11-06

- **Repeated local unlock.** Current source defaults local chat to passwordless
  in-memory session accounting with no usage history written to disk. Requiring
  a passphrase is optional in LLM settings and applies next launch. Protected
  hosted/remote accounting, credentials, and existing encrypted history remain
  separate. The Mac's Keychain issue is not repaired by this change.
- **False model identity.** Local Qwen claimed Anthropic in the supplied reply,
  while the process used the pinned Qwen GGUF offline. Chat now receives the
  backend-reported local model label and runtime facts each turn. Remote routes
  disclose configured identity and upstream uncertainty. Fifteen host tests
  passed; the isolated live identity reply subsequently passed. Final packaged verification remains pending.
- **Permission granularity.** Source now separates project security and saved
  action presets. Autonomous workspace edits stay bounded and unsaved. Optional
  app-access task grants are limited to the exact app/model/human task/chat,
  25 operations, and 10 minutes, with Stop/completion revocation. OS approval is
  still required. Native app actions have not been requalified in this increment.

## Main-window frame and installation warning — 2026-11-06

- **Default window still looked like an IDE.** The earlier Chat-tab layout did
  not meet the owner's separate main-window requirement. Current source adds
  full-width application navigation and content in empty windows, preserving
  the complete IDE in separate project windows. Dedicated Models, Workspaces,
  and LLM settings pages reuse existing backend routes. Native visual and
  packaged-launch acceptance remains pending.
- **Installation appeared corrupt.** Startup patches changed two files while
  the product manifest retained upstream checksums. The packager now refreshes
  every existing integrity entry before signing. It keeps integrity validation
  active and rejects missing or escaping paths. Tests cover legitimate rebuilds
  and detection of later modification. Verify the next package on launch.
- The QA package's session-only unlock and protected-storage limitations remain
  unchanged. No OS Keychain reset or plaintext credential fallback was added.

## Studio reply shape, workspace windows, and secret storage — 2026-11-06

- **Chat reply shape**. The old SDK chat path attached a timestamp and coding
  instructions to a greeting. Direct chat now sends ordinary role messages
  and retains usage gating. A same-model Studio/Ollama run verified a normal
  greeting and the correct arithmetic answer.
- **Workspace navigation**. Workspaces opens a separate IDE window. The
  packaged app passed a cold launch into General Chat with an IDE in the prior
  session state. The Usage ledger allows one backend to own its unlock at a time.
  [Remaining acceptance](TODO.md#studio-source-selection-and-device-acceptance--2026-11-06).
- **Protected editor storage on the current Mac**. The OS reported
  `Keychain Not Found`. The QA build uses volatile editor secrets and session
  unlock. Remembered unlock and persistent encrypted workspace review require a
  protected-storage build. The encrypted Usage ledger retains its own persistence.
- **Physical compute**. Studio JVM generated through Mac and Samsung CPU
  workers over scoped USB. An earlier harness ran Mac/Samsung native layers
  over Tailscale/LTE. Managed enrollment, oversized model admission, and
  automatic WAN recovery remain open.

## Agent and provider continuation — 2026-10-31

Fixed source defects: Node count eight versus actual ten; planner missing model templates; strict wrapper incompatibility; tool replacement race; missing source/activity UI. On-device-only and English keyword planning remain limitations. [Evidence and next steps](docs/AGENT_CHAT_CHECKPOINT_2026-10-31.md).


## Resource and pairing review — 2026-10-31

Source fixes: cumulative storage/RAM admission and exact shard byte coverage;
unsupported SDK slice requests; native >99-layer offload cap; stale/shared launch
admission; first-peer bias and unbounded forwarding; signed-proof replay window
and sessions surviving identity replacement; MCP route aliases bypassing client
model/output limits. Checks and remaining gaps are recorded in
[the resource increment](docs/RESOURCE_NETWORK_PROGRESS_2026-10-31.md).

Open: no cross-owner resource reservations or qualified WAN layer scheduler,
no automatic NAT traversal for native RPC, no generic secure public exposure of
legacy loopback-trusted inference, no payment settlement, and no physical proof
for all requested pairing carriers. Existing voice/packaging/security gates remain.


## Bundled-model duplicate load and IDE host failure states — 2026-10-30

Fixed: Android model loading could wait for the startup load, then reject a
second load of the same model against the remaining RAM budget. ModelLibrary
now reuses a ready RunAnywhere model with the same library identity/path and
active runtime. Different models/backends retain memory admission. Samsung
SM-A207F physical generation and repeated-load identity checks passed.

Fixed: failed SDK-host startup/exit could leave stale readiness, and an unlock
failure could be absent from the visible chat. Host lifecycle now clears readiness,
reports failure and permits a fresh attempt. Broken backend writes clear pending
requests. Regression tests cover these paths; new native Mac interaction remains
unqualified because computer-use AX/screenshot requests time out. No app crash
was established from those timeouts. See the
[checkpoint evidence](docs/desktop/evidence/studio-afk-20261030.json).

## Native worker default-kernel equivalence — 2026-10-29

Open qualification issue: pinned CPU llama.cpp build on this Intel Mac produced
different continuation text between local and two-RPC-worker execution with
default repacking/flash-attention settings. Both executed real model layers.
Disabling repacking and flash attention for both paths restored identical output
for the fixed 32-token test. The individual source of the difference is not yet
isolated; do not generalize this to a corruption bug or universal determinism.
Retain both results in the [runtime evidence](https://github.com/sabbirimon/meshlit-inference/blob/feat/worker-admission/evidence/2026-10-29.json).
Default-profile numerical/performance qualification and broader model/device
coverage are pending. No app default was changed to hide this result.


## G1 contract hardening and math assessment — 2026-10-26

Durable ledger replay now rejects invalid domains, duplicate currency policies, inconsistent terminal costs and illegal event shapes. Any uncertain write disables the current authority until reopen. Android profile/key edits use one checked encrypted transaction; a failed commit blocks further credentials in that instance, and current profile/local-key bindings are rechecked before dispatch. Independent integer accounting and write-then-fail regressions pass; full regression, both debug assemblies and both lints passed. Samsung device follow-up is in progress. Studio durable credentials, all-adapter revocation, cross-device authority and live account/device qualification remain open. No automatic routing or provider billing-cap promise was added.

The [OpenAI math assessment](docs/MATH_RESEARCH_2026-10-26.md) reviews the pinned 719-manuscript catalog and selected compute/security candidates. Standalone HyperL gained bounded exact-arithmetic reduction tests; no theorem-derived speedup or cipher replacement is claimed.

## Optional Tailscale / VPN increment — 2026-10-26

The owner requested real VPN dashboards while keeping Basic settings minimal. Studio now reads optional installed Tailscale CLI status with bounded, cancellable local execution; Android reports its actual app-effective VPN transport separately from Tailscale package presence and hands management to the official client. Observations are opt-in, in-memory, timestamped and expiring. No unsupported connect toggle, paid API call, VPN reconfiguration or push is part of this increment. See [setup and evidence boundaries](docs/TAILSCALE_AND_VPN.md). Live tailnet and physical-device qualification remain open.


## Usage accounting boundaries — 2026-10-26

The new ledger covers Android online-provider text calls and Studio interactive chat, not all adapters or provider account activity. Device-local budgets guard estimates and cannot guarantee a provider invoice ceiling. Unknown/cancelled requests retain reservations; manual reconciliation requires owner-confirmed per-attempt charges. Opaque gateways are blocked by free-only/currency-budget policy. Studio's usage vault needs a passphrase each session, and journal capacity is bounded with no archival UI yet. See [details](docs/USAGE_AND_MODEL_BUDGETS.md). These limitations remain explicit rather than being counted as completed LTS gates.


## Confirmed baseline CI timeout — 2026-10-26

GitHub check `120763534145` explicitly reports that the V2 job exceeded its one-hour execution limit during the separate Core Candidate stage. Both new debug assemblies completed locally; final lint and the scoped restricted-channel test are tracked in [the baseline report](docs/desktop/STUDIO_BASELINE_2026-10-26.md). The full CI timeout remains open; no workflow timeout was increased or checks disabled.

## LTS planning blockers — 2026-10-26

[The LTS plan](docs/STUDIO_MOBILE_LTS_PLAN.md) records source gaps rather than treating them as completed integrations: Studio model routing is pending; the tunnel module is configuration-only; unified durable usage/spend admission is absent; current full CI was cancelled. G0 must reproduce the baseline and inspect the actual CI failure/cancellation cause. Existing device, voice, RAG and native qualification gates below stay open. No new runtime regression is asserted by this documentation audit.

## Current validation boundary — 2026-10-24

The paused build45 source fixes and unresolved cases are summarized in [the checkpoint](docs/PAUSED_CHECKPOINT_2026-10-24.md). Publication preserves this work; final voice regressions, both flavor build/lint and physical device checks remain open. Earlier successful builds below apply only to their named revisions.


## Active reliability findings — 2026-10-24

- **Source review fixes / device retest pending:** Live voice no longer treats
  ordinary Android silence as a fatal error, claims Listening during transcription,
  discards short qualified speech at timeout, truncates phrase captions to three
  lines, or releases AudioTrack before its meter child finishes. Regression tests
  and physical validation are recorded separately in PROGRESS.md.
- **Observed isolated probe:** Samsung native SmolVLM identified a square but
  omitted color and took 82.686 s. In-app offline photo Chat remains unwired.

- **Observed:** Qwen3 0.6B fast-mode Samsung reply gave 32 for 17+25. Explicit reasoning returned 42 after 38.4 s. Preserve both results; do not claim model-quality acceptance.
- **Source fix / device retest pending:** Provider setup Back loses origin; recommendation Load button does not indicate loaded state; native Qwen completion path omitted ChatML roles.
- **Source fix / final qualification pending:** RAG had no durable ingestion; remote RAG used a placeholder domain; HyperL displayed reference-only memory admission in native mode.
- **Source fix / retest pending:** Qwen3 now suppresses incomplete reasoning text and strips the template closing marker before saving/displaying its final answer.
- **Open:** SDK counters are not authoritative; physical live speech/image understanding and full device-layout sweep remain gates.

## Current photo lifecycle correction — 2026-10-24

The old photo path ran in a screen coroutine and then required a second text-model request. Source now uses the chat controller and displays the selected vision provider answer directly, without requiring a local text model. Stop, timeout, interrupted history and provider-change handling are explicit. Real provider and spoken offline voice acceptance remain open. The build43 V2 debug CI stage passed, but its separate Core Candidate stage hit the job timeout.

## Current build41 device observations — 2026-10-23

Samsung build42 showed an ordinary “hi” sent through the local tool planner whenever Web/Phone grants were enabled; the bundled small model returned an unrelated refusal. Source now requires an apparent tool request before using that planner, with a regression test for greetings and explicit commands. The voice mic opened a modal and auto-started; source now uses inline manual Start/Stop controls, a live input level/speech indicator, and a clear no-speech timeout. Photo selection opened a blocking dialog and could not show a usable model choice when no vision profile existed; source now uses an inline card with visible provider/model choices and a single Send photo action, or a setup link. Physical Samsung voice and provider vision response checks remain open; the bundled text model cannot process pixels.

Build43 Samsung still returned “I'm sorry, but I can't assist with that” to “sing a song” with Web and Phone tools off. This is a separate small-model response quality issue; the new tool-routing rule does not resolve it. A larger compatible local model or a configured provider requires its own device and account qualification.

Samsung DocumentsUI lists the copied Whisper/Piper ZIPs but did not return a selection during ADB taps. The import action now uses Android's one-time content picker; choosing Samsung My Files returned content URIs and both packs passed Meshlit's strict manifest/path/checksum validation on Samsung. A Piper sample returned to Stopped without a UI error; audible output and a complete recognition → chat → synthesis turn remain unverified. The ZIPs are in both phones' Downloads for manual Xiaomi import.

Samsung SM-A207F and Xiaomi M2101K7AI both accepted the V2 debug APK through ADB after the earlier Xiaomi USB policy block was cleared. Samsung dashboard showed discovery AUTO and scanning; Pause changed to OFF and cleared observed entries. The OFF state now labels its control “Resume automatic discovery.” Xiaomi chat displayed an invented human identity from the small bundled model, prompting an explicit Meshlit identity instruction; a new reply still needs to be checked. The SDK did not supply trustworthy token counts or speed, so Chat reports them unavailable. A pairing path that previously persisted trust after a failed handshake is changed to fail closed in source; physical adversarial pairing remains an open acceptance gate. No remote cluster takeover resistance is claimed solely from unit tests.

Updated 2026-10-10. Older bug IDs, hypotheses and screenshots are preserved in docs/history/BUGS-before-studio-2026-10-10.md. They are not automatically current open bugs. Feature backlog belongs in TODO.md and the desktop tracker.

| Item | State | Evidence / next verification |
| --- | --- | --- |
| Desktop system-role serialization rejected configured system prompt | Fixed | Serializer contracts and actual offline generation pass |
| HyperL dylib own install name pointed at development path | Fixed; packaging gate retained | Relocatable unchanged-source builder; all 12 CPU recipes/precise reduction and recovered payload hashes pass |
| Trimmed packaged Java omitted OSHI/JNA logging | Fixed | Full-classpath jdeps modules included; both recovered launchers generate and PKG monitor/render pass without external Java |
| DMG create/mount device unavailable in build session | Build path corrected; mount acceptance open | File-based HFS→UDZO plus hdiutil verification and exact extracted payload checks pass; no OS-mounted/clean-target proof |
| Benchmark warm-up violated shared minimum output budget | Fixed harness | 64-token warm-up; four real matched trials pass; aborted run contributes no timing data |
| q4_0 key cache gave poor arithmetic response | Held out of normal desktop settings | f16/q8_0 exposed; no general model-quality certification; Q4_K_M weights are a different setting |
| Combined Android lint/build stalled under concurrent load | Serial retry passes | One worker/in-process compiler; both flavors zero errors, 374 warnings each; core inference 7 warnings |
| Windows Ghostty argument test assumed POSIX absolute paths | Fixture corrected; 30 local desktop cases and Windows host CI pass at c1f2d60 | Native temp-directory absolute paths retain pin/literal/relative-path rejection assertions; no Ghostty process launched or Windows port claimed |
| Existing app warnings and historical cold-start/ANRs | Open acceptance/cleanup | Latest APK device tests required; prior build/start observations retain their dates |
| Build41 Xiaomi install blocked by device policy | Resolved for debug install | Earlier `INSTALL_FAILED_USER_RESTRICTED` on M2101K7AI was cleared; current V2 debug APK installs through ADB. MIUI still blocks ADB tap injection, limiting autonomous screen traversal. |
| “Use as primary” did not route requests | Honest UI label applied; routing open | The saved active endpoint ID had no reader in the model router. Devices now says “Mark as preferred” and explains this is a device-list preference until routing is implemented. |
| Radar sweep missing in visible device flow | Source fix; physical animation check pending | Device pairing now embeds radar and radar uses a bright 3-second rotating wedge only while discovery is active and animations are enabled. OFF disables the effect. |
| Build41 Android lint warnings | Open cleanup; no lint errors | V1 and V2 lint tasks pass with 378 warnings and 18 hints each, mostly unused resources and KTX suggestions; no claim of warning-free UI |
| Forced process loss/orphan recovery, memory/leak/long-run behavior | Unqualified | Shutdown hooks and explicit unload are implemented; stress/crash evidence still needed |
| Physical multi-device oversized model, native placement and failover | Open implementation/acceptance | Planner/loopback evidence is insufficient for physical fleet qualification |

No fabricated total bug count or project completion percentage. Report each reproducible issue with exact source/artifact, device/backend, expected/actual result, sanitized logs and its regression test or acceptance gate.

## Samsung permission-screen finding — 2026-10-26

The App permissions page has no storage/files row. Normal document-picker imports remain available; the existing all-files settings helper is not linked from that page. Add clear selected-file versus optional broad-access guidance and an Advanced-only settings handoff in a later UI increment; do not automatically grant broad file access.

## Prerelease continuation — 2026-10-26

Samsung Android 11 HyperL dataset import failed at Files.getFileStore despite an app-private path. Fixed the Android port to request owner-only POSIX attributes atomically rather than inspect the denied filesystem store. Device regression and permission assertions pending; native recipe comparisons completed before the original failure.

Samsung follow-up: direct POSIX creation passed import, verify, rotation and owner-only permission assertions; export then exposed an Android hard-link denial. Export now verifies all bytes first, exclusively creates an owner-only destination with CREATE_NEW, and copies with bounded buffers/cancellation, removing incomplete output on handled failure. The adapter exposes the private staging file only after success. The intermediate validation was stopped before source edits; final rebuild and device retest are pending.

## Final prerelease validation — 2026-10-26

Resolved and physically verified on Samsung: HyperL Android getFileStore denial and hard-link export denial. Authenticated export and owner-only permissions now pass alongside import/verify/rotation. The original non-void instrumentation method is also corrected.

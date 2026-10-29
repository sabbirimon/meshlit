# Meshlit current implementation and acceptance queue
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](PLAN.md) · [Progress](PROGRESS.md) · [Document status](docs/DOCUMENTATION_STATUS.md).
Scope: cross-platform tracking; feature and device gates remain separate. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

## DeepSeek Harness hosted workspace — 2026-10-30

Implemented an optional connection to the real upstream Harness workspace inside
Studio's integrated browser and both Android flavors. Android has Settings,
Tools and automation, and Code workspace entry points; Studio has a rail/command
entry. Connector settings persist clean host addresses, never launch tokens. Remote connections
require HTTPS; literal loopback HTTP is supported for local hosts/tunnels.
Android exposes no native JS/device bridge and refuses certificate bypass.

The pinned Node host uses a separate private home, read-only initial permissions,
no inherited provider keys, and disables official-model session-log contribution
and session/product telemetry. Real Intel Mac host startup, cookie login, 401
without authentication and foreign-origin API rejection passed with zero model
requests. Adapter/launcher tests and Android build results are recorded in
[the evidence](docs/desktop/evidence/deepseek-harness-20261030.json).

This is a hosted-workspace integration, not a native Android Harness runtime or
shared Meshlit agent engine. Harness owns its model bills, tools, approvals and
jobs; disconnect is not Stop or logout. Shared model/usage admission, paired
operations, native phone task controls and physical/TLS/provider qualification
remain open in the [H1–H5 plan](docs/architecture/deepseek-harness.md).
No paid API call, device enrollment, VPN change or release publication was made.

## Device runtime and HyperL first implementation — 2026-10-29

The private [runtime feature branch](https://github.com/sabbirimon/meshlit-inference/tree/feat/worker-admission)
now contains a bounded capability codec and local worker-admission library: 23 JVM
contract tests pass. It checks exact backend/build/model identity, atomic memory
credits, headroom, expiry, ownership, revocation and authority epochs. Cancellation
retains running-job credits until native cleanup is confirmed. This library is not
yet wired into authenticated workers or the Studio/Android UI.

Standalone [HyperL native liveness](https://github.com/sabbirimon/HyperL/commit/69d2ad8)
releases intermediates after last use while preserving ABI, numerical rules and
conservative admission. Four Release CTest cases, three sanitizer CPU cases and
500 before/after bitwise/status comparisons pass. A controlled chain used 32,768
versus 212,992 bytes of live native buffer payload; this is not overall RSS or a
speed claim. Newly covered changes retain HyperL's separate license and are not
automatically imported into Meshlit's Android port.

Real llama.cpp local/two-RPC-worker generation was rerun on this Mac. Default
kernel settings produced different text; matched reference settings (no repack,
flash attention off) produced identical 32-token output with two-worker placement.
Both results are retained. Existing HyperL two-worker/eight-shard TLS tests also
passed on this host. No phone connected; physical-device, oversized-model, sustained
RSS/latency and production worker integration gates remain open. No paid API calls
or model downloads. See [implementation and evidence](docs/architecture/meshlit-inference-clusters.md).


## Device clusters and HyperL runtime plan — 2026-10-29

Created the private [meshlit-inference](https://github.com/sabbirimon/meshlit-inference)
repository with reviewed architecture, staged acceptance gates and source pins.
Scope includes device-to-device jobs, compatible model layer execution, an optional
HyperL adapter and measured HyperL improvements. DeepInfer is a future supported-host
backend, not a working Mac/Android engine. This delivery is a plan only; no new
cluster/HyperL runtime or performance claim. First gate: reproduce existing native
baselines, then capability admission and one approved pair. See the
[integration record](docs/architecture/meshlit-inference-clusters.md).


## Separate OmniRoute and FreeLLMAPI dashboards — 2026-10-29

The new IDE source now has an explicit per-connection catalog health check:
reachable/authentication/access/rate-limit/service/timeout states, timestamp and
model presence, without generation requests. Three fixture test groups passed.
Gateway upstream-provider health, real account qualification and installer/mobile
delivery remain pending; a successful catalog is not proof that generation works.

Source review confirms distinct desktop apps and authentication flows. Dedicated
service dashboards, per-service free/paid/other filters, installation-aware ports,
login/session isolation and chat selection are planned independently. Current
Compose model search and Code OSS ID-only catalogs do not implement these filters.
FreeLLMAPI desktop defaults to 31415 (may change on conflict); 3001 is the existing
server preset. No matching standard-location app or usual-port listener was found.
No installation, login or paid call was attempted. See the [separate integration
plans and pinned source evidence](docs/desktop/ONLINE_MODELS_AND_CODE_WORKSPACE.md#separate-gateway-integrations-source-review-and-plan-2026-10-29).

## Code OSS development integration and mobile follow-up — 2026-10-27

The development workbench now contains a Meshlit extension with Home/navigation,
independent chat panels, model connections, a configured MCP directory, and
Chat/Plan/Edit/Autopilot sessions through the actual Antigravity native runtime.
Native workbench validation opened Home plus two chats, a real JavaScript editor,
and the built-in Git extension; the terminal command exists (shell execution was
not part of that check). One earlier real bundled-local inference completed with
165 reported input and 6 output tokens; external provider accounts remain untested.
SDK transport/Stop and MCP approval/denial tests use synthetic model fixtures.

The Kotlin stdio broker reuses the durable usage vault and budget admission.
Provider keys use editor SecretStorage; the macOS Keychain request is separate
from Meshlit's usage-vault passphrase. Helper executable/name mismatch caused an
initial launch crash and is corrected. Packaging now consistently sets Meshlit
helper names, identifiers, icon and URL handler. Development signing is ad-hoc:
Keychain access may still need approval after rebuilds. A stable Apple developer
signing identity and notarization remain open; no Keychain ACLs were weakened.

Requested next: Android with the same visual language and simpler navigation;
paired-phone Studio task control with desktop approval, scoped access, Stop and
revocation; optional full mobile IDE. These are pending, not working controls.
The pinned Antigravity packages have no Android runtime. Evaluate an authenticated
hosted Code OSS/code-server workbench first, with optional on-phone Linux hosting
qualified separately. MCP Git import/removal/tool discovery, full vendor-dashboard
migration, durable chat recovery, mobile pairing and full device execution remain
open. The development workbench still depends on local SDK/JDK/Compose jars;
previous Studio and published beta installers remain separate.

## Reuse-first Studio IDE and operator foundation — 2026-10-27

The owner selected Antigravity SDK and a genuine built-in IDE with independent
chats, plus paired-device operator workflows. Pinned VSCodium 1.143.17183 and
Antigravity SDK 0.1.22 official artifacts; Intel macOS checksum, SDK import,
explicit no-tool/no-subagent/no-retry configuration and workbench archive checks
passed without starting an agent or making model calls. This is upstream
preflight only: existing Studio UI is unchanged and full integration remains open.
[Integration sequence and gates](companions/studio-runtime/README.md);
[evidence](docs/desktop/evidence/studio-upstreams-validation.json).


## Studio identity and assistant modes — 2026-10-27

Studio now displays generated version, source revision, dirty-source marker and build time in its title/About. A per-profile OS file lock covers packaged and development GUIs; a second launch requests focus instead of opening another process. On macOS the development launcher builds/opens **Meshlit.app** with bundle identifier `com.meshlit.desktop`, serializes builds and checks the instance lock before replacing a bundle. Old Java/JCEF notification registrations are not modified; no JCEF runtime is used or notification permission enabled.

Chat and coding share **Chat / Plan / Edit / Autopilot**, a separate approval policy, chosen assistant workspace, action review and activity list. Plan blocks writes, Edit stops after one file mutation, and Autopilot has eight model steps with a ten-minute coroutine deadline. Each model step passes the existing credential and durable usage admission/settlement path. The default is Chat with approval required for every tool. Broader workspace edit permission requires a user confirmation and resets on restart or scope change.

Registered tools list/read bounded text, create a file without overwrite, replace one exact text match after a read receipt, show local system readings, and navigate Meshlit views. No unrestricted OS/shell, administrator, browser-clicking, provider billing or credential-mutation tool is registered. File writes reject Plan, unsaved editor state, traversal/symlinks, stale content and private paths. Unsupported/invalid model action JSON stops the run without retry. Human terminal/settings remain separate. Reviewed dashboard text can be sent through the same chat modes; it does not become trusted instructions.

Native Intel macOS bundle startup, ad-hoc integrity, duplicate executable rejection and repeat-launcher no-rebuild checks passed. The running bundle identifies clean source revision `468f369`. Desktop suite: 71 passed, one physical SSH fixture skipped. Shared controls and chat renders were inspected. Validation and remaining gates: [desktop guide](docs/desktop/ONLINE_MODELS_AND_CODE_WORKSPACE.md). Contract tests use scripted model decisions, not a claimed live-model qualification. Complete Code OSS embedding, general computer actions, richer patch review/undo, live provider/model tool reliability and simpler mobile controls remain open. Published beta.5 installers are unchanged.

## Embedded vendor dashboards and page reading — 2026-10-26

Studio's service dashboard, catalog and guide actions now render the actual vendor page inside a Meshlit window using OpenJFX WebView 21.0.13. **Read page → review → Add to chat draft** captures bounded visible text, excludes input/password/editable fields, redacts common key formats and preserves the existing draft. Sending remains a separate action using the selected model and existing credentials/budget contracts. Vendor page content is marked untrusted; no host command bridge or API-key injection is exposed.

Live Intel macOS qualification passed with WebView embedded in Compose: JavaScript, DOM extraction/redaction, cross-origin redirect review, unavailable-service error and clean shutdown. Desktop regression: 62 cases, 61 passed, one physical SSH fixture skipped, no errors/failures. The initial Chromium candidate failed native shutdown on this Mac and is not included. OmniRoute/FreeLLMAPI are not responding on their standard local ports; no authenticated gateway account, other desktop OS, installer or full Code OSS workbench qualification is claimed. Mobile remains deferred. [Setup and limitations](docs/desktop/ONLINE_MODELS_AND_CODE_WORKSPACE.md).

## Vendor dashboards and desktop menus — 2026-10-26

Model services now have distinct Overview, Models, Connection and Usage pages. The initial external-browser dashboard links are superseded by the embedded view above; gateway dashboard addresses persist separately from API endpoints and reject credential-bearing URLs. OmniRoute and FreeLLMAPI use documented loopback server defaults, not invented installation status. Connected model selection still uses the real endpoint response. Native desktop menus and VS Code folder handoff are implemented; the built-in editor remains lightweight and full workbench embedding remains open. See [desktop scope and validation](docs/desktop/ONLINE_MODELS_AND_CODE_WORKSPACE.md).


## Studio visual redesign — 2026-10-26

Implemented the owner's separate references: Gemini-style chat composition and an IDE-style coding window with explorer, editor, optional terminal and real assistant chat. Shared desktop typography, palette, settings selection and service cards are updated. See [progress](PROGRESS.md) and [desktop scope/validation](docs/desktop/ONLINE_MODELS_AND_CODE_WORKSPACE.md). Full VS Code engine/agent behavior, mobile visual port and installed package qualification remain open; this is a source/dev UI update.


## Desktop-first online models and coding workspace — 2026-10-26

The owner prioritized Studio before a simpler mobile model selector. Studio now exposes a combined Online models dashboard for OpenRouter, Hugging Face, OmniRoute, FreeLLMAPI and compatible services, a direct chat model menu, saved non-secret addresses and collapsed technical controls. Requests still require explicit session consent and real catalog selection; no automatic fallback, live-account proof or billing cap is added.

The initial embedded coding workspace provides bounded local UTF-8 editing, conflict-aware atomic saves, unsaved-change prompts, explicit code-to-chat drafts, saved Focus/Code/Review/Custom views and the human-only terminal panel. The initial VS Code extension opens trusted local workspaces in Studio; it is installed locally and its launcher configured. Full VS Code/Monaco embedding, language servers/debugger, autonomous edits and VS Code chat integration remain separate work. [Setup, validation and scope](docs/desktop/ONLINE_MODELS_AND_CODE_WORKSPACE.md).


## Android app identity — 2026-10-26

The owner requested the mobile app use the same identity as Studio and remove the visible “v2” suffix. Both UI flavor labels now resolve to **Meshlit**; Play-review and core-candidate overlays use that same resource. Application IDs, version codes and signing settings are unchanged, preserving update/data identity. Android now uses the existing black M mark, with safe-zone adaptive and monochrome layers plus lossless density-specific legacy/round icons. Removed two empty unqualified XML icon files so pre-26 launchers use the real bitmap fallbacks. Legacy icons can be regenerated with `scripts/generate-mobile-icons.cjs` (Sharp).

The existing pinned bundled model was hash-verified without download. No physical phone is attached; installed-launcher appearance is not yet qualified. Both debug flavor assemblies and lints passed (zero errors). All six ABI/universal APK outputs report the Meshlit label and contain the exact updated legacy icons. The first offline attempt lacked a cached pre-existing QR dependency; normal resolution succeeded. Published prerelease assets are unchanged.


## Studio icon and development runtime fix — 2026-10-26

Follow-up: macOS development launches explicitly set `-Xdock:name=Meshlit` and `apple.awt.application.name=Meshlit` before AWT starts. The Java launcher otherwise retains its Java label even after the Dock icon changes. This affects the development launcher; packaged installer identity remains separate.

The owner reported Java error icons and `DesktopToolsKt` failures while the dev app remained open during recompilation. The current jar contains that class; the old process reported `ClassNotFoundException`. Development launches now copy the runtime classpath into a unique per-launch snapshot, preventing later builds from replacing an open JVM's jars/classes. The crashed earlier process was stopped for a fresh launch.

Studio now reuses Meshlit's existing black M vector mark for the window and supported taskbar/Dock APIs, with PNG/ICO/ICNS distribution icons configured for Linux/Windows/macOS. Reproduce assets using `scripts/generate-desktop-icons.cjs` with Sharp available to Node. Black UI styling is unchanged. This source fix does not replace published beta.5 installers; installed-platform icon qualification remains separate. Validation: Studio suite passed (51 passed, one physical SSH fixture skipped); PNG/ICO/ICNS decode checks passed; the actual settings menu/dashboard rendered successfully through the isolated development launch. A first Gradle task-registration attempt failed before compilation and was corrected. Published installers were not rebuilt.


## Studio credential continuation — 2026-10-26

The owner resumed development and requested keeping the black Studio UI. Optional saved host credentials now use a separate passphrase-unlocked AES-GCM vault, atomic replacement and exclusive process ownership. Save/use/forget/lock are explicit; loading a key leaves network access off. Credentials bind to the normalized host, protocol and interactive-inference purpose. Replacement/removal/locking invalidates issued leases; UI credential actions stop the current client and clear session access. Model refresh and chat validate saved credential access before dispatch. A failed write locks the vault rather than exposing uncertain state.

This is Studio interactive-host coverage only: CLI/agent adapters, cross-device authority, OS keychain integration, Windows/Linux installed-app qualification and live provider testing remain open. Forgetting a local key does not revoke the provider-issued token or undo accepted charges. Black appearance is preserved; no automatic routing, paid request or release rebuild is introduced. Validation results are recorded in the usage guide.


## G1 hardening acceptance — 2026-10-26

- [x] Validate replayed accounting and reject ambiguous writes until reopen.
- [x] Commit Android provider/key changes together and recheck bindings before dispatch.
- [x] Add independent cost/replay/commit tests and review pinned math candidates.
- [x] Complete final regression/build/lint checks for this hardening increment.
- [ ] Qualify durable Studio credentials, all-adapter revocation, cross-device authority and live account/device boundaries before declaring G1 complete.

## Optional VPN dashboard acceptance — 2026-10-26

- [x] Implement bounded read-only Studio Tailscale status and Android app-network observations with official-client handoff.
- [x] Keep consent default-off, Basic navigation unchanged, technical fields collapsed, and provider identity unknown where Android cannot verify it.
- [x] Complete new contract tests, both debug builds/lints and native dashboard render inspection.
- [ ] Qualify a live tailnet and physical phones across VPN loss, sleep/wake, cellular changes and split tunneling.


## Usage and gateway acceptance — 2026-10-26

- [x] Implement encrypted append-only request metadata, reported input/output counts, price snapshots and manual bill corrections.
- [x] Reserve device-local currency allowances before text requests; preserve unresolved charges across failure/restart.
- [x] Wire Android online-provider chat/routes/agents and Studio chat to the authority; add dedicated history/budget controls.
- [x] Add explicitly optional OmniRoute and experimental FreeLLMAPI profiles without automatic fallback or assumed free entitlement.
- [x] Complete required final builds/lints and document exact results for this increment; one physical-phone SSH fixture remains skipped.
- [ ] Qualify live gateways and physical Android use; cover remaining adapters, archival/export, authenticated pricing and cross-device accounting.


## Active implementation increment — 2026-10-26

- [x] Capture the pre-change Studio destination audit and inspect the cancelled CI stage.
- [x] Run shared and Studio baseline tests before source edits.
- [x] Implement native dashboard entry points and six/four-entry Basic menus on Android/Studio; retain Advanced/search and current grants.
- [x] Expose only actual latest route/response observations; label missing billing and VPN backends.
- [x] Complete final changed-source tests, both Android debug builds/lints, scoped Core Candidate boundary test and offscreen Studio visual inspection; physical UI acceptance remains open. See [baseline report](docs/desktop/STUDIO_BASELINE_2026-10-26.md).
- [ ] Continue G1 durable usage, budgets and shared authority before adding automatic paid fallback or claiming account billing.

## Studio and mobile LTS queue — 2026-10-26

Plan-first scope: [full architecture, dashboard design and gates](docs/STUDIO_MOBILE_LTS_PLAN.md). Earlier pause/checkpoint entries below are historical; this pass does not resume application builds.

- [x] Inspect unified Git state, upstream repositories and existing Android/Studio integration boundaries.
- [x] Prepare consolidated LTS design including optional gateways, direct free/paid services, usage accounting, VPN, Studio parity and Basic/Advanced UX.
- [ ] G0: reproduce source/CI baseline and reconcile each Studio parity row.
- [ ] G1: implement shared contracts, migrations, credential authority and atomic usage/budget ledger.
- [ ] G2: qualify direct provider and optional gateway adapters with free/paid/mixed policies.
- [ ] G3: implement Models, Routing and Usage dashboards on Android and Studio.
- [ ] G4: implement verified VPN observations and official-client handoff/controls where supported.
- [ ] G5: scenario Auto settings, minimal Basic navigation and full visual/accessibility polish.
- [ ] G6: qualify saved reliability work, HyperL integration and remaining admitted Studio parity.
- [ ] G7: qualify packages, upgrades/rollback and maintenance readiness before an LTS designation.
- [ ] GX: independently qualify distributed/experimental capabilities and later operational backends.

[Current saved checkpoint](docs/PAUSED_CHECKPOINT_2026-10-24.md) contains the ranked next gates and [build/run handoff](docs/PAUSED_CHECKPOINT_2026-10-24.md#buildrun-and-agent-handoff). This source push does not complete the unchecked acceptance items.


## Current continuation — 2026-10-24

**Paused by owner: ship HyperL first.** The queue below is preserved for resumption.

- [x] Live / One turn voice, mute, tap interruption, audio focus, phrase captions and speech-memory admission source.
- [x] Correct review findings in silence handling, capture/transcription state, short-turn endpoint, caption truncation and AudioTrack cleanup.
- [x] Shared eight-reference defensive skill catalog; Android MCP/local chat integration and Android/Studio read-copy panels.
- [ ] Qualify updated physical microphone conversation, captions, interruption, mute, End/background and speech coexistence on Samsung.
- [ ] Integrate and qualify offline photo Chat; isolated SmolVLM probe took 82.686 s and omitted color. Online testing is deferred to the owner.
- [ ] Full-duplex acoustic interruption and online realtime speech adapters; no completion claim from current turn-taking voice.

- [x] Shared bounded persistent knowledge corpus and failure/grant tests.
- [x] Android encrypted import/search/remove and chat grounding source path.
- [x] Studio encrypted vault and opt-in chat grounding source path.
- [x] HyperL selected-backend estimate and repeat CPU measurement source path.
- [ ] Qualify latest Android/Studio UI, source attribution, native Qwen and actual offline spoken conversation.
- [ ] Re-run contract tests after catalog/template/remote changes; both flavor build/lint before APK delivery.
- [ ] Complete physical layer execution/recovery, multi-member encrypted delivery and reverse native relay; source additions above do not close them.
- [ ] Qualify real provider vision, training, operational runtimes/cloud billing and monetary rewards with the required hosts/accounts.

## Current priority — 2026-10-24

See the ordered [high-impact backlog](docs/HIGH_IMPACT_BACKLOG_2026-10-24.md). Photo-to-chat lifecycle and the Kandari-inspired compact Android UI are implemented in source; device and real-provider acceptance are tracked separately. The older build41/install and draft-release notes below are historical gates: build43 beta.4 was published, with the Core Candidate CI timeout still open.

## Current device and security gates — 2026-10-23

- [ ] Reconcile and qualify the older Android Studio requests using [the source audit](docs/OLD_ANDROID_STUDIO_REQUEST_AUDIT.md); prioritize voice input, model transfer, Auto Pilot repair, safe imports, persona/adapter selection and distributed LoRA by real backend evidence.

- [ ] Install and exercise the current build41 APK on Xiaomi; the verified ARM64 file is in its Downloads folder.
- [ ] Run the Samsung UI, model load/generation, discovery and approved-device revocation acceptance checks.
- [ ] Prove two-device direct chat and hostile pairing denial; prove layer sharding with coordinator plus two live workers.
- [ ] Build authenticated multi-member Mesh chat, desktop worker/agent/MCP backends and a real server/container runtime before claiming parity.
- [ ] Publish release assets only after on-device and signing/package gates; keep the existing prerelease draft until then.

## Paused Android/desktop peering continuation — 2026-10-12

[Future build plan](docs/PEERING_UI_BUILD_PLAN.md) · [Exact handoff and validation](docs/PEERING_UI_HANDOFF_2026-10-12.md). Implementation is partial and paused at the owner's request; resume from the saved working tree. Both platforms are in scope. Android Studio is the reference, not the target.

## Rewards and performance follow-up — 2026-10-23

- [x] Review the older Android reward plan and keep the implemented ledger tied to completed, measured hosted inference.
- [x] Remove redundant Android/Desktop receipt verification and reduce large Studio reply redraw frequency.
- [x] Improve Android and Studio rewards screens with clearer balance, history, backup and empty states.
- [ ] Benchmark Android hosted-job completion and Studio long-reply rendering on actual devices; profile further changes against those measurements.
- [ ] Connect Studio hosting to rewards only after its real hosted-job lifecycle supplies measured native output tokens and failure/forwarding evidence.

## Pairing, remote workers and custom compute — 2026-10-23

- [x] Verify both QR and manual worker approval against live pinned TLS identity, token and capability revision; add token rotation.
- [x] Select mixed LAN/VPN/direct Internet worker groups and expose live resource/admission risk assessment to users and delegated agents.
- [x] Add bounded custom HyperL graph editing to Studio's actual native CPU executor.
- [x] Build and package pinned ARM64 native layer executables in both debug APK flavors; verify both APK entries.
- [ ] Run genuine two-phone model-layer execution, worker-loss and oversized-model cases on independent devices.
- [ ] Implement an end-to-end authenticated reverse layer tunnel through the built-in relay for NAT traversal; measure relay behavior and worker loss.
- [ ] Audit each missing Studio setting against its Android backend and implement/qualify the corresponding desktop operation; do not count menu entries as parity.

## Reward metering and monetary design — 2026-10-23

- [x] Define service-specific usage units, reciprocal receipt evidence, offline reconciliation, fraud controls, settlement stages and regulatory gates in [the metering plan](docs/REWARD_METERING_AND_SETTLEMENT.md).
- [ ] Instrument authoritative native usage for each sellable service and prove cancellation/loss/forwarding cannot earn rewards.
- [ ] Build reciprocal signed receipts, anti-replay/idempotent reconciliation and independent fraud checks before any spendable balance.
- [ ] Complete jurisdiction-specific legal/payment review and processor integration before purchase, rental, payouts or exchange.


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

- [ ] Explain file-picker grants on App permissions and expose the existing optional all-files settings handoff under Advanced; test on supported Android versions.

## Prerelease continuation — 2026-10-26

Four-platform prerelease: complete Samsung storage regression, Intel Mac package/runtime checks, Windows/Linux CI installers, source checkpoint and checksummed GitHub prerelease. Clean-machine Windows/Linux installation and verified publisher signing remain separate gates.

Prerelease evidence update: Samsung storage/native/usage checks and Windows/Linux installer CI passed. Intel Mac payload inference/render/native checks passed. Finish final Android lint and publish checksummed prerelease; physical Windows/Linux installs, signing, live Tailscale and paid account tests remain open.

## Final prerelease validation — 2026-10-26

Completed final build/lint gate for Android and Intel Mac; Windows/Linux CI installers and Samsung regression checks passed. Remaining delivery gate: finish beta.5 asset uploads, verify remote digests and publish. Platform signing and physical Windows/Linux acceptance remain open.

Delivery recovery: finish verified temporary-part transfer and GitHub reconstruction workflow; verify all 13 final assets and remove temporary parts before publication. Preserve the exact locally tested Android APK signature. Do not rebuild or publish a differently signed replacement as the tested binary.

## Beta.5 delivery completed — 2026-10-26

- [x] Publish [beta.5](https://github.com/sabbirimon/meshlit/releases/tag/v2.0.0-beta.5) for Android, Intel macOS, Windows x64 and Linux amd64 after verifying all thirteen remote asset names, uploaded states, sizes and SHA-256 digests.
- [x] Complete the [relay](https://github.com/sabbirimon/meshlit/actions/runs/40273491384) and remove temporary parts; keep the equivalent Mac PKG local-only.
- [ ] Complete physical Windows/Linux installation, production signing/notarization, broader device acceptance and live provider/VPN qualification. These remain separate from successful prerelease delivery.

This supersedes the pending upload/publication delivery gate above.

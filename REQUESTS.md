# Current user requests and implementation tracking
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](PLAN.md) · [Progress](PROGRESS.md) · [Document status](docs/DOCUMENTATION_STATUS.md).
Scope: cross-platform tracking; feature and device gates remain separate. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

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


## G1 contract hardening and math assessment — 2026-10-26

Durable ledger replay now rejects invalid domains, duplicate currency policies, inconsistent terminal costs and illegal event shapes. Any uncertain write disables the current authority until reopen. Android profile/key edits use one checked encrypted transaction; a failed commit blocks further credentials in that instance, and current profile/local-key bindings are rechecked before dispatch. Independent integer accounting and write-then-fail regressions pass; full regression, both debug assemblies and both lints passed. Samsung device follow-up is in progress. Studio durable credentials, all-adapter revocation, cross-device authority and live account/device qualification remain open. No automatic routing or provider billing-cap promise was added.

The [OpenAI math assessment](docs/MATH_RESEARCH_2026-10-26.md) reviews the pinned 719-manuscript catalog and selected compute/security candidates. Standalone HyperL gained bounded exact-arithmetic reduction tests; no theorem-derived speedup or cipher replacement is claimed.

## Optional Tailscale / VPN increment — 2026-10-26

The owner requested real VPN dashboards while keeping Basic settings minimal. Studio now reads optional installed Tailscale CLI status with bounded, cancellable local execution; Android reports its actual app-effective VPN transport separately from Tailscale package presence and hands management to the official client. Observations are opt-in, in-memory, timestamped and expiring. No unsupported connect toggle, paid API call, VPN reconfiguration or push is part of this increment. See [setup and evidence boundaries](docs/TAILSCALE_AND_VPN.md). Live tailnet and physical-device qualification remain open.


## Active request: usage history, budgets and optional gateways — 2026-10-26

The owner asked to continue with real free/paid model usage history on Studio/mobile, then optional OmniRoute and FreeLLMAPI, while conserving coding credits. This increment implements a shared ledger, currency budgets and dedicated controls for Android online-provider text calls and Studio interactive chat. Existing dependencies/caches are reused; no live paid API call, server installation, publication or background automation is performed. Remaining coverage and qualification are explicit in [the usage guide](docs/USAGE_AND_MODEL_BUDGETS.md).


## Begin staged implementation — 2026-10-26

The owner now requests implementation, starting with baseline checks and missing Studio capabilities, then dashboards and Basic-settings cleanup. Reuse research, assets and caches; avoid wasted credits and parallel agents. This supersedes the plan-first hold below. Current evidence and the first increment boundary are in [the baseline report](docs/desktop/STUDIO_BASELINE_2026-10-26.md).

## Studio/mobile LTS and dashboard requests — 2026-10-26

Current scope is **planning before implementation**. Record the full design in [the LTS plan](docs/STUDIO_MOBILE_LTS_PLAN.md):

- Optional OmniRoute and FreeLLMAPI gateways; direct free and paid providers remain independently usable.
- Dedicated Models, Routing, Usage & Costs, and VPN & Network dashboards with detailed management and explicit spending policies.
- Finish missing Studio capabilities by backend/evidence, with a refined font, palette, texture, icon and motion system on both platforms.
- Minimal Basic settings; scenario-based Auto defaults; searchable Advanced tuning and preserved user overrides.
- Preserve unfinished voice, RAG, model, HyperL integration and physical-device acceptance in the staged queue.
- Check Git and avoid wasting ChatGPT/Codex allowance. The owner clarified that “credits” meant account usage, not creator attribution or a standalone HyperL continuation.

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

## Prerelease continuation — 2026-10-26

Owner selected Android + macOS + Windows + Linux for the experimental prerelease and authorized saving progress to GitHub. Publisher enrollment/signing is a separate owner identity step; do not label debug/ad-hoc/unsigned packages as verified publisher releases.

## Final prerelease validation — 2026-10-26

Android + Intel macOS + Windows x64 + Linux amd64 prerelease packages are built and scoped. The owner disconnected Samsung after passing tests and authorized continuing while away. GitHub checkpoints are pushed; draft release asset transfer is in progress.

## Four-platform prerelease delivered — 2026-10-26

The authorized [beta.5 prerelease](https://github.com/sabbirimon/meshlit/releases/tag/v2.0.0-beta.5) is public with thirteen verified final assets. [Relay reconstruction](https://github.com/sabbirimon/meshlit/actions/runs/40273491384) preserved the exact tested APK/DMG bytes and removed its temporary parts. Android and Intel Mac targeted evidence is recorded; Windows/Linux installers have CI evidence, not physical installation qualification. Publisher signing and broader platform parity remain open. No paid API calls, main merge or store submission were performed. This supersedes the draft-transfer status above.

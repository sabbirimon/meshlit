# Android and Studio settings parity audit — 2026-11-06
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-06: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

This is a source and navigation audit of the working checkout. It is not a fresh Android build, device test, provider login test or proof of complete feature parity. The current Code OSS Studio shell and the older Compose desktop are separate UI implementations. Older desktop functionality is not counted as available in the current shell unless a current command exposes it.

## Management and platform scope update

Android now has a Management category and a grouped dashboard backed by the
shared DashboardDirectory. Both Android variants compiled and their navigation
tests passed; packaged/physical acceptance is separate. Desktop now has
Management, Credentials and SSH dashboards in source. The SSH counterpart opens
an explicitly reviewed built-in OpenSSH terminal on macOS/Linux; it is not the
Android inbound server or a qualified external-terminal integration.

The initial six-group audit below is retained as a feature-by-feature comparison.
The current seventh category is Management. Four old declarations (gibberlink,
packages, securitylab and rewards) are intentionally hidden by BuildProfile; they
are not visible broken menu routes. Cross-platform security and hosting plans
include future iOS and HarmonyOS without claiming their hardware qualification.

## Inventory and evidence

Android's catalog groups are defined in [SettingsDestinations.kt](../app/src/main/kotlin/com/meshlit/ui/modern/SettingsDestinations.kt). [ModernSettingsScreen.kt](../app/src/main/kotlin/com/meshlit/ui/modern/ModernSettingsScreen.kt) dispatches screens, enforces BuildProfile routes, offers General/Advanced navigation, and opens Android-specific controls. [ModernMeshlitApp.kt](../app/src/main/kotlin/com/meshlit/ui/modern/ModernMeshlitApp.kt) and [WorkspaceNavigation.kt](../app/src/main/kotlin/com/meshlit/ui/modern/WorkspaceNavigation.kt) route the main drawer and direct model/media/chat destinations. Build flavor and Play-review restrictions can remove entries.

Studio inventory is [settings.cjs](../companions/studio-runtime/extension/settings.cjs), [navigation.cjs](../companions/studio-runtime/extension/navigation.cjs), [main-pages.cjs](../companions/studio-runtime/extension/main-pages.cjs), [shell.cjs](../companions/studio-runtime/extension/shell.cjs) and registrations in [extension.cjs](../companions/studio-runtime/extension/extension.cjs). A registered command is a wired UI route, not proof an optional host or external device is running. In this QA package, project memory/action persistence requires protected-storage support and can report unavailable.

**Status vocabulary:** Available means a current Studio UI route exists. Different means a narrower or platform-specific counterpart. Not ported means no matching current Studio settings destination was found. These statuses do not certify Android runtime behavior.

## Latest requested additions and provisional status

The owner additionally requested personality/custom instructions, local candid
style, chat voice selection, live conversation/transcript/translation tuning, and
image/video/sound/music generation. The personality and local read-aloud modules
now exist with focused tests, but their packaged Chat integration and live output
acceptance are separate gates. OpenLive live-preference wiring is under active
integration. These additions narrow specific gaps; they do not establish complete
Android/Studio parity or close the older microphone-to-reply report.

| Capability | Android / existing implementation | Current Studio boundary and next gate |
| --- | --- | --- |
| Personality and custom instructions | Per-conversation style/warmth and instructions, plus personalization settings | New [personality module](../companions/studio-runtime/extension/chat-personality.cjs) offers eight presets and bounded app-wide instructions. Verify saved selection reaches the next local/hosted/bot request and survives relaunch. Per-conversation Android parity and memory-personality coupling remain incomplete. |
| Local candid style | Existing local behavior and policy controls have their own scope | New local style preference is prompt wording only. Verify external routes omit it and no tool, device, vault or project grant changes. It is not a model-weight edit or a bypass switch. |
| Message read aloud | Native phone speech and headset lifecycle | New [macOS read-aloud module](../companions/studio-runtime/extension/chat-voice.cjs) has installed-voice/rate settings and per-owner Stop. Eight process/validation tests passed; packaged message-button wiring and audible output still need acceptance. No microphone is opened by read aloud. |
| Live captions and transcript | Android microphone/STT/live voice paths, with unresolved real-device hearing/reply qualification | OpenLive's separate call UI owns live capture, captions, transcript and playback. Studio links to that interface; it does not currently embed the transcript or duplicate capture. Test actual end-to-end speech, permissions, interruption and release. |
| Translation and speech tuning | Android language/voice settings depend on available engines | New [live text preferences](../companions/studio-runtime/extension/live-voice-settings.cjs) select conversation/translation and text languages/style in OpenLive instructions. Engine, voice, speed, sensitivity and turn timing stay in OpenLive. Its one-session speech language does not establish independent source/target speech engines. Bengali recognition/output and live translated audio remain unqualified. |
| Image, video, vision and generated speech | Explicit online adapters and saved remote video jobs exist in [Android media generation](../app/src/main/kotlin/com/meshlit/media/MediaGeneration.kt) | A current Studio creation/output manager is planned. Require actual model capability, provider auth, bounded request/output handling, cancellation, preview/export/delete and recovery. Android source or a submitted video job is not Studio media-output proof. |
| Sound effects and music | No general qualified adapter follows from Android speech synthesis | Requested and planned for Studio. Select an actual runtime/provider and license, implement the artifact lifecycle, and inspect real generated audio before exposing ready controls. |

## Models & voice

| Android destination | Current Studio status | Wiring and limit |
| --- | --- | --- |
| `models` · Models and downloads | Different | Models dashboard and source picker show bundled runtime and saved APIs. Full Android GGUF import/download/load manager is not ported to this dashboard. [Model sources](../companions/studio-runtime/extension/model-sources.cjs). |
| `voice-settings` · Voice and speech | Different / additions under integration | Voice and vision launches owner-installed OpenLive, discovers installed engines and previews speech. Local read-aloud and live text-preference modules are added with the acceptance limits above. Android native speech settings and credentials/billing remain separate. [Voice](../companions/studio-runtime/extension/voice.cjs). |
| `media` · Camera, vision and audio | Different | OpenLive camera/screen and voice are companion workflows. Android photo/live vision and media-generation screens are not equivalent Studio chat controls. |
| `providers` · Online providers | Available, different | Saved compatible API profiles, source categories and catalogs through Models. Android provider adapter/settings parity is not established. [Connections](../companions/studio-runtime/extension/connections.cjs). |
| `huggingface` · Hugging Face dashboard | Available, different | Hugging Face provider dashboard handoff exists. Android-specific downloads/account implementation is independent. |
| `router` · Model router | Not ported | OmniRoute gateway dashboard is a separate host product; it is not the Android scenario/chain/comparison router. |
| `behavior` · Custom local model behavior | Different / addition under integration | Custom bot instructions and backend system behavior remain. The new personality/candid-style module adds prompt preferences; it does not port Android local policy rules, training or guardrail editing. |
| `training` · Fine-tuning | Not ported | No current Studio fine-tuning destination. Existing training companion does not constitute a wired Studio page. |
| `acceleration` · Acceleration | Different | System resources/CPU policy is wired. This is not Android backend capability selection or a guarantee of GPU execution. |
| `hyperl` · HyperL libraries | Not ported to current shell | Older Compose desktop has native HyperL controls; current Code OSS Settings exposes no HyperL page. |
| `colibri` · Colibri host | Different | Compatible remote model endpoints are possible; Android Colibri Off/On/Auto policy is not a Studio control. |
| `rag` · Knowledge library | Different | Project memory/OpenViking provide scoped context workflows. Android document-library import/index and retrieval controls are not a complete port. |
| `usage` · Usage & costs | Available, different | Usage and budgets page, optional protected ledger, and session-only local usage. Android persistence and approval lifecycle differ. [Usage](../companions/studio-runtime/extension/usage.cjs). |

## Agents & memory

| Android destination | Current Studio status | Wiring and limit |
| --- | --- | --- |
| `personalization` · Memory and personality | Different / addition under integration | Saved bot instructions and workspace memory remain. New app-wide personality/custom instructions and local candid style have module tests; Android per-conversation warmth and memory-personality controls are not fully ported. |
| `tasks` · Task manager | Different | Agent teams expose their own jobs/status/control. No unified Android task planner/task manager page. |
| `connected-capabilities` · Connected capabilities | Available, project required | OpenViking/Artemis adapters and scopes. Current Studio command requires an open trusted folder, so General-window invocation does not itself create a usable project page. [Connected services](../companions/studio-runtime/extension/connected-services.cjs). |
| `agent-catalog` · Agent role library | Available | Agent teams include role library and adapted profiles. Role templates do not grant tools. |
| `agent-teams` · Agent teams | Available | Team catalog/run/status/control goes through backend. Independent host/capability approvals still apply. [Teams](../companions/studio-runtime/extension/agent-teams.cjs). |
| `agents` · Agent management | Different | Workspace Plan/Edit/Autopilot with project action presets. Android durable agent-management inventory and all device grants are not identical. |
| `bot-connections` · Bots and connections | Different | Reusable local/API-backed bot profiles exist. Android AstrBot/Grok/OpenClaw adapters and signed Bot Inbox are not all present in Studio. [Bots](../companions/studio-runtime/extension/bots.cjs). |
| `agent-registry` · Agent registry | Different | Bots, team profiles and MCP connections are separate lists; no unified Android registry page. |
| `agent-logs` · Agent logs | Different | Opt-in project action history and team logs are narrower than Android agent/MCP diagnostics export. |
| `tools-automation` · Tools and automation | Different | Tools & permissions, MCP connectors, app access and connected services. No single Android automation dashboard port. |
| `mcp-catalog` · MCP connections | Available, different | Configured stdio/HTTP MCP sources and per-agent selection. Catalog/setup and grants differ from Android HTTPS enrollment. |
| `gateway` · Agent Gateway | Different | OmniRoute/FreeLLMAPI host dashboards and protocol guidance. Android built-in MCP/A2A/model listener is not hosted by this Studio page. [Protocols](../companions/studio-runtime/extension/protocols.cjs). |
| `openclaw` · Phone agent and autonomy | Different | Mac app access and optional computer companions are separate implementations. Android on-device autonomy/App Accessibility control remains phone-side. |
| `automation` · Android automation | Android-specific / counterpart | Android Accessibility settings are not copied to Mac. Studio has separately approved macOS app access and optional Artemis host tasks. |
| `hooks` · Agent hooks | Not ported | No arbitrary user lifecycle-script editor in current Studio. Reviewed MCP connectors are not script-hook parity. |
| `deepseek-harness` · DeepSeek Harness | Available | Optional installed Harness configuration/dashboard handoff. Separate runtime and account permissions remain explicit. |

## Devices and connections

| Android destination | Current Studio status | Wiring and limit |
| --- | --- | --- |
| `devices` · Devices | Different | Device connection assistance checks configured model sources and setup-only endpoints. This is not full Android discovery/enrollment UI. ID is a direct app route and group member, absent from SettingsDestination entries. |
| `network` · Network and pairing | Different | Source setup, Device connection assistance and protocol instructions exist. Full listener/transport/pairing dashboard not ported. |
| `vpn` · VPN & network | Not ported to current shell | Android app-network/Tailscale view and older Compose VPN dashboard are independent of current Studio shell. |
| `sharding` · Sharding and workers | Different / incomplete | Can select already-running local/remote coordinator endpoint. Managed desktop worker enrollment, layer placement UI and oversized-model qualification remain separate work. |
| `radar` · Device radar | Not ported | No Studio nearby-device/topology radar page. Do not synthesize devices from model profiles. |
| `host-map` · Global host map | Not ported | No Studio map of consenting relay members. |
| `external-services` · LAN and VPN services | Different | Remote model endpoint catalog/contact checks cover a subset, not arbitrary printer/NAS/service discovery. |
| `web-bridge` · Meshlit Web/API bridge | Not ported | Phone invitation/enrollment is documented through Protocols. No Studio-hosted equivalent enrollment dashboard. |
| `device-dashboard` · Device management | Different | Model-source revocation and device contact grants exist; no unified groups/workers/relay/peer manager. |
| `meshchat` · Mesh chat | Not ported | General LLM chat is not peer-to-peer messaging. |
| `remotecommands` · Device and cluster commands | Not ported | Scoped phone/SSH multi-node command UI is not equivalent to model API checks or agent teams. |
| `p2p` · P2P chat and commands | Not ported | No native Studio WebRTC signed peer-chat/command configuration page. |
| `gibberlink` · GibberLink and English transcript | Retired Android surface; not ported | The declaration is filtered out by BuildProfile.retiredRoutes before navigation. This is an intentional retired surface, not a visible broken setting. |
| `external` · External devices and OTG | Android-specific / not ported | Android USB/OTG grants and removable-storage controls do not map to a current Studio page. |
| `ssh` · SSH nodes and connections | Different / source implemented | New SSH dashboard saves non-secret profiles, checks local configuration, and explicitly launches built-in OpenSSH with host-key checking. Android inbound enrollment and other terminal adapters remain separate. |
| `peers` · Forwarding peers | Not ported | No current Studio inference-forwarding-peer manager. |

## Privacy & permissions

| Android destination | Current Studio status | Wiring and limit |
| --- | --- | --- |
| `securityprivacy` · Security and privacy | Different | Tools & permissions, project security, explicit app/device/MCP grants and protected Usage cover separate scopes; no unified Android privacy/identity panel. |
| `guardrails` · Local behavior and guardrails | Different | Bot instructions and external gateway controls do not equal Android local policy and term-rule editor. |
| `permissions` · App permissions | Available counterpart | Tools & permissions explains scopes and links project security, macOS app access, MCP and devices. Android OS runtime grants remain platform-specific. |
| `firewall` · Network rules | Not ported | No Studio Meshlit-listener allow/deny rules page. Endpoint validation is not a firewall UI. |
| `identity-recovery` · Device identity and recovery | Not ported | Protected Usage unlock is not device signing-identity backup/restore. |
| `crypto` · Local cryptography | Not ported to current shell | No current Studio local cryptography dashboard. Older Compose implementation is not exposed here. |
| `legal` · Terms and privacy | Different | Bundled notices/manual and upstream attribution remain present. No matching Android acceptance/data-controls page. |
| `search` · Search access and articles | Different | Settings search and editor file search exist. Android Internet/article-search grants and local app-content search do not map to those. |

## System & developer tools

| Android destination | Current Studio status | Wiring and limit |
| --- | --- | --- |
| `files` · Files and storage | Available counterpart | Separate IDE Explorer, file dialogs, file references and diffs. Android SAF/ZIP/AI-asset file-manager parity is not claimed. |
| `ide` · Code workspace | Available counterpart | Full Code OSS project window with separate project chat; General Chat remains its own window. |
| `device` · Device | Different | System resource observations cover host/process metrics. Device naming, roles and Android hardware profile are separate. |
| `resources` · Resources and sensors | Different | System resources plus authorized remote resource advertisements. Remote capacity, prices and execution remain unverified until measured. |
| `power` · Power and costs | Different | CPU policy observes some power/memory conditions. Android energy estimates and download policies are not a Studio settings page. |
| `monitor` · Monitoring and layer pipeline | Different | Live local CPU/RAM/process/model charts. No full Android worker pipeline/thermal monitor port. [System monitor](../companions/studio-runtime/extension/system-monitor.cjs). |
| `operations` · Operations dashboard | Different | Stop conversation, app access and local model controls exist separately. No unified Android per-function emergency-stop dashboard. |
| `recovery` · Checkpoints and recovery | Not ported | Model checkpoint and replicated recovery dashboards are not present in current Studio settings. |
| `runtime` · Linux runtime and sandbox | Not ported | No desktop VM/QEMU activation dashboard in current shell. |
| `commands` · Commands and instructions | Available counterpart, narrower grants | IDE terminal is human-facing. Workspace autonomy does not grant arbitrary shell execution or Android manual-runtime command access. |
| `packages` · Lab packages | Retired Android surface; not ported | BuildProfile.retiredRoutes hides this old destination. Existing package backends are not a promise to restore the retired UI. |
| `securitylab` · Security Lab | Retired Android surface; not ported | BuildProfile.retiredRoutes hides this destination. Keep the retirement distinct from a missing handler in a visible menu. |
| `termux` · Termux integration | Android-specific | ExistingTermuxSettings probes an installed Android shell and delegation. No equivalent Mac Termux setting is appropriate. |
| `crawler` · Web crawler | Not ported as dedicated settings page | Optional Crawl4AI source companion exists; generic MCP/host setup does not equal Android crawler settings. |
| `cloud` · Cloud management dashboard | Different | Provider gateways and credential-bound model profiles are narrower than Android vendor infrastructure inventories/actions. |
| `audit` · Audit and telemetry | Different | Opt-in project action history is not the full Android encrypted audit/OTLP exporter. |
| `logs` · Logs and export | Different | Editor/host diagnostics and team outcomes exist; no matching unified Android log filtering/export screen. |
| `rewards` · Mesh rewards wallet | Retired Android surface; not ported | BuildProfile.retiredRoutes hides this destination. Resource-sharing model advertisements are not a wallet or settlement service. |

## App & help

| Android destination | Current Studio status | Wiring and limit |
| --- | --- | --- |
| `dashboards` · Dashboards | Different | Models/modes dashboard, Settings and resource monitor exist; old Compose overview is not the current shell. |
| `appearance` · Appearance | Available counterpart | Theme tokens match the IDE, with editor theme/font settings. Android wallpaper colors, scheduled palettes and glass options are not a port. |
| `notifications` · Notifications | Platform-specific | Android opens OS notification channels. Desktop native notifications belong to app/OS settings; no copied Android channel controls. |
| `menu-layout` · Customize menu | Not ported | Studio sidebar hierarchy/collapse exists, but Android saved shortcut add/remove/reorder is not implemented. |
| `configuration` · Configuration profiles | Not ported | No Android portable settings export/review/import UI in current Studio. |
| `help` · Guide and tutorial | Available counterpart | User manual is bundled and opens through its registered command. Android tutorial progress and all screenshots are separate. |
| `about` · About and availability | Different | App/upstream version, notices and manual exist; dedicated Meshlit build/availability page is a navigation improvement opportunity. |

## Conversation settings compared

The Android [ChatOptionsDialog.kt](../app/src/main/kotlin/com/meshlit/ui/modern/ChatOptionsDialog.kt) is per conversation and commits only on Save. It includes task presets, saved bots, style/warmth, custom instructions, local/provider/router/Colibri selection, cluster group choice, reasoning effort, Chat/Agent mode, approval policy, individual tool groups, output budget and history controls. [ChatToolSelection.kt](../app/src/main/kotlin/com/meshlit/chat/ChatToolSelection.kt) restricts route eligibility and ten-tool capacity independently of app/device grants.

| Concern | Android | Current Studio |
| --- | --- | --- |
| Model route | Loaded phone/cluster, online profile, scenario router, Colibri | Bundled runtime, approved local/remote compatible server, already-running coordinator, provider gateway |
| Mode | Chat / Agent with selected permitted groups | Chat / Plan / Edit / Autopilot; folder tools require a separate project window |
| Reasoning | Exact-model declared effort support; local/mixed routes Auto | Composer Effort uses exact-profile capabilities; local Auto. Dashboard explains the control |
| Approval | Ask / Auto / selected tools; restart returns to Ask | Project setup saves Ask / auto reads / bounded workspace edits. Device/MCP/app/provider approvals remain independent |
| Sampling and history | Per-conversation settings, 0–20 history messages, manual or measured cluster output budget | LLM settings currently app-wide, 0–10 completed turns and 1–1024 token output ceiling. No automatic cluster output-budget UI |
| Instructions | Per-chat instructions plus companion style and presets | Reusable bot instructions and mode-specific backend prompts; new app-wide personality/custom-instruction module under integration. Full per-conversation Android controls remain incomplete |
| Voice and vision | Native phone microphone/photo/voice/live-vision flows | Optional OpenLive live call/transcript plus text translation tuning under integration; separate macOS read-aloud module. Independent permissions, credentials and live-output gates |
| Conversation persistence | Android recent chats persisted in its controller/store | Shell lists actual currently open conversations/pages. This is not a restored cross-session conversation library |
| Local access | Android's own model/settings security | General local chat can use session-only usage without a password; optional chat lock and protected ledger remain separate |

## Navigation fixes supported by existing Studio commands

1. Keep **Workspace agents** directly reachable from Settings through `meshlit.workspaceAgents`, with a clear project-first description. The model dashboard already exposes the chooser, but the Settings agent section currently emphasizes teams rather than project mode selection.
2. Keep **MCP connectors** distinct from **Connected capabilities**. `meshlit.connectors` works as configuration navigation; `meshlit.connectedCapabilities` requires a trusted folder and otherwise reports “Open a project folder first.” Its General-window button should offer a project handoff rather than appear to be a global settings page.
3. Expose **Forget remembered Usage unlock** through existing `meshlit.forgetUsageUnlock`, alongside Usage and the chat-lock option. Label it separately from clearing encrypted history and do not claim remembered secrets in session-storage builds.
4. Add discoverable appearance shortcuts using the existing editor theme/font commands in the separate coding window. Do not route an everyday General Chat “Settings” click into the editor preferences by default.
5. Make **Stop local model / resource monitor** and **Stop app access** easy to distinguish. System monitor owns local model stopping, while `meshlit.stopAppAccess` revokes app-control work. Neither claims to stop every remote task or undo completed edits.

These are navigation improvements over existing code, not requests to grant any capability automatically. New VM, training, peer transport, identity recovery, arbitrary hooks, provider billing and managed sharding dashboards require implementation and separate validation before exposure.

## Android route findings requiring follow-up

Four catalog entries — `gibberlink`, `packages`, `securitylab`, `rewards` — have no dedicated dispatch branch in the inspected ModernSettingsScreen. ModernMeshlitApp routes catalog settings IDs there; its fallback renders the settings list. Their catalog text therefore overstates navigation coverage. This audit does not remove them or modify Android. A follow-up should either wire the intended existing screen or hide/mark the entry unavailable, with route tests. `devices` is the converse: a direct main app route and group member, but not a SettingsDestination record.

## Acceptance queue for the latest requests

1. Qualify personality and custom instructions through the actual packaged Chat
   request, including hosted omission of local-only style and unchanged tool grants.
2. Verify the per-message speaker action, persisted voice/rate and Stop against
   actual macOS playback. Check new conversation and window close cancel only
   Meshlit-owned playback; do not stop another app's audio.
3. Run OpenLive microphone, transcript/captions, model reply and voice output in
   the live-call UI. Then test translation with measured source transcription and
   target output. Treat language preferences, engine support and translated audio
   as separate outcomes, including Bengali's unqualified speech path.
4. Specify and implement Studio media creation with truthful model capabilities,
   remote/local data destinations, artifact progress, cancellation, bounded files,
   export/delete and credentials/usage. Qualify each modality independently.
5. Continue the earlier gaps in model import/download, scenario routing, training,
   memory, signed bot inbox, arbitrary hooks, managed sharding, SSH/peer transport,
   identity recovery and Android route wiring. These remain open even if the new
   personality and voice controls pass their own checks.

## Verification boundary

The audit enumerates every declared Android SettingsGroup ID and checks all actual SettingsDestination records are covered. Main-page unit tests cover truthful runtime status, fixed command mappings, unsupported-operation rejection, access-setting persistence, and cancellation returning the workspace page to idle. No phone connection, Android install, new remote request, or live external service validation was performed for this audit. Physical sharding and native-shell evidence belong to their separately dated reports.

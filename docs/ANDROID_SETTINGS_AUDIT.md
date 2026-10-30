# Android settings, tools and voice audit
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-31: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Source reviewed 2026-10-31 for the desktop evolution request. Both Android UI
flavors use the modern settings router. This is a route/controller inventory and
focused defect review, not a claim that every setting has passed physical testing.
Historical screenshots in `docs/assets/screenshots/settings.png` show an older
bottom-tab layout and are not current redesign evidence.

## Changes and rationale

- Basic now exposes models, voice, media, providers and memory alongside everyday
  preferences. Advanced opens six purpose categories instead of a long flat list.
  Search always covers both tabs and all categories; existing route IDs, build
  filters, stored preferences and working functionality are retained.
- Saved Quick access shortcuts are visible again in Basic. Category navigation and
  search reset the list position; Back restores the prior settings destination.
- Added `voice-settings`: the former media route reaches legacy audio-file tools,
  whereas live conversation and natural voices use HandsFreeVoiceDialog.
- Chat tools no longer disappear without explanation on online/routed/host chats.
  An explicit local-route button describes the change before Save. Group counts
  explain the ten-tool bound; additions over capacity are disabled while existing
  groups can always be switched off. Save failures appear beside Save. Core and
  Play review restrictions remain intact; no device or service grant is enlarged.
- Chat option saves now reject the short startup window before chat history has
  loaded instead of silently losing edits when no conversation is ready.
- In-chat live voice now opens a full-screen native surface. End, mute, manual
  Send voice and Interrupt remain outside the scrollable tuning/caption region.
  The main view separates Listening, Transcribing, Thinking and Speaking, reports
  real input activity, and keeps detailed provider/tuning controls behind Settings.
- Previously, no detected speech was silently retried every 30 seconds until the
  five-minute session limit. Capture now bounds initial silence to 12 seconds and
  live mode releases/pauses the microphone with explicit Retry guidance. Android
  mute/client-silenced states are errors. Manual Send accepts only actual audible
  samples; silent audio is not promoted to a successful turn.
- Optional quiet-input threshold adaptation is saved and off by default. It uses
  a bounded floor and observed quiet samples; fixed user thresholds remain the
  default. Speech recognition still requires compatible packs/language; it is not
  a semantic VAD. The activity meter uses measured logarithmic RMS, not fabricated
  waveform values. Animation honors the app motion preference.
- Reply-model preflight explains a missing/disabled reply route before recording.
  Speech packs do not replace a chat model. The current offline recognition packs
  support English; provider/Android language support differs.

## Complete catalog route trace

All 73 catalog IDs resolve to a concrete existing screen or the new direct voice
route. Every visible catalog entry belongs to exactly one category. The rows below
are **source-reviewed only**; credentials, runtime artifacts, permission grants,
paired devices and platform capabilities must still be exercised separately.
Controller references describe actual code paths, not live availability claims.

| Route | Screen source | Controller / boundary |
| --- | --- | --- |
| `dashboards` | [WorkspaceDashboards](../app/src/main/kotlin/com/meshlit/ui/modern/WorkspaceDashboards.kt) | Navigation to existing Models, Routing, Usage and VPN screens; no new backend. |
| `usage` | [WorkspaceDashboards](../app/src/main/kotlin/com/meshlit/ui/modern/WorkspaceDashboards.kt) | OnlineProviders usage/budget history; missing runtime counts remain unknown. |
| `vpn` | [WorkspaceDashboards](../app/src/main/kotlin/com/meshlit/ui/modern/WorkspaceDashboards.kt) | Existing network observations and optional Tailscale companion; no bundled VPN implementation. |
| `menu-layout` | [MenuLayoutScreen](../app/src/main/kotlin/com/meshlit/ui/modern/MenuLayoutScreen.kt) | MenuShortcutStore persists ordered IDs; saved shortcuts now appear in Basic settings. |
| `sharding` | [ShardingSettingsScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ShardingSettingsScreen.kt) | PipelineHost and approved workers; negotiated planning does not prove multi-phone execution. |
| `radar` | [RadarScreen](../app/src/main/kotlin/com/meshlit/ui/modern/RadarScreen.kt) | PeerRepository, DiscoveryModeStore and PipelineHost observations. |
| `host-map` | [HostMapScreen](../app/src/main/kotlin/com/meshlit/ui/modern/HostMapScreen.kt) | HostLocationStore and RelayManager; location sharing remains explicit. |
| `external-services` | [ExternalServiceScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ExternalServiceScreen.kt) | Saved endpoint directory and bounded URL check; a response is not model/tool readiness. |
| `web-bridge` | [WebBridgeEnrollmentScreen](../app/src/main/kotlin/com/meshlit/ui/modern/DeviceCompanionPanel.kt) | WebBridgeHost invitation and TLS enrollment; roles do not grant compute. |
| `device-dashboard` | [DeviceManagementScreen](../app/src/main/kotlin/com/meshlit/ui/modern/DeviceManagementScreen.kt) | Saved pairing, peer, relay and WebBridge directories; revocation has its own control. |
| `meshchat` | [PeerChatScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ConnectedToolsScreens.kt) | PeerChatHost foreground signed pairing and WebRTC; not offline multi-member messaging. |
| `securityprivacy` | [SecurityPrivacyScreen](../app/src/main/kotlin/com/meshlit/ui/modern/SecurityPrivacyScreen.kt) | DiscoveryModeStore and SettingsRepository; links filtered by BuildProfile. |
| `deepseek-harness` | [HarnessScreen](../app/src/main/kotlin/com/meshlit/harness/HarnessScreen.kt) | Existing owner-host harness client; requires configured host and credentials. |
| `tools-automation` | [ToolsAutomationScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ToolsAutomationScreen.kt) | McpToolRegistry, GatewayHost and saved hook registry; inventory does not grant tool authority. |
| `mcp-catalog` | [McpCatalogScreen](../app/src/main/kotlin/com/meshlit/ui/modern/AgentMcpScreens.kt) | GatewayHost remote-route enrollment; catalog listing does not authorize invocation. |
| `rag` | [RagSettingsScreen](../app/src/main/kotlin/com/meshlit/ui/screens/settings/RagSettingsScreen.kt) | SettingsRepository saves retrieval/loop mode; this switch alone does not implement source connectors. |
| `huggingface` | [HuggingFaceDashboardScreen](../app/src/main/kotlin/com/meshlit/ui/modern/HuggingFaceDashboardScreen.kt) | ModelLibrary with actual Hub catalog/account requests; public listing is not account entitlement. |
| `agent-registry` | [AgentRegistryScreen](../app/src/main/kotlin/com/meshlit/ui/modern/AgentMcpScreens.kt) | AgentProfileStore and McpToolRegistry inventory; not a second autonomous runtime. |
| `agent-logs` | [ModernLogsScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ModernLogsScreen.kt) | LogBuffer filtered to AGENT; log availability differs from task execution proof. |
| `guardrails` | [GuardrailSettingsScreen](../app/src/main/kotlin/com/meshlit/ui/modern/GuardrailSettingsScreen.kt) | LocalBehaviorSettings and GatewayHost policy; no hosted-policy bypass. |
| `search` | [SearchAccessScreen](../app/src/main/kotlin/com/meshlit/ui/modern/SearchAccessScreen.kt) | AppSearchService local/search grants and Brave credential handling. |
| `appearance` | [AppearanceSettings](../app/src/main/kotlin/com/meshlit/ui/modern/ModernSettingsScreen.kt) | SettingsRepository flows persist theme, display, language and motion preferences. |
| `crypto` | [CryptoScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ConnectedToolsScreens.kt) | CryptoHost plus operation/agent grants; secrets should remain in credential storage. |
| `gibberlink` | [GibberLinkScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ConnectedToolsScreens.kt) | GibberLinkHost foreground audio and saved Media grants; received text never auto-executes. |
| `remotecommands` | [RemoteCommandsScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ConnectedToolsScreens.kt) | RemoteCommandHost with pinned SSH node/action grants. |
| `p2p` | [PeerChatScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ConnectedToolsScreens.kt) | Alias of PeerChatScreen/PeerChatHost; retained for existing links. |
| `colibri` | [ColibriScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ColibriScreen.kt) | ColibriBackend saved host policy and real freshness checks; no automatic download/fallback. |
| `models` | [ModernModelsScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ModernModelsScreen.kt) | ModelLibrary and InferenceCoordinator actual model lifecycle. |
| `voice-settings` | [HandsFreeVoiceDialog](../app/src/main/kotlin/com/meshlit/ui/modern/HandsFreeVoiceDialog.kt) | New direct route to HandsFreeVoiceDialog, VoicePreferences, HandsFreeVoice and speech adapters; gates retained. |
| `media` | [MediaOptionsScreen](../app/src/main/kotlin/com/meshlit/ui/modern/MediaOptionsScreen.kt) | Existing MediaOptionsScreen routes to camera/vision, media generation and legacy WAV/STT/TTS tools; live voice has a dedicated route. |
| `configuration` | [ConfigurationScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ConfigurationScreen.kt) | ConfigurationTransfer schema validation, preview and partial apply; excludes secrets/trust/grants. |
| `personalization` | [PersonalizationScreen](../app/src/main/kotlin/com/meshlit/ui/modern/PersonalizationScreen.kt) | PersonalMemory encrypted explicit facts and policy; not model training or automatic capture. |
| `behavior` | [LocalBehaviorScreen](../app/src/main/kotlin/com/meshlit/ui/modern/LocalBehaviorScreen.kt) | LocalBehaviorSettings; applies local instructions and imported custom weights separately. |
| `recovery` | [RecoveryScreen](../app/src/main/kotlin/com/meshlit/ui/modern/RecoveryScreen.kt) | PipelineHost native checkpoint operations; replicated cluster recovery remains a separate gate. |
| `training` | [FineTuningScreen](../app/src/main/kotlin/com/meshlit/ui/modern/FineTuningScreen.kt) | TrainingHost and SshConnections; Soup training occurs on a paired POSIX host. |
| `router` | [ModelRouterScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ModelRouterScreen.kt) | ModelRoutes, ModelLibrary and OnlineProviders; saved route selection needs capable configured engines. |
| `cloud` | [CloudManagementScreen](../app/src/main/kotlin/com/meshlit/ui/modern/CloudManagementScreen.kt) | CloudManagement and AgentBackend; read adapters and per-purpose encrypted credentials, vendor gaps remain. |
| `operations` | [OperationsScreen](../app/src/main/kotlin/com/meshlit/ui/modern/OperationsScreen.kt) | OperationsControl, StopCoordinator and ClusterControls enforce saved feature grants/emergency stop. |
| `gateway` | [GatewayScreen](../app/src/main/kotlin/com/meshlit/ui/modern/GatewayScreen.kt) | GatewayHost persisted listener/route/policy and actual start/stop; MCP is not AG-UI. |
| `commands` | [ManualCommandsScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ManualCommandsScreen.kt) | RuntimeHost, SshConnections and AgentBackend; bounded explicitly selected execution. |
| `packages` | [LabPackagesScreen](../app/src/main/kotlin/com/meshlit/ui/modern/LabPackagesScreen.kt) | LabPackages through RuntimeHost; requires an actual ready guest/runtime. |
| `securitylab` | [SecurityLabScreen](../app/src/main/kotlin/com/meshlit/ui/modern/SecurityLabScreen.kt) | SecurityLab scoped target/job controls and optional companions; not bundled arbitrary tooling. |
| `providers` | [OnlineProvidersScreen](../app/src/main/kotlin/com/meshlit/ui/modern/OnlineProvidersScreen.kt) | OnlineProviders persisted encrypted profiles; endpoint/model/account capabilities checked separately. |
| `power` | [PowerCostsScreen](../app/src/main/kotlin/com/meshlit/ui/modern/PowerDevicesScreens.kt) | PowerRepository actual measurements and saved policy; unknown sensors/cost remain unknown. |
| `external` | [ExternalDevicesScreen](../app/src/main/kotlin/com/meshlit/ui/modern/PowerDevicesScreens.kt) | ExternalDevices USB/SAF discovery and Android grants; no automatic device driver. |
| `acceleration` | [AccelerationScreen](../app/src/main/kotlin/com/meshlit/ui/modern/AccelerationScreen.kt) | Saved backend preferences and actual runtime availability; no generic GPU/NPU guarantee. |
| `agents` | [AgentManagementScreen](../app/src/main/kotlin/com/meshlit/ui/modern/PowerDevicesScreens.kt) | AgentBackend durable scopes/jobs plus model/provider/tool inventories. |
| `ssh` | [SshConnectionsScreen](../app/src/main/kotlin/com/meshlit/ui/modern/SshConnectionsScreen.kt) | SshConnections, host pins and encrypted credentials; explicit endpoint grants. |
| `firewall` | [FirewallSettingsScreen](../app/src/main/kotlin/com/meshlit/ui/modern/FirewallSettingsScreen.kt) | SettingsRepository listener rules; not an OS-wide firewall. |
| `device` | [DeviceScreen](../app/src/main/kotlin/com/meshlit/ui/screens/settings/DeviceScreen.kt) | Existing device profile/hardware screen; capability selection is not execution proof. |
| `resources` | [ResourcesScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ResourcesScreen.kt) | ResourceSnapshotReader observes actual platform sensors/resources. |
| `tasks` | [TaskManagerScreen](../app/src/main/kotlin/com/meshlit/ui/modern/TaskManagerScreen.kt) | AgentBackend TaskBoard and human/agent job controllers; planning records are distinct from jobs. |
| `ide` | [CodeWorkspaceScreen](../app/src/main/kotlin/com/meshlit/ide/CodeWorkspaceScreen.kt) | CodeWorkspace bounded source/file operations; not a full phone IDE toolchain. |
| `hyperl` | [HyperLScreen](../app/src/main/kotlin/com/meshlit/ui/modern/HyperLScreen.kt) | OperationsControl, HyperLLibrary and HyperLDatasets; separate optional licence and native CPU/source-generation boundaries. |
| `permissions` | [PermissionSetupScreen](../app/src/main/kotlin/com/meshlit/permissions/PermissionSetup.kt) | PhoneAdministration and Android permission intents; no automatic grant. |
| `files` | [FileManagerScreen](../app/src/main/kotlin/com/meshlit/ui/modern/FileManagerScreen.kt) | FileManager local SAF grants and bounded archive/preview operations. |
| `termux` | [ExistingTermuxSettings](../app/src/main/kotlin/com/meshlit/ui/modern/ModernSettingsScreen.kt) | TermuxBridge and saved delegation; requires separately installed Termux and permission. |
| `notifications` | [ModernSettingsScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ModernSettingsScreen.kt) | Android system notification settings Intent; system owns channel state. |
| `monitor` | [ModernMonitorScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ModernMonitorScreen.kt) | Actual device/model/pipeline observations; unknown measurements remain unknown. |
| `audit` | [AuditTelemetryScreen](../app/src/main/kotlin/com/meshlit/ui/modern/AuditTelemetryScreen.kt) | AuditTelemetry/TracingController and SettingsRepository; opt-in metadata, retention and bounded export. |
| `logs` | [ModernLogsScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ModernLogsScreen.kt) | LogBuffer filtering and export; not all events imply execution success. |
| `identity-recovery` | [RecoveryScreen](../app/src/main/kotlin/com/meshlit/ui/v2/screens/PeeringSettingsScreens.kt) | V2 identity recovery screen and protected identity backup; distinct from inference checkpoints. |
| `rewards` | [RewardsScreen](../app/src/main/kotlin/com/meshlit/ui/v2/screens/PeeringSettingsScreens.kt) | V2 signed local compute receipt/wallet controls; not a currency exchange or income guarantee. |
| `network` | [ModernNetworkScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ModernNetworkScreen.kt) | ModernNetworkScreen pairing/transport pages; approval and actual native capability remain separate. |
| `peers` | [ForwardingPeersScreen](../app/src/main/kotlin/com/meshlit/ui/screens/settings/ForwardingPeersScreen.kt) | ForwardingPeersScreen saved inference-router peers; not implicit trust. |
| `runtime` | [RuntimeDashboardCard](../app/src/main/kotlin/com/meshlit/ui/screens/cloud/RuntimeDashboardCard.kt) | RuntimeHost/QEMU dashboard, persisted opt-in; executable and guest artifacts required. |
| `crawler` | [CrawlerSettingsCard](../app/src/main/kotlin/com/meshlit/ui/screens/cloud/CrawlerSettingsCard.kt) | Crawler settings and scoped companion client; Python/Chromium run on an owner host. |
| `openclaw` | [OpenClawScreen](../app/src/main/kotlin/com/meshlit/ui/modern/OpenClawScreen.kt) | OpenClawHost/OpenClawNode/AndroidControl; distinct protocol and explicit Android scopes. |
| `automation` | [AndroidAutomationSettingsScreen](../app/src/main/kotlin/com/meshlit/ui/screens/cloud/AndroidAutomationSettingsScreen.kt) | AndroidAutomationSettingsScreen and SettingsRepository; Accessibility/app grants remain explicit. |
| `hooks` | [HooksScreen](../app/src/main/kotlin/com/meshlit/ui/screens/settings/HooksScreen.kt) | SettingsRepository hook registry and reviewed HookEngine; no downloaded script auto-enable. |
| `help` | [HelpTutorialScreen](../app/src/main/kotlin/com/meshlit/ui/modern/HelpTutorialScreen.kt) | Bundled offline help and tutorial route links; read progress is not execution evidence. |
| `legal` | [LegalDocumentsScreen](../app/src/main/kotlin/com/meshlit/legal/LegalScreens.kt) | Versioned offline legal documents/acceptance; not runtime feature consent. |
| `about` | [ModernSettingsScreen](../app/src/main/kotlin/com/meshlit/ui/modern/ModernSettingsScreen.kt) | BuildConfig/BuildProfile availability text and repository notices. |

## Verification and remaining gates

New/updated regression coverage:

- `SettingsDestinationsTest`: category coverage/uniqueness, profile filtering,
  natural-voice/memory search, complete Basic/Advanced partition.
- `ChatToolSelectionTest`: capacity, explicit local route prerequisite, restricted
  builds, and revocation while full or while a saved selection is now restricted.
- `VoiceTurnControlTest`: bounded silence, real-sample manual boundary, opt-in quiet
  calibration versus steady noise, and meter behavior. Existing cancellation,
  cleanup, memory admission and caption tests remain relevant.
- `SettingsNavigationBuild41Test`: real voice route, category navigation and
  cross-category search; `ChatToolsSettingsDeviceTest`: actual Save/reopen/cancel
  and capacity UI, restoring the owner's options without dispatching tools.
- `VoiceMicrophoneDeviceTest`: actual capture/cancel plus peak/minimum RMS evidence;
  this alone cannot prove speech recognition or a conversation.
- `LiveVoiceConversationDeviceTest`: requires the owner to speak after the actual
  Listening phase, then checks Whisper recognition, the current local chat reply,
  Piper playback and completed-turn state. It has no injected audio or cloud call;
  a failed or absent utterance must remain a failure. The real turn remains in chat.

These tests were prepared by this focused implementation task; consolidated Gradle,
lint, device and render results are recorded by the main build handoff, not inferred
here. A prerecorded reference WAV and passing unit checks do not prove that a live
human conversation works on a particular phone. No new GPU/NPU, distributed model,
mobile Docker/Electron or HyperL/LLM interoperability capability is claimed.

Partial mobile support keeps existing encrypted PersonalMemory, native inference,
AgentBackend/TaskBoard, paired host controls and explicit scopes. The optional
claude-mem/OpenMausBot/OpenBot desktop integrations are not silently installed on
Android and do not gain phone authority through a navigation link.

## Broader authority, persistence and automatic-feature review

The wider review traced selected high-impact switches beyond the navigation table.
It did not execute stored owner hooks, grant Accessibility, call paid providers, or
start a VM/remote script. Runtime qualification remains distinct from this review.

| Area | Actual authority and persistence | Finding / action |
| --- | --- | --- |
| Theme and appearance | SettingsRepository DataStore; theme keys only | Reset correctly removes only `theme.*`, leaving service grants and other settings intact. No broad reset change was needed. |
| Per-chat tools | ChatOptions per conversation, then tool-specific app/service/device gates | Tool selection and actual delegation remain separate. The new route/capacity explanations address the visible “cannot turn on” paths without granting a tool permission. |
| Worker group in chat settings | PipelineHost encrypted `active-group` | The chip previously wrote immediately despite Cancel. It now stages the selection until Save, checks that the cluster is idle, commits the group, and attempts restoration if immediate chat-option validation fails. Chat options and worker groups remain separate stores; this is not a cross-store transaction guarantee. |
| Android autonomous delegation | AndroidControl preferences plus SettingsRepository Android automation plus Android Accessibility plus package scope | The phone-agent page now displays the combined saved gate rather than showing effective access from one flag. Explicit user enablement changes both gates; disabling revokes the autonomous gate first. Android OS access remains separate. |
| AndroidControl writes | Checked SharedPreferences commits | Enable/app-scope/all-app writes previously ignored a failed commit. They now report failure and block autonomous dispatch for the process after a failed write, because Android may mutate its in-memory map even when disk persistence fails. Restart is required before re-enabling after that failure. |
| Voice preferences | Device-local JSON in SharedPreferences; no audio or credential values | Unknown additive top-level and nested fields survive recognized-option edits. Invalid JSON/unknown enums do not silently reset or overwrite settings on open. The UI blocks use until an explicit reset, which retains the original blob in a local backup. Checked writes report errors. |
| Voice automatic endpointing | Bounded microphone capture, local threshold, optional adaptation | Initial silence pauses capture with actionable diagnostics. Adaptive sensitivity is opt-in and does not create speech from zero PCM. Manual Send still requires audible samples. |
| Native model recovery | PersonalMemory recovery policy plus ChatController | Existing bounded same-model text retry remains distinct from tool replay, OS repair, downloads, distributed recovery or changing learned weights. |
| Colibri Auto/agent modes | ColibriBackend saved host access, fresh model observation, per-chat grant and operation gate | Source uses explicit host access and freshness; Auto is not a blanket network fallback. Disabling host/agent access is deliberately immediate in its own editor, and is different from ordinary staged provider edits. |
| Agent jobs and Stop | AgentBackend encrypted jobs/scopes and OperationsControl/StopCoordinator | Scope revocation cancels matching queued/running jobs. Saved roles and a listed tool are not authority. Disk failure and real process teardown still need named runtime evidence. |
| Hooks and script runner | SettingsRepository hook registry/master flow, HookEngine and ConfigScriptRunner | Audit found detached execution surviving join timeout, shared result-state ambiguity, missing-result success, remote target false-success and insufficient mid-run revocation checks. Dedicated hook/runner correction and isolated regression tests are assigned separately in this build. Do not call the old implementation fully qualified. |
| Host harnesses and optional desktop apps | Explicit host configuration and their existing protocols | Harness/companion presence does not grant phone access. OpenClaw, AG-UI and MCP remain distinct contracts. No installed host was assumed ready merely from a saved URL. |
| Older settings implementations | Legacy V1/V2 settings/view-model files remain in source | The modern catalog is the app navigation authority. Older Hugging Face token and SettingsSearchIndex paths must not be wired into the new catalog without reviewing persistence and stale capability claims. They were not removed or used as a feature-readiness source. |

The UI now uses a shared native `SettingsFeatureSection` for conversation and voice
settings: one bordered tonal container per feature, primary-colored wrapping title,
a divider under the title, and consistent internal spacing. Root categories have
colored titles and boundaries. These are semantic groups, not decorative boxes
around every switch. System theme/high contrast supplies foreground colors; font
scaling and long descriptions remain unconstrained by fixed single-line titles.

Additional focused regressions cover unreadable/future voice settings, nested
unknown-field retention, failed AndroidControl commits and effective revocation.
The device tools-settings test also checks worker-group Cancel versus Save and
restores the owner's original group/options. Passing those isolated contracts is
not proof of every automatic feature, browser action, remote harness or OS grant.

# Older Android Studio request and plan audit
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-24: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

## Current request reconciliation — 2026-11-06

The [Chat/Studio request inventory](CHAT_STUDIO_REQUEST_RECONCILIATION_2026-11-06.md)
reconciles this plan with the current main shell, passwordless local chat, bounded
project autonomy, bots, personalities, voice/translation and media requests.
Earlier architecture choices and completion labels below retain their dated
scope. Studio settings parity, durable history/RAG, training, media adapters,
managed device execution and release acceptance remain separate gates.

Reviewed 2026-10-23 against the separate `mllm` checkout's `PLAN.md`, `REQUESTS.md` and `TODO.md`. These files are historical requests and implementation notes from a different working tree. Their “done” labels do not prove that the current Meshlit APK contains or qualifies the same behavior. This table names the current gate to close before marking a request transferred.

| Older request | Current Meshlit evidence | Remaining gate |
| --- | --- | --- |
| R-01/06/09/19 settings, active model and popup regressions | Current Settings and Models have separate screens and the Android debug build compiles. | Recheck navigation, loaded-model truth, popup contrast and long text on both phones. |
| R-02/03 voice spectrum and animated icons | Pairing and Device radar now draw an active scan sweep. No current `MicSpectrumVisualizer` or reusable `AnimatedIcon` implementation was found. | Port only if live audio input level and accessible motion controls can be verified on device. |
| R-04 palettes and theme motion | Current Appearance settings provide palettes, fonts and animation control. | Test light/dark, large system text and reduced motion across all screens. |
| R-05/11/18 model download, import and progress | Current model catalog/download code and Models UI exist. | Test authenticated/failed URL, device import, resume/pause/cancel, storage exhaustion, integrity and load on a phone. GitHub/FTP import is a separate trust and provenance path. |
| R-10 agent screen and R-20/22 Auto Pilot repair | Current `AgentSession` has an autopilot continuation mode and the app has an agent UI. The older watchdog/repair executor was not found as a matching current module. | Design explicit observations, owner consent, bounded repair actions, rollback and audit; test cancellation and denied actions. |
| R-12 GitHub code/agent/tool import | Current file, agent and MCP areas are not proof of safe repository installation. | Add reviewed provenance, permissions, sandboxing and an owner approval path before code execution. |
| R-13 file management | A current Files workspace exists. | Test provider import/export, cancellation and hostile paths on device. |
| R-15 power, battery and thermal gauges | Current Resources/monitoring screens and device telemetry exist. | Check live samples, chart updates, units, thermal throttling and background/permission behavior on both phones. |
| R-21 bundled Qwen coder | Current starter APK uses a smaller SmolLM2 model to control install size. | Offer a verified optional coder-model download with hardware and storage admission; do not silently re-bundle a much larger model. |
| R-23 distributed LoRA training | `core-training` has ring/DiLoCo strategy contracts and tests; Fine-tuning describes a paired host job. | Prove a real licensed dataset, compatible training host, multi-device job, adapter output, evaluation, peer loss and cancellation. Contracts are not a completed phone cluster training run. |
| Old TODO #183 personality, skill templates and tuned weights | Current chat personality controls alter instructions. | Add per-model persona/skill templates and separately verified adapter selection; prompt styling is not weight tuning. |
| Old TODO #179 Ghostty terminal | Current Android terminal parser and separate desktop terminal paths exist. | Qualify any Android renderer replacement, dependency/license and NDK build before claiming Ghostty integration. |
| Old PLAN gateway/discovery/trust work | Current app has newer gateway, discovery, trust and pairing paths with different wiring. | Audit real call sites, denial/revocation, hostile peer payloads and end-to-end device behavior instead of copying old branch phase labels. |

The older plan's proposed commit order, phase tag and build status belong to that checkout. Current priority remains: working two-phone pairing and UI, safe model download/load, explicit permission boundaries, then physical multi-device inference and training gates.

## Desktop gap review, 2026-11-24

Compared the original Android Studio checkout’s PLAN.md, REQUESTS.md and TODO.md, the October Android gap audit, the November CS-01–CS-23 Studio inventory and the current native Studio entry points. Earlier prototypes and the current native package expose different features.

| Early request | Current desktop evidence | Missing or awaiting acceptance |
| --- | --- | --- |
| Durable chats, branching, personality and recall (CS-05, 07–09, 17) | Message actions, per-reply votes and an explicit local reply preference. Native threads are held in memory. | Encrypted restart-safe conversation history, session search/branching, per-model personas and evaluated learning from feedback. |
| Model sources, management and tuning (R-05, 11, 18; CS-03) | Public two-pane GGUF download picker, checksum verification, local-library imports, provider and peer routes. | Resumable transfer queue, gated entitlement handling, broad format execution, hardware-tested GPU offload and several concurrently resident models. |
| Documents, media and voice (CS-10–14) | Text/image attachments, Windows dictation and local read-aloud. | Durable document indexing with citations, qualified live captions/translation, integrated video/image/music generation and an end-to-end microphone test on target hardware. |
| Agents and automation (R-10, 12, 20, 22; CS-06, 16, 22–23) | Native IDE, actions dashboard, SSH profiles and MCP inventory. | Reviewed per-chat tool calls, independent durable agent runs, restart and revocation, native bot windows, verified tool imports and browser task execution. |
| Network and distributed execution (CS-15, 21) | Signed pairing, approved GGUF sharing, LAN inference and a Samsung LTE protocol-client test. HyperL has separate layer-worker evidence. | An end-to-end WAN test inside the released Android app, public relay commissioning, multi-host recovery/checkpoints and qualified physical accelerator combinations. |
| Contribution rewards and identity | Android signed opt-in receipts. Desktop uses the same receipt fields and contributor fingerprint. MeshCoin adds signed per-contributor chains, recordkeeper acknowledgments, optional percentage allocation and deterministic per-unit serials. | Cross-platform facade for desktop block replication on the released Android app, independent work verification, dispute handling and production settlement. |
| Phone/platform parity (R-02, 03, 04, 13, 15, 23; CS-20) | Desktop branding, themes, native toolchain and scoped device control exist. | Full-app hardware-backed lock, all-screen accessibility testing, mobile energy attribution, distributed LoRA qualification and parity for older phone-only workflows. |

The [original Android gap matrix](OLD_ANDROID_GAP_AUDIT_2026-10-23.md) also tracks cluster storage, cloud administration, group/offline messaging, resource billing, optional VM tooling and fleet diagnostics. See the [Studio request ledger](CHAT_STUDIO_REQUEST_RECONCILIATION_2026-11-06.md) for the individual CS request IDs.

The desktop catalog now offers LM Studio and Hugging Face as separate sources. The pinned official LM Studio SDK requests the model catalog and per-model download options. The HP's 0.4.28 guest session returned a permissions error for the Staff Picks RPC. Studio can instead read the public catalog at https://lmstudio.ai/models and follow publisher-listed GGUF sources to immutable Hugging Face files. The public page markup and the SDK's experimental Hub methods can change upstream. The Hugging Face dashboard supports paginated public GGUF search, publisher avatars, optional read-token sign-in, the user's model repositories, and sign-out. Read credentials stay in the extension secret store.

The desktop chat history control supports opt-in encrypted storage, reopening a recent chat, branching from a selected reply, pausing new saves, and deleting a saved chat. History is scoped to the selected model and output-filter setting. The public GGUF downloader retains partial bytes after interruption, validates HTTP ranges on retry, and verifies the complete file before registering it. Android download qualification and production reward settlement remain separate work.

## Archived Android builds through the 2.0.49 desktop preview

Checked on November 24, 2026. The August 1 planning snapshot is `1dfbad6`. The first Android shell is `798a564`. Change `446f76b` added an inference foreground service and Jobs UI. The August reward-unit change `1d291c5` was a design document. The October 8 menu snapshot `98faaf0` declared 43 settings destinations. The current Android code declares 77 and filters them by build profile. Four retired settings are hidden in the experimental profile. Some features, including contributions, have other entry points.

I checked the manifests, ZIP model entry names and sizes, and DEX name references in these retained debug packages. The comparison did not launch the archived packages. The physical Samsung currently reports version 2.0.48, code 48. The desktop comparison uses the 2.0.49 native VSIX and release branch, with older Compose Studio and companion implementations identified separately.

| Retained APK | Model entry | DEX name references found | SHA-256 |
| --- | --- | --- | --- |
| 0.1.0, code 1 | Qwen2.5-1.5B-Instruct Q4_K_M, 986,048,768 bytes | AgentExporter | `a898e1c0f5071ef151bebae2b30d53073bdcee08da8b5639ce8e2ee36a867fb4` |
| 0.2.3-v1, code 3 | SmolLM2-360M-Instruct Q8_0, 386,404,992 bytes | AgentExporter, AgentBudget, HookEngine, DocumentPreprocessor | `067e65525b316d01f46829ee956ffab2ed40eb9a215cea5b78ee8877a6711c54` |
| 2.0.0-v2build1, code 4 | SmolLM2-360M-Instruct Q8_0, 386,404,992 bytes | Same four references as 0.2.3-v1 | `b1bb9c2e1c71ff07dd72fd21a3fb65f5b8b1e1b8eef9c6b538b871032eca7417` |

### Original request ledger

| Old request | What carries forward |
| --- | --- |
| R-01, 06, 08, 09, 19 | Settings navigation, loaded-model identity and popup visibility need checks in the installed UI. Read the actual model asset and loaded model separately. |
| R-02, 03, 04, 14 | Port a microphone level display only with actual capture, plus accessible motion, contrast and theme controls. Use the old references for design comparison. |
| R-05, 07, 11, 18 | Reuse the verified, resumable GGUF manager. Look up current repository entitlement and qualify low-space, cancellation and restart on each platform. |
| R-10, 12, 20, 22 | Bring back agent exports and scoped actions first. Qualify reviewed tool imports, budget enforcement, watchdog recovery, cancel and rollback separately. |
| R-13, 17 | Preserve file manager and chat copy/save workflows. Add portable whole-conversation Markdown, code-only and versioned JSON Lines exports on both platforms. |
| R-15, 16 | Derive power and thermal gauges from measured values and show an unavailable reason for unsupported sensors. |
| R-21, 23 | Offer the coder model as an optional verified asset. Distributed LoRA still needs a real dataset, resulting adapter and evaluation. |
| TODO 179, 183 | Keep native desktop terminals. Evaluate Android Ghostty separately. Port per-model persona, skill templates and independently checked adapter loading. |
| R-700, R-701 | Reuse signed contribution receipts. MeshCoin replication, disputes and independent verification remain open. The old document preprocessor truncates long text and passes through binary input. OCR and durable retrieval with citations need their own implementation. |

### Feature-by-feature port decisions

Each ID from the checked-in feature map appears once. The source link identifies the Android, shared or historical implementation or design. **Available** describes a native desktop workflow. **Partial** identifies narrower coverage. **Companion** and **Prototype** need native package integration or hardware qualification. **Port** and **Design** are proposals. **Phone**, **Shared** and **Desktop** identify the platform that owns the work. These are source and artifact findings. Functional checks are reported separately.

| Feature and source | Desktop disposition | Delivery or remaining acceptance |
| --- | --- | --- |
| [local-inference](../core-inference/src/main/kotlin/com/meshlit/core/inference/RunAnywhereInferenceEngine.kt) | Available | Chat already uses a verified local CPU runtime. Exercise load, stop, unload and restart on clean machines. |
| [models](../app/src/main/kotlin/com/meshlit/models/ModelLibrary.kt) | Available | Models & providers supports verified GGUF downloads and imports. Qualify storage exhaustion, gated access and each additional runtime independently. |
| [chat](../app/src/main/kotlin/com/meshlit/chat/ChatController.kt) | Available | Native chat streams replies, identifies the model, saves encrypted history on opt-in and branches from a reply. Test upgrade and accessibility with existing chats. |
| [settings](../app/src/main/kotlin/com/meshlit/ui/modern/ModernSettingsScreen.kt) | Partial | Home has Management, Tools and Settings. Add a cross-page search index and apply the Basic and Advanced distinction consistently. |
| [tasks](../core-mcp/src/main/kotlin/com/meshlit/core/mcp/control/TaskBoard.kt) | Port | Put the durable task board in Management. Preserve parent IDs, revision checks, bulk operations and cancellation of actual executing jobs. |
| [typed-backend](../core-mcp/src/main/kotlin/com/meshlit/core/mcp/control/AgentCommands.kt) | Port | Reuse the human and agent operation schema behind desktop commands. Test authorization, idempotency, persistence and interruption. |
| [web-api](../core-mcp/src/main/kotlin/com/meshlit/core/mcp/control/ControlBridgeServer.kt) | Partial | The local model API serves inference. Port the distinct enrolled browser client workflow with scoped credentials and revocation. |
| [devices-groups](../core-mcp/src/main/kotlin/com/meshlit/core/mcp/control/DeviceDirectory.kt) | Port | Build nested groups and cluster membership under Devices using the Grid plan. Pairing establishes a peer identity only. |
| [layer-pipeline](../app/src/main/kotlin/com/meshlit/pipeline/PipelineHost.kt) | Prototype | HyperL and CPU worker tests provide evidence. Wire model placement into Studio, show ownership and recover from physical worker loss. |
| [worker-qr](../app/src/main/kotlin/com/meshlit/pipeline/LayerPairingQr.kt) | Partial | Studio has device-ID and link pairing. Add QR enroll/scan for the worker role with a fingerprint check and expiry. |
| [cluster-planning](../core-inference/src/main/kotlin/com/meshlit/core/inference/pipeline/ClusterNegotiation.kt) | Prototype | Reuse fresh resource offers and layer planning. Confirm actual native placement and reserved memory before enabling a cluster run. |
| [phone-replicas](../docs/layer-pipeline-and-recovery.md) | Design | Define which encrypted task and memory records replicate, conflict rules and deletion propagation. Include desktop and phone peers. |
| [storage-holders](../docs/layer-pipeline-and-recovery.md) | Design | Make model and checkpoint storage an explicit node role with verified content, quotas, deletion and recovery. |
| [failover](../docs/layer-pipeline-and-recovery.md) | Design | Implement fenced job leases and compatible checkpoint restoration. Test split ownership and loss of an active worker. |
| [monitor](../app/src/main/kotlin/com/meshlit/ui/modern/ModernMonitorScreen.kt) | Partial | Desktop reports actual CPU and RAM. Add history, storage, thermal status and per-device freshness where the OS exposes them. |
| [permissions](../app/src/main/kotlin/com/meshlit/permissions/PermissionSetup.kt) | Partial | Keep current per-tool and sharing controls. Add job-scoped grants and test revocation during a running operation on each OS. |
| [logs](../app/src/main/kotlin/com/meshlit/ui/modern/ModernLogsScreen.kt) | Port | Add a unified searchable, redacted log viewer and explicit TXT/JSONL export. Cover private prompts and tokens in redaction tests. |
| [workspace](../app/src/main/kotlin/com/meshlit/ide/CodeWorkspaceScreen.kt) | Available | The desktop project IDE supplies source editing, search and terminal access. Check opening existing Android-exported workspaces. |
| [ide-toolchain](../docs/code-workspace-and-permissions.md) | Desktop | Separate project windows and the bundled Zig compiler already execute code. Qualify debugger support per language and host. |
| [sandbox-vm](../app/src/main/kotlin/com/meshlit/sandbox/RuntimeHost.kt) | Port | Expose an optional owner-installed VM through start, status and stop operations. Prove clean shutdown and isolation per host. |
| [root-vnc](../docs/runtime-and-sandbox.md) | Bridge | Keep phone-specific privileged operations on the owner device. Offer an explicit authorized remote display session for compatible guests. |
| [terminal](../app/src/main/kotlin/com/meshlit/terminal/TerminalSession.kt) | Desktop | Use the native integrated terminal and shell profiles. A paired Android device can retain its own Termux setup. |
| [network-tools](../core-net/src/main/kotlin/com/meshlit/core/net/NetworkDiagnostics.kt) | Port | Start with read-only diagnostics and redacted exports. Add desktop capture only with platform-specific consent and accurate status. |
| [ssh-enrollment](../docs/platform-and-backend-contract.md) | Design | Separate ordinary SSH terminals from worker provisioning. Preview the specific host changes and verify worker identity after enrollment. |
| [crawler](../companions/crawler/service.py) | Companion | Expose the optional crawler through a reviewed endpoint with domain scope, result size limits and cancellation. |
| [browser-agent](../app/src/main/kotlin/com/meshlit/browser/BrowserActivity.kt) | Port | Use a visible desktop browser session with scoped actions, an action trace and an effective Stop control. |
| [openclaw](../app/src/main/kotlin/com/meshlit/openclaw/OpenClawHost.kt) | Bridge | Keep Android phone actions on the phone. Add a desktop client for a paired gateway and qualify the protocol on real devices. |
| [autonomous-os](../app/src/main/kotlin/com/meshlit/openclaw/AndroidControl.kt) | Partial | Desktop has reviewed local app control using platform helpers. Test target selection, denied permissions, revocation and stop on all three OSes. |
| [future-platforms](../docs/platform-and-backend-contract.md) | Partial | Mac, Windows and Linux desktop previews ship. Keep further OS ports independently qualified. |
| [vendor-backends](../docs/platform-and-backend-contract.md) | Prototype | Intel and AMD ran one Qwen projection together in a Metal experiment. Qualify whole-model output, speed, memory and fallback before exposing that mode. |
| [tutorial](../app/src/main/kotlin/com/meshlit/ui/modern/HelpTutorialScreen.kt) | Partial | Desktop has a welcome flow. Adapt the Android offline walkthrough to real desktop paths, with resume and accessible keyboard navigation. |
| [tracking](../AGENTS.md) | Shared | Use the same release evidence ledger and replace stale cross-platform feature counts whenever a package changes. |
| [device-runtime-policy](../core-inference/src/main/kotlin/com/meshlit/core/inference/models/DeviceRuntimePolicy.kt) | Partial | Studio uses host CPU and memory observations. Add fresh runtime probes, model-specific admission and a clear reason for unavailable hardware. |
| [context-and-kv](../core-inference/src/main/kotlin/com/meshlit/core/inference/models/ModelRuntimeOptions.kt) | Partial | Expose effective model context and cache settings from the running process. Verify readback after restart and at memory limits. |
| [hugging-face](../core-inference/src/main/kotlin/com/meshlit/core/inference/models/HuggingFaceClient.kt) | Available | Public discovery and optional read-token sign-in are available. Test private and gated entitlements with an approved account. |
| [bundled-model](../app/src/main/assets/models/bundled-model.json) | Available | The desktop preview bundles Qwen2.5-1.5B. Verify the packaged hash, model identity and offline reply on each target. |
| [online-models](../app/src/main/kotlin/com/meshlit/providers/OnlineProviders.kt) | Available | Use explicitly chosen compatible endpoints and their model inventory. Test expired credentials and unreachable endpoints. |
| [power-costs](../app/src/main/kotlin/com/meshlit/power/PowerRepository.kt) | Port | Add measured battery and power-source readings where available, with units and source. Keep model usage estimates separate from electrical measurements. |
| [external-devices](../app/src/main/kotlin/com/meshlit/devices/ExternalDevices.kt) | Bridge | Desktop uses OS volume and USB permissions. Keep Android OTG and Storage Access Framework grants on the phone. |
| [acceleration-settings](../app/src/main/kotlin/com/meshlit/models/AccelerationPreferences.kt) | Partial | CPU thread limits are available. Show GPU inventory separately from full-model backend eligibility and measured speed. |
| [ssh-client](../core-ssh/src/main/kotlin/com/meshlit/core/ssh/SshClient.kt) | Partial | Desktop has SSH profiles or terminal handoff where the controller is available. Test host-key change, key selection and disconnect. |
| [listener-policy](../core-firewall/src/main/kotlin/com/meshlit/core/firewall/PortAwareFirewall.kt) | Port | Expose a Meshlit listener inventory and scoped bind rules. Verify loopback defaults and revocation for each listener. |
| [agent-management](../app/src/main/kotlin/com/meshlit/ui/modern/PowerDevicesScreens.kt) | Port | Port named agent profiles, scoped job submission, audit logs, expiry and cancellation into the native release. |
| [configuration-transfer](../app/src/main/kotlin/com/meshlit/configuration/PortableConfiguration.kt) | Port | Export versioned non-secret settings with preview and rollback. Recreate device-local secret and consent grants on the destination. |
| [media-options](../app/src/main/kotlin/com/meshlit/ui/modern/MediaOptionsScreen.kt) | Partial | Chat accepts selected text and images. Validate real vision models and explicit camera or microphone access on each OS. |
| [iot-enrollment](../core-mcp/src/main/kotlin/com/meshlit/core/mcp/control/DeviceDirectory.kt) | Port | Support consented sensor and relay nodes in Devices with typed capabilities and expiry. Admit compute only after an execution probe. |
| [radio-media-federation](../docs/media-iot-and-radio-nodes.md) | Design | Use an optional paired hardware service. Define stream permissions, formats and lifecycle before desktop integration. |
| [scenario-model-router](../core-inference/src/main/kotlin/com/meshlit/core/inference/models/ModelRouter.kt) | Port | Port named routing rules, sequential model chains and side-by-side answers. Show the actual route and model used for each step. |
| [chat-text-attachments](../app/src/main/kotlin/com/meshlit/ui/modern/ChatAttachmentActions.kt) | Available | The desktop composer reads bounded text attachments. Test character encoding, oversized input and destination disclosure. |
| [online-media-generation](../core-inference/src/main/kotlin/com/meshlit/core/inference/models/OnlineMediaClient.kt) | Port | Add optional compatible local or free adapters, cancellation and artifact export. Verify each supported media type with actual output. |
| [local-model-behavior](../core-inference/src/main/kotlin/com/meshlit/core/inference/models/LocalModelBehavior.kt) | Partial | Desktop has reply preferences and output filtering. Add per-model profiles and evaluate how saved preferences change later answers. |
| [host-fine-tuning](../companions/training/meshlit_training.py) | Companion | Expose a user-selected training host with dataset consent, progress, cancel and adapter evaluation. Qualify a real training output. |
| [native-local-checkpoints](../app/src/main/kotlin/com/meshlit/recovery/NativeCheckpointStore.kt) | Port | Port encrypted model KV save and restore, bound to exact model and runtime. Test incompatible restore and deletion. |
| [cloud-credential-management](../core-cloud-mcp/src/main/kotlin/com/meshlit/core/cloudmcp/management/CloudProfiles.kt) | Partial | Reuse the credential UI where present. Add reviewed read-only provider inventory and usage with independent account acceptance. |
| [ai-file-archives](../app/src/main/kotlin/com/meshlit/ui/modern/FileManagerScreen.kt) | Partial | Desktop inspects model artifacts. Add bounded archive preview, extraction and export with path and storage validation. |
| [audit-telemetry](../app/src/main/kotlin/com/meshlit/observability/AuditTelemetry.kt) | Port | Start with local signed activity events and redacted export. Make optional collector delivery explicit and test offline operation. |
| [first-use-legal-agreement](../app/src/main/kotlin/com/meshlit/legal/LegalAgreementStore.kt) | Port | Add versioned, offline-readable notices and a local record of acceptance where the selected release channel requires it. |
| [play-review-distribution](../app/src/playReview/AndroidManifest.xml) | Phone | Keep Play review, Android manifests and AAB packaging on Android. Desktop has separate installer and signing checks. |
| [ai-assisted-repair](../docs/AI_REPAIR_AND_EVOLUTION.md) | Design | Begin with diagnosis and a human-reviewed repair plan. Verify a bounded repair, rollback and a durable outcome record. |
| [signed-update-channels](../PLAN.md) | Partial | Preview archives have checksums and attestations. Qualify trusted updates, rollback, and signing or an explicitly unsigned distribution policy. |
| [security-lab-hardware-access](../docs/SECURITY_LAB_AND_HARDWARE_ACCESS.md) | Phone | The Android route is retired. Any desktop diagnostics should have a separate defensive scope and physical hardware qualification. |
| [embedded-agent-gateway](../core-mcp/src/main/kotlin/com/meshlit/core/mcp/gateway/EmbeddedGateway.kt) | Port | Port authenticated, typed MCP and A2A requests with separate grants for inference and tool execution. |
| [manual-command-input](../app/src/main/kotlin/com/meshlit/ui/modern/ManualCommandsScreen.kt) | Partial | Desktop has a terminal. Add typed reviewed device commands with target preview, expiry and reported outcome. |
| [vm-security-assessments](../app/src/main/kotlin/com/meshlit/security/SecurityLab.kt) | Phone | Keep the retired lab outside ordinary Studio parity. Isolated, read-only diagnostics can have their own later scope. |
| [vm-lab-packages](../app/src/main/kotlin/com/meshlit/security/LabPackages.kt) | Phone | Keep phone guest package management out of Studio Home. Any future VM workspace manages its own packages. |
| [manual-replica-journal](../core-federation/src/main/kotlin/com/meshlit/core/federation/recovery/ReplicaJournal.kt) | Prototype | Reuse the signed fixed-membership journal contract. Validate restart, revocation and conflict handling on independent physical nodes. |
| [remote-agent-routes](../core-mcp/src/main/kotlin/com/meshlit/core/mcp/gateway/RemoteAgentClient.kt) | Port | Port owner-enrolled MCP and A2A routes with scoped inputs, deadlines, revocation and visible destinations. |
| [terraform-saved-plan](../companions/deployment/meshlit_terraform.py) | Companion | Keep deployment an optional operator-owned tool. Present the saved plan and require explicit approval for the exact target and plan. |
| [storage-dsp-companion](../companions/node/meshlit_node.py) | Companion | Allow optional content storage and offline signal processing through bounded typed jobs; verify returned hashes. |
| [operations-dashboard](../app/src/main/kotlin/com/meshlit/operations/StopCoordinator.kt) | Partial | Management exposes local controls. Add a durable jobs list, stop acknowledgement and reconciliation of work on remote nodes. |
| [unified-ai-routes](../core-mcp/src/main/kotlin/com/meshlit/core/mcp/gateway/UnifiedRoute.kt) | Partial | Desktop has provider and paired inference routes. Add explicit per-session MCP and A2A actions and their independent grants. |
| [accelerator-node-probe](../companions/node/accelerators.py) | Prototype | Expose accelerator reports with driver, backend, device and observed timestamp. Require model execution evidence before offering a worker. |
| [platform-native-contracts](../core-common/src/main/kotlin/com/meshlit/core/common/platform/PlatformContract.kt) | Partial | Desktop has three OS package paths. Test OS-specific adapters on actual target hardware independently of packaging CI. |
| [node-network-baseline](../companions/node/meshlit_node.py) | Companion | Integrate authenticated latency and bandwidth checks before cluster placement and retain measurement freshness. |
| [hyperl-libraries](../core-gpu/src/main/kotlin/com/meshlit/core/gpu/HyperLLibrary.kt) | Prototype | Connect Studio to standalone HyperL and verify actual local, layer-worker and recovery routes through the packaged UI. |
| [global-content-search](../app/src/main/kotlin/com/meshlit/search/AppSearchService.kt) | Partial | Recent chat search and model catalog search exist. Index permitted settings, devices and local documents with scoped results. |
| [reply-reader-token-controls](../app/src/main/kotlin/com/meshlit/ui/modern/ReplyContent.kt) | Partial | Studio renders messages, model identity and reply actions. Add reader accessibility and native usage details per turn where available. |
| [cluster-output-budget](../app/src/main/kotlin/com/meshlit/chat/ClusterOutputBudget.kt) | Port | Port manual output limits first. Enable automatic cluster budgets only when fresh measured evidence exists for that route. |
| [ssh-node](../core-ssh/src/main/kotlin/com/meshlit/core/ssh/NodeSshServer.kt) | Bridge | Keep the phone's inbound SSH identity and grant on Android. Use approved desktop profiles and verify remote target identity. |
| [production-candidate-channel](../app/src/main/kotlin/com/meshlit/BuildProfile.kt) | Port | Publish an explicit desktop stable scope and qualify installs and upgrades independently of the experimental channel. |
| [multiplatform-workspace-contracts](../shared-workspace/src/commonMain/kotlin/com/meshlit/workspace/Workspace.kt) | Shared | Reuse language, response and formatting fixtures across Android and desktop. Test each renderer for keyboard and screen-reader output. |
| [desktop-host-client](../desktopApp/src/main/kotlin/com/meshlit/desktop/Main.kt) | Desktop | The older Compose host client is a separate implementation. Preserve useful authenticated-host behavior under the current Studio entry points. |
| [harmonyos-next-native-preview](../ohosApp/entry/src/main/ets/pages/Index.ets) | Phone | The native HarmonyOS client has its own toolchain and device acceptance. Pair it through the shared protocol if qualified. |
| [colibri-host](../core-inference/src/main/kotlin/com/meshlit/core/inference/models/ColibriClient.kt) | Companion | Implement explicit Off, On and Auto policy for a compatible host with clear evidence of which backend served a request. |
| [gibberlink-audio](../core-gibberlink/src/main/cpp/bridge.cpp) | Phone | Android retired the audio-packet route. An opt-in audio companion would need a separate use case and proof. |
| [local-cryptography](../core-mcp/src/main/kotlin/com/meshlit/core/mcp/crypto/CryptoEngine.kt) | Port | Port the offline hash and encryption utilities separately from the pairing keys and wallet, with local-only input handling. |
| [remote-chat-commands](../app/src/main/kotlin/com/meshlit/remote/RemoteCommandHost.kt) | Port | Require a named target and a scoped grant for each typed action. Return an attributable result to the chat. |
| [p2p-chat-commands](../app/src/main/assets/p2p/index.html) | Port | Add authenticated peer messaging, then room membership, offline delivery and separate command grants. |
| [model-usage-budgets](../shared-workspace/src/jvmMain/kotlin/com/meshlit/workspace/UsageLedger.kt) | Partial | Show runtime-reported usage or unknown. Add local per-model quotas, reset periods and user-controlled alerts. |
| [optional-vpn-observations](../desktopApp/src/main/kotlin/com/meshlit/desktop/TailscaleProbe.kt) | Companion | Add read-only reachability for an owner-selected VPN and qualify actual routes and expiry. |
| [deepseek-harness-workspace](../app/src/main/kotlin/com/meshlit/harness/HarnessScreen.kt) | Companion | Offer a named external harness as an optional project integration. Verify its lifecycle, endpoint scope and cancel behavior. |
| [studio-project-memory](../companions/studio-runtime/extension/memory.cjs) | Companion | Project memory exists in a companion. Verify trusted workspace isolation and encrypted reopen in the native package before claiming parity. |
| [studio-agent-companions](../companions/agents/prepare.py) | Companion | Keep optional companion packages independent. Verify provenance, permissions, execution and stopping each installed adapter. |
| [voice-conversation-tuning](../app/src/main/kotlin/com/meshlit/chat/HandsFreeVoice.kt) | Partial | Windows dictation, local read-aloud and companion links cover parts of voice. Test a full local multilingual turn, interruption and device release. |
| [external-resource-participation](../core-mcp/src/main/kotlin/com/meshlit/core/mcp/gateway/GatewayResources.kt) | Port | Expose resource offers, expiry and owner limits per node. Reconcile leases against resources actually used. |
| [adaptive-replica-paths](../core-federation/src/main/kotlin/com/meshlit/core/federation/recovery/ReplicaHttpTransport.kt) | Prototype | Use verified alternate replicas in the desktop model service. Test holder loss and reject stale or corrupted copies. |
| [verified-shard-recovery](../core-inference/src/main/kotlin/com/meshlit/core/inference/cluster/ShardAssembler.kt) | Prototype | Pair verified partial reconstruction with a real desktop cluster job. Distinguish file recovery from resuming a generation. |
| [optional-gateway-prompt-protection](../core-mcp/src/main/kotlin/com/meshlit/core/mcp/gateway/GatewayPolicy.kt) | Partial | Local output filtering exists. Port the gateway setting with its own scope and test it at the remote boundary. |
| [agent-teams](../core-mcp/src/main/kotlin/com/meshlit/core/mcp/control/AgentTeams.kt) | Companion | Team orchestration exists in development sources. Qualify concurrent execution, cancellation, limits and per-member audit in the native release. |
| [connected-context-automation](../core-mcp/src/main/kotlin/com/meshlit/core/mcp/integrations/CompanionHttp.kt) | Partial | Desktop supports selected attachments and local app actions. Add connector-specific data grants and auditable revocation. |
| [device-connection-assistance](../core-mcp/src/main/kotlin/com/meshlit/core/mcp/control/DeviceConnectionCoordinator.kt) | Partial | LAN discovery, device IDs, pairing links and a private relay exist. Verify public WAN pairing inside the Samsung app and reconnect after interruption. |
| [studio-main-shell](../companions/studio-runtime/extension/shell.cjs) | Desktop | General Chat and separate project IDE windows are available. Preserve the distinction across restart and launch routes. |
| [studio-chat-personality](../companions/studio-runtime/extension/chat-personality.cjs) | Partial | The native chat has explicit feedback and preferences. Validate per-model personalization and end-to-end speech independently. |
| [studio-management-security](../companions/studio-runtime/extension/management.cjs) | Partial | Management has native controls and companion handoffs. Verify credential rotation, target scope and access revocation in the installed package. |
| [studio-bot-dashboard](../companions/studio-runtime/extension/bots.cjs) | Companion | Bot profiles and separate detail windows exist in the other Studio shell. Qualify the full workflow in the published native edition. |

### Android settings destinations

Each setting links conceptually to a feature ID above. The October column identifies entries present in the 43-destination snapshot. The current build filters four retired IDs and imposes further restrictions in Core and Play review.

| Android setting | October snapshot | Desktop workstream or platform-specific detail |
| --- | --- | --- |
| Management (`dashboards`) | Added later | operations-dashboard |
| Usage & costs (`usage`) | Added later | model-usage-budgets |
| VPN & network (`vpn`) | Added later | optional-vpn-observations |
| Customize menu (`menu-layout`) | Added later | persist user-defined shortcuts |
| Sharding and workers (`sharding`) | Added later | layer-pipeline |
| Device radar (`radar`) | Added later | nearby topology and last verified contact |
| Global host map (`host-map`) | Added later | map consenting peers only and expire stale locations |
| LAN and VPN services (`external-services`) | Added later | listener-policy |
| Meshlit Web/API bridge (`web-bridge`) | Added later | web-api |
| Device management (`device-dashboard`) | Added later | devices-groups |
| Mesh chat (`meshchat`) | Added later | p2p-chat-commands |
| Security and privacy (`securityprivacy`) | Added later | permissions |
| DeepSeek Harness (`deepseek-harness`) | Added later | deepseek-harness-workspace |
| Tools and automation (`tools-automation`) | Added later | agent-management |
| MCP connections (`mcp-catalog`) | Added later | embedded-agent-gateway |
| Connected capabilities (`connected-capabilities`) | Added later | connected-context-automation |
| Knowledge library (`rag`) | Added later | select sources, index them durably and cite retrieved passages |
| Hugging Face dashboard (`huggingface`) | Added later | hugging-face |
| Agent registry (`agent-registry`) | Added later | agent-management |
| Agent logs (`agent-logs`) | Added later | per-run timeline, export and redaction |
| Local behavior and guardrails (`guardrails`) | Added later | optional-gateway-prompt-protection |
| Search access and articles (`search`) | Added later | global-content-search |
| Appearance (`appearance`) | Yes | theme, text scaling and reduced motion |
| Local cryptography (`crypto`) | Added later | local-cryptography |
| GibberLink and English transcript (`gibberlink`) · retired setting | Added later | gibberlink-audio |
| Device and cluster commands (`remotecommands`) | Added later | remote-chat-commands |
| P2P chat and commands (`p2p`) | Added later | p2p-chat-commands |
| Colibri host (`colibri`) | Added later | colibri-host |
| Models and downloads (`models`) | Yes | models |
| Voice and speech (`voice-settings`) | Added later | voice-conversation-tuning |
| Camera, vision and audio (`media`) | Yes | media-options |
| Configuration profiles (`configuration`) | Yes | configuration-transfer |
| Memory and personality (`personalization`) | Yes | studio-chat-personality |
| Custom local model behavior (`behavior`) | Yes | local-model-behavior |
| Checkpoints and recovery (`recovery`) | Yes | failover |
| Fine-tuning (`training`) | Yes | host-fine-tuning |
| Model router (`router`) | Yes | scenario-model-router |
| Cloud management dashboard (`cloud`) | Yes | cloud-credential-management |
| Operations dashboard (`operations`) | Yes | operations-dashboard |
| Agent Gateway (`gateway`) | Yes | embedded-agent-gateway |
| Commands and instructions (`commands`) | Yes | manual-command-input |
| Lab packages (`packages`) · retired setting | Yes | vm-lab-packages |
| Security Lab (`securitylab`) · retired setting | Yes | security-lab-hardware-access |
| Online providers (`providers`) | Yes | online-models |
| Power and costs (`power`) | Yes | power-costs |
| External devices and OTG (`external`) | Yes | external-devices |
| Acceleration (`acceleration`) | Yes | acceleration-settings |
| Agent role library (`agent-catalog`) | Added later | agent-management |
| Agent teams (`agent-teams`) | Added later | agent-teams |
| Bots and connections (`bot-connections`) | Added later | studio-bot-dashboard |
| Agent management (`agents`) | Yes | agent-management |
| SSH nodes and connections (`ssh`) | Yes | ssh-client |
| Network rules (`firewall`) | Yes | listener-policy |
| Device (`device`) | Yes | device-runtime-policy |
| Resources and sensors (`resources`) | Added later | external-resource-participation |
| Task manager (`tasks`) | Yes | tasks |
| Code workspace (`ide`) | Yes | workspace |
| HyperL libraries (`hyperl`) | Yes | hyperl-libraries |
| App permissions (`permissions`) | Yes | permissions |
| Files and storage (`files`) | Yes | ai-file-archives |
| Termux integration (`termux`) | Yes | terminal |
| Notifications (`notifications`) | Yes | desktop notification categories and quiet hours |
| Monitoring and layer pipeline (`monitor`) | Yes | monitor |
| Audit and telemetry (`audit`) | Yes | audit-telemetry |
| Logs and export (`logs`) | Yes | logs |
| Device identity and recovery (`identity-recovery`) | Added later | export and restore verified identity using owner consent |
| Mesh rewards wallet (`rewards`) · retired setting | Added later | MeshCoin wallet, signed contribution receipts and optional recordkeeper allocation |
| Network and pairing (`network`) | Yes | device-connection-assistance |
| Forwarding peers (`peers`) | Yes | devices-groups |
| Linux runtime and sandbox (`runtime`) | Yes | sandbox-vm |
| Web crawler (`crawler`) | Yes | crawler |
| Phone agent and autonomy (`openclaw`) | Yes | openclaw |
| Android automation (`automation`) | Yes | autonomous-os |
| Agent hooks (`hooks`) | Yes | reviewed lifecycle triggers, audit and revocation |
| Guide and tutorial (`help`) | Yes | tutorial |
| Terms and privacy (`legal`) | Yes | first-use-legal-agreement |
| About and availability (`about`) | Yes | tracking |

### Desktop advantages available in the preview

- General Chat and the full Code OSS project IDE open in separate native windows. The IDE includes a terminal and a bundled Zig compiler.
- The desktop catalog imports compatible GGUF files in place from existing LM Studio or Bionic libraries and exposes a two-pane public model browser.
- Local desktop app-control helpers target macOS accessibility, Windows automation and Linux AT-SPI. Each OS has its own permission and qualification boundary.
- The release provides an Intel Mac archive, an Apple Silicon archive, a Linux x64 archive and Windows portable and installer packages.

The following build adds Moltbook to the original 77 setting declarations. The final sweep opened all 74 visible destinations on the Samsung and the API 34 emulator. The Samsung also passed the separate General, Voice, Advanced tools, cross-category search, cryptography and network navigation test. These sweeps verify entry and rendering. Account authorization, external provider writes and hardware-specific inference each require their own evidence.

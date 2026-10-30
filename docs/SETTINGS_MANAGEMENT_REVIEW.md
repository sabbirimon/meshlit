# Settings and management source review
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-31: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Reviewed 2026-10-31 for the desktop evolution work. This is a bounded source and
contract review, not a claim that every setting was exercised on every platform.
Build, installed-app, provider, native hardware and paired-device evidence remain
separate. See [the implementation plan](DESKTOP_EVOLUTION_PLAN.md),
[user manual](USER_MANUAL.md) and the current PROGRESS record for validation.

## Authority and persistence map

| Surface | Controller and storage | Actual authority / boundary |
| --- | --- | --- |
| Code OSS product settings | `extension/settings.cjs`, existing command handlers | Product setup and navigation; editor preferences remain separate. A destination is not evidence that its optional service is installed. |
| Code OSS conversations | `extension.cjs`, `agent_host.py`, `IdeBackend.kt` | Independent transient sessions, selected model, explicit tools, Stop and the existing usage ledger. Switching an established route/mode creates a new conversation. |
| Code OSS project memory | `memory.cjs`, `memory-store.cjs` | Manual project notes, bounded lexical recall and metadata receipts. AES-GCM files use SecretStorage keys. Local recall, API recall and history all start off. No automatic transcript capture. |
| Compose desktop preferences | `Preferences.kt` / Java Preferences | Appearance, language, scale and advanced navigation persist. Conversation, response policy and session network consent are separate. |
| Desktop engine | `EnginePreferences`, `LocalEngine`, `IdeEngineSession` | Saved native CPU options apply on a new model load. Workbench now reads the same saved options; it has no dedicated engine-tuning panel yet. A running model is not silently reconfigured. |
| Desktop resource directory | `DesktopResources.kt` | Explicit development resources override packaged resources consistently. HyperL verification must agree with the JVM native-library search location. |
| Compose knowledge library | `DesktopKnowledge`, `KnowledgeCorpus`, `KnowledgePanel` | Separate passphrase vault; local/remote sharing and agent sharing are independent grants. Locking stops subsequent retrieval. Previously sent content cannot be retracted. |
| Desktop provider connections | `OnlineConnections`, `DesktopCredentials`, `HostClient` | Non-secret addresses persist separately from purpose/endpoint-bound credentials and session permission. A model catalog is not account or billing proof. |
| Desktop device addresses | `NodeStore`, `NodesPanel`, `DeviceDashboardPanel` | Saving an address is not worker enrollment, identity authentication or proof of current availability. New pairing regression investigation was not completed in this review. |
| Desktop human/agent actions | `StudioAgent`, workbench reviewed dispatcher, App Access policy | File edits, native app control and MCP calls have different scopes. User approval is not proof that an upstream tool completed. Root/admin, credential and trust changes remain independently controlled. |
| HyperL | `DesktopHyperL`, shared `NativeCpu` and admission contracts | Verified numerical CPU/JNI execution; independent of chat inference, model sharding, GPU/NPU support and provider permissions. Separate HyperL terms remain intact. |
| Android settings | `ModernSettingsScreen`, `SettingsDestinations`, `SettingsRepository` | Shared modern navigation for both flavors. Persistence, runtime grants, service readiness and optional companion setup are distinct. Grouping must retain real routes and build-profile restrictions. |
| Android script hooks | `HookEngine`, `ConfigScriptRunner`, persisted hook registry/master switch | Local bounded DSL only. Caller-owned cancellation, per-run results, live grant checks and explicit unavailable remote dispatch. Hooks do not acquire shell/peer authority from their names. |
| Android manual scripts | `ScriptsScreen`, in-memory `ScriptLibrary` | Runs now belong to the screen coroutine. The library remains in-memory; remote upload/dispatch is not implemented. Upload is visibly disabled as unavailable. |
| Optional upstream applications | Studio Services/MCP adapters | Independently installed claude-mem, OpenMausBot and OpenBot retain their own setup, auth and capabilities. A health response or dashboard link is not full integration proof. |

## Concrete defects corrected in this change

1. **Compose knowledge permission bypass.** Agent modes used the ordinary chat
   retrieval call, omitting the corpus's independent agent gate. The wrapper now
   forwards the agent flag; the UI exposes a separate opt-in; each model round
   retrieves current permitted context. Default denial and subsequent revocation
   have focused regressions.
2. **Stop during asynchronous workbench preparation.** Memory lookup, receipt
   persistence and file revalidation introduced dispatch gaps. Active-session and
   trust checks now prevent a late send, MCP approval or editor mutation after
   Stop/revocation. History cannot extend an approval's lifetime.
3. **Memory budget mismatch.** The caller could supply a character limit larger
   than the memory API accepted, blocking ordinary prompts. Limits now agree and
   preserve whole bounded excerpts. Character counts are not labelled tokens.
4. **Desktop engine settings divergence.** Workbench loaded native defaults while
   Compose saved tunable options. New workbench loads now read the same validated
   preferences; active sessions remain stable. Output limits respect small local
   context capacity.
5. **HyperL resource mismatch.** The verifier and LLM resource lookup used
   different override rules; native search could select a different file from
   the verified one. A shared directory resolver, matching launcher property and
   explicit search-location check address this without changing Android's ABI.
6. **Hook timeout and completion isolation.** Waiting on an independently launched
   Job did not cancel actual work, and the global last event could belong to a
   different script. Hooks now await their own structured execution result.
   Cancellation propagates, missing completion cannot become success, revocation
   stops pending/running work, and the startup grant mirror begins disabled.
7. **False remote script success.** Peer targets previously emitted StepOk without
   dispatch. They now fail as unavailable; fan-out rejects unsupported remote
   participation before its local portion. Script depth, step count, variables,
   concurrency and deadlines are bounded. Wait conditions are evaluated rather
   than ignored.

These entries describe source changes and associated regression cases. Consult
current test/build evidence before treating them as acceptance-passed.

## Remaining consolidation and verification work

- Compose Studio and Code OSS are separate desktop surfaces. Their credential
  stores, knowledge/project-memory vaults and agent policies are not interchangeable.
  Migration must be explicit; silently copying keys, grants or conversations is
  inappropriate. A common typed capability/settings catalog is a useful future
  boundary; presentation aliases must not become duplicate runtime controllers.
- Many Compose destinations intentionally share a controller: health/memory/
  monitoring/tasks use ResourceMonitor; terminal/commands use TerminalPanel;
  behavior/guardrails use response policy. Group these as task-oriented entry
  points and explain their scope instead of creating independent toggles.
- A desktop entry marked unavailable must stay visibly unavailable until a real
  controller and its tests exist. Docker/VM, remote script dispatch, phone worker
  enrollment and distributed inference must not be inferred from new service links.
- ScriptLibrary still stores manual scripts in memory. ScriptsScreen's upload
  action is disabled and labelled unavailable; no remote protocol was invented.
- Hardware/OS grants, native stop behavior, account-specific providers, connector
  authentication and physical pairing/revocation need their own runtime evidence.
  No blanket “all settings work” claim follows from route/source inspection.

## Stale architecture references

`docs/architecture/current-state.md` contains a dated 2026-10-07 baseline and older
inventory/model assertions. Current source pins Android's starter to **Qwen2.5
0.5B Instruct Q4_K_M (491,400,032 bytes)** and desktop's starter to **Qwen2.5 1.5B
Instruct Q4_K_M (1,117,320,736 bytes)** in their respective manifests. Its SmolLM2
text and old module/feature counts must not be used as current build facts.
Use `settings.gradle.kts`, `FEATURE_MAP.md`, the manifests and current validation
instead. Older build-guide references to hardcoded build40.1 are also historical;
the packager has version/build arguments. Preserve dated evidence while linking
readers to current sources.

## Review coverage

Reviewed: desktop routing/preferences, knowledge dispatch and persistence, engine
configuration, HyperL resource/load boundary, workbench conversation/tool dispatch,
new project-memory contracts, runtime packaging, and Android hooks/script execution.
The mobile reviewer separately inspected navigation and selected persistence
paths. This document does not assert a full line-by-line audit, physical peer
qualification, all-provider access or a clean-machine release certification.

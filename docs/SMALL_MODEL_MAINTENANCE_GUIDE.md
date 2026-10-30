# Fix and verify Meshlit with a smaller coding model
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Use this guide when continuing development in another chat or with an older/smaller
LLM. It also explains the limits of using smaller models inside Meshlit. Do one
bounded task at a time; do not ask a small model to rewrite the entire application.

## First context to provide

Give the model `AGENTS.md`, `docs/CHECKPOINT_2026-10-31.md`, the relevant row below,
and only the affected source/test files. Use the current source as authority;
older architecture documents contain dated facts. Do not paste credentials,
complete logs containing private conversations, model weights or whole repositories.

The current work includes unqualified build/device/security gates. A passing unit
test does not prove real voice, GPU support, phone pairing or an upstream service.
Never replace an unavailable backend with a simulated successful result.

## Where to look, why, how to fix, how to verify

| Problem | Where to look | Why this boundary matters | Safe fix approach | Required verification |
| --- | --- | --- | --- | --- |
| A setting is missing or hard to find | `app/.../ui/modern/SettingsDestinations.kt`, `ModernSettingsScreen.kt`; `companions/studio-runtime/extension/settings.cjs` | Catalog, route and controller must agree | Preserve route IDs and build filters. Group existing controls; wire a real destination. Keep editor preferences separate. | `SettingsDestinationsTest`, settings Node tests; open/search destination in the installed app. |
| A setting appears saved but has no effect | `app/.../settings/SettingsRepository.kt`, target controller, `AndroidControl.kt`, `VoicePreferences.kt`; `docs/SETTINGS_MANAGEMENT_REVIEW.md` | UI state alone is not persisted/backend state | Trace UI→writer→storage→flow→consumer→dispatch. Check write success. Show failure. Reopen and restart. Do not silently reset corrupt data. | Existing persistence tests plus a changed-behavior regression; saved state survives reopen, Cancel preserves old state, failed writes report failure. |
| Chat tools cannot turn on | `app/.../ui/modern/ChatOptionsDialog.kt`, `chat/ChatToolSelection.kt`, `chat/ChatController.kt`; workbench `extension.cjs`/`connections.cjs` | Selected route/mode, tool-count limit and saved grants are separate | Explain unsupported routes; offer an explicit supported route. Reject only over-limit additions; always allow disabling. Save atomically. Do not grant permissions automatically. | `ChatToolSelectionTest`, `ChatToolsSettingsDeviceTest`, `chat-session.test.cjs`; exercise enabled, disabled, limit, Cancel and reopen. |
| Android live voice starts but hears nothing | `chat/SpeechAdapters.kt`, `VoiceTurnControl.kt`, `HandsFreeVoice.kt`, `ui/modern/HandsFreeVoiceDialog.kt` | Microphone PCM, speech detection, STT, model reply and TTS are separate stages | Check permission/client-silencing and measured input level. Inspect detector threshold/silence bounds. Preserve manual Send, bounded retry and visible errors. Do not treat silence as success or loop forever. | `VoiceTurnControlTest`, microphone device test, then `LiveVoiceConversationDeviceTest` with an actual owner utterance. A WAV fixture/playback-only pass is insufficient. |
| Speech is recognized but no reply is spoken | `HandsFreeVoice.kt`, `ChatController.kt`, `SpeechAdapters.kt`, `core-inference/.../OfflineSpeechRuntime.kt` | The chat model or output engine may be unavailable independently | Check route readiness, model load, transcript, generation error, selected output, host availability and cleanup. Preserve cancellation and explicit online permission. | One real transcript→local reply→audible output; Stop/reopen and missing-engine failures. Never use paid credentials to make a test pass without authorization. |
| Hook keeps running after Stop or timeout | `app/.../agent/hooks/HookEngine.kt`, `scripts/ConfigScriptRunner.kt`, `operations/StopCoordinator.kt` | The caller must own the actual job, not just a join/waiter | Keep structured cancellation and per-run results. Recheck enablement. Attempt independent revocations even if one save fails. Unsupported remote dispatch must fail visibly. | `HookEngineTest`; timeout, cancellation, master toggle off, concurrency isolation and unsupported target. Do not run saved owner scripts for a test. |
| An agent reads memory without consent | Workbench `memory-store.cjs`, `memory.cjs`, `extension.cjs`; Compose `DesktopKnowledge.kt`, `Main.kt`; shared `KnowledgeCorpus.kt` | Local, remote and agent permissions are independent | Recheck at dispatch/each round. Keep project boundaries and encrypted storage. Handle key loss without resetting data. Never grant authority through recalled text. | Memory Node tests, `DesktopKnowledgeTest`; default deny, cross-project, Stop/revoke race, corruption and forget. |
| Model tuning differs between desktop surfaces | `desktopApp/.../IdeBackend.kt`, `IdeEngineSession.kt`, `EngineSettings.kt`, `LocalEngine.kt` | Saved preferences and a running engine are different states | Use one validated saved preference authority when starting a new engine. Do not silently change a running session. Respect context and output limits. | Desktop engine/options tests; reload and inspect effective configuration; real local generation separately. |
| HyperL verifies one file but loads another | `desktopApp/.../DesktopResources.kt`, `DesktopHyperL.kt`, `LocalEngine.kt`; runtime resolvers | Resource overrides and native search paths must identify the same verified binary | Use the shared resolver and reject mismatch. Preserve model/native hashes and HyperL licensing. Do not advertise missing GPU/NPU targets. | `DesktopResourcesTest`, `core-hyperl` tests, then packaged `--hyperl-check` on named hardware. |
| Companion does not connect | `companions/agents/prepare.py`, source lock, `extension/companion-client.cjs`, `companions.cjs`, `connections.cjs`, `agent_host.py` | Worker/UI/MCP/AG-UI endpoints are different | Verify pinned source and prerequisites, typed health, correct port and endpoint-bound credentials. Keep loopback restriction. OpenBot AG-UI is not MCP. | Companion/auth Node and Python tests; actual local health/UI and supported tool call. Record missing Docker/account/runtime separately. |
| Pairing or remote management fails | `core-peer-security`, `core-federation`, desktop `HostClient.kt`/`NodeStore.kt`, Android device/controller files | Discovery does not establish trust or compute capability | Trace invitation→identity/pin→approval→scope→live offer→dispatch. Never disable certificate checking or auto-approve a new identity. | Existing security/transport tests, then two actual devices: pair, reject wrong identity, revoke, disconnect, inspect Stop. Forwarded chat is not layer sharding. |
| An upstream advisory is reported | `docs/UPSTREAM_SECURITY_REVIEW.md`, pinned upstream manifest and external audit evidence | A dependency match is not proof of exploitability or remediation | Identify the production path and exact fixed version from primary advisories. Upgrade in an isolated source checkout; refresh pin only after build and contract checks. | Re-scan the new lockfile, test affected integration and negative cases. Never mark zero-day-free or silently clear a finding. |

Here `app/...` means `app/src/main/kotlin/com/meshlit/`; desktop source lives under
`desktopApp/src/main/kotlin/com/meshlit/desktop/`. Find exact files with `rg --files`
before editing—do not invent a path or method if the guide and current source differ.

## A repeatable repair procedure

1. Write the observed symptom and expected behavior in one sentence. Reproduce it
   using an isolated profile or non-destructive test.
2. Read the relevant controller, persisted data shape, UI caller and existing tests.
   Draw the short data path: control→save→state→consumer→dispatch→result.
3. Identify the failing link with evidence. Do not assume the visible screen owns
   the backend or that changing a label repairs execution.
4. Make the smallest change preserving existing grants, data and public interfaces.
   Add a regression for the failure contract, not a test copying the implementation.
5. Run targeted tests, then compile affected callers. Run full build/lint gates
   before distributing an app. Verify physically when the behavior uses hardware.
6. Report changed files, exact command/result, limitations and remaining gates.
   Update the checkpoint and docs. Never claim a check ran because its command exists.

## Commands to use from this checkout

Use the configured JDK21 and Android SDK. Do not change `$HOME` or erase caches.
Read `BUILD.md` and the checkpoint for actual environment paths.

```sh
git status --short
git diff --check
node --test companions/studio-runtime/extension/*.test.cjs
python3 scripts/prepare-bundled-model.py
./gradlew --no-parallel --max-workers=2 :desktopApp:test
./gradlew --no-parallel --max-workers=2 :app:testMeshlitV1DebugUnitTest :app:testMeshlitV2DebugUnitTest
./gradlew --no-parallel --max-workers=2 :app:assembleMeshlitV1Debug :app:assembleMeshlitV2Debug
./gradlew --no-parallel --max-workers=2 :app:lintMeshlitV1Debug :app:lintMeshlitV2Debug
python3 scripts/update-doc-tracking.py --write
python3 scripts/update-doc-tracking.py --check
python3 scripts/validate-feature-map.py
```

Run only relevant tests while iterating; full gates belong before artifact delivery.
Python SDK tests need the pinned SDK environment. Local-server tests need loopback
permission. Physical device tests need the named connected device and real grants.
No source unit suite substitutes for these prerequisites.

## Pasteable instruction for another coding model

> Continue Meshlit from docs/CHECKPOINT_2026-10-31.md. Read AGENTS.md and the relevant
> row of docs/SMALL_MODEL_MAINTENANCE_GUIDE.md. Work on this one defect: [symptom].
> Expected behavior: [result]. First locate the real UI, storage and controller;
> explain the failing boundary briefly. Preserve existing changes, permissions,
> credentials and licenses. Implement a small fix with a meaningful regression.
> Run the relevant commands and report actual results. If required context or
> hardware is unavailable, record the exact missing evidence; do not fabricate
> APIs, success, token counts or devices. Do not push, release, install dependencies
> or enable remote/paid services unless this task explicitly authorizes it.

Give one model one owned file group. If several models work separately, use branches
or worktrees and specify file ownership; review and test their combined changes.
Save a short handoff before changing models instead of replaying the entire chat.

## Smaller models used inside Meshlit

- Use a compatible, verified model format and a context budget the installed runtime
  actually supports. A model family name alone does not establish compatibility.
- Start with short plain chat. Test structured output before allowing tools. Keep
  tool groups few and tasks bounded; invalid tool JSON must dispatch no action.
- Voice uses separate recognition and synthesis engines. A smaller chat model does
  not repair microphone capture or make an unsupported speech backend available.
- Give compact, relevant notes through explicit memory permissions. Avoid giant
  repeated transcripts. Character limits are not measured model token usage.
- Compare the same fixture tasks and actual latency/errors before changing defaults.
  If the small model fails a capability, show that limit or choose another explicitly
  selected model. Never silently send the task to a paid provider.

## Android-specific handoff checklist

V1 and V2 share the modern Kotlin/Compose UI. Repair the shared controller and
settings authority rather than creating different fixes in legacy screens.

1. State the flavor/build, Android version, device model, speech input/output and
   selected chat route. Keep owner data; never clear app storage to hide a bug.
2. For voice, check RECORD_AUDIO and actual input levels first; for cross-app tools,
   check both saved delegation gates, OS Accessibility connection and app scope.
   Microphone access and automation authority are unrelated permissions.
3. Run both flavor unit suites and compile both flavors. Prepare the pinned bundled
   model before assembly. Keep model/native binaries outside commits.
4. Build `:app:assembleMeshlitV2DebugAndroidTest`. Read each test before running it.
   `SettingsNavigationBuild41Test` checks navigation;
   `ChatToolsSettingsDeviceTest` checks Save/Cancel/limits and restores preferences.
5. `VoiceMicrophoneDeviceTest` checks capture/cleanup and measured levels;
   `LiveVoiceConversationDeviceTest` needs an owner speaking a real utterance.
   It leaves an actual transcript/reply in the current local chat. Coordinate that
   interaction; do not claim success from a prerecorded input or empty capture.
6. Run both app lints before distributing APKs. Record actual APK SHA-256, build
   source, device/API and test result. V1 build success does not prove V1 was tested
   on a phone. Small local models, host speech and online providers have separate
   prerequisites and no automatic paid fallback.

Pasteable mobile task: “Fix [Android symptom] in Meshlit's shared modern UI and real
controller. Use the Android row/checklist in this guide, preserve both flavors and
owner data, add a failure-contract regression, compile/test both flavors, and name
which physical test remains. Do not modify unrelated native engine or peer trust
code, invent Android support for a desktop service, or report unrun device checks.”

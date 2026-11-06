# Agent and provider checkpoint — 2026-10-31
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-06: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

## Meshlit continuation — 2026-11-05

1. **Managed devices and recovery.** In **Distributed layers**, optionally enable
   **Use this coordinator's CPU for model layers** to admit the coordinator and
   one approved worker. Without it, the existing two-worker minimum applies.
   Placement uses fresh approved capacity and exact nonoverlapping layer ranges.
   In **Replicated recovery**, review and publish up to four completed local
   text turns (8 KiB max) to the configured signed three- or five-replica group.
   Recovery requires a current quorum, a new ownership epoch and the same GGUF
   bytes. It opens an idle Chat with tools off. It does not restore drafts,
   attachments, model context caches, tool outcomes, or a running generation.
2. **Hosted search and bots.** In **Online providers**, review the exact OpenAI
   or compatible model and endpoint for Agent web search and allow agent use.
   Then grant that review to one conversation and select **Agent**. The model
   may propose one built-in public web search per turn. Meshlit reviews the
   exact query with the owner and sends bounded results to the same endpoint.
   Local phone/files/knowledge tools are excluded. Both requests count toward
   usage. The protocol uses the
   [OpenAI function calling contract](https://developers.openai.com/api/docs/guides/function-calling).
   In **Bots and connections**, configure and review an
   [AstrBot scoped chat API](https://docs.astrbot.app/en/dev/openapi.html) request
   or a [Grok Bot routine trigger](https://docs.x.ai/grok-bot/skills-routines-and-automations).
   AstrBot replies in a private session; the Grok trigger acknowledges that the
   routine started. The xAI model connection is separate. Existing
   [OpenClaw gateway](https://docs.openclaw.ai/gateway/openai-http-api) controls
   are linked. These are connections to owner-configured services. Meshlit does
   not run their messaging channels or install or execute bot-generated skills.
   In **Agent management**, create or edit an instruction profile with an
   approved local or hosted model and select it in Chat. The profile itself
   confers no tool, device, or credential access.
3. **Voice and vision.** In voice options, enable **Review transcript before
   sending** to edit, send or discard recognized text. Stop and timeout discard
   a pending turn. In **Camera, vision and audio**, import a matching vision GGUF
   and projector. The photo composer defaults to **On-device photo** on builds
   with the native runner. It checks RAM, uses an isolated loopback process,
   and keeps the question and result out of subsequent model context. A hosted
   vision route is available only when selected explicitly. The original image
   is not retained in Chat history.
4. **Studio workspace.** Open **Workspace changes** from a chat or the Meshlit
   navigation. **Last turn** shows only applied edits from that chat, including
   unsaved editor changes; **Working tree** compares current files against Git
   HEAD. Use the filter and click a row for the native editor diff. Right-click
   a row, Explorer file, or workspace reference in Chat to open, review, save a
   copy, copy a path or text, or reveal the file. File links resolve in the
   trusted workspace. Last-turn snapshots live in the current workbench
   session. See the [native menu contract](https://code.visualstudio.com/api/extension-guides/webview#_context-menus).

The managed recovery checkpoint passed both Android debug flavors, shared
inference and federation tests, desktop engine tests, V2 instrumentation APK
assembly and both lints. Hosted tools and bot protocol tests use a real local
HTTP socket with controlled responses. All 112 Studio extension tests passed.
An isolated native Code OSS 1.143.1 test loaded the updated extension, opened
Workspace changes, opened a file and verified a real Git diff against HEAD.
A native right-click on the changed row showed all seven file actions. Selecting
Review file changes from that menu opened the working-tree diff. Physical
multi-device recovery, phone voice/vision and live bot/provider acceptance remain
separate gates.

## Explicit Android agent mode continuation — 2026-11-04

Source now replaces English-keyword activation with a saved per-conversation
Chat / Agent choice in settings and the composer shield menu. Old chats keep
their tool selections and default to Chat. Chat cannot execute selected chat
tools, including offered device connections. Agent plans every nonblank message;
short/non-English requests reach the planner, without claiming multilingual task
success. Removing the last tool group returns to Chat. Provider/route/Colibri
selection resets the mode to Chat. Existing operation, OS and exact-call approval
gates remain; broader approval choices still reset to Ask on restart.

The managed recovery checkpoint subsequently passed Android unit tests, debug
builds and lint. Physical mode switching, tool dispatch/review and multilingual
task quality remain open.


Owner requested a quick checkpoint at 2% remaining weekly allowance, with no extra
credits. This is a source checkpoint, not a claim of Codex-equivalent capability.

## Changes

- Main Settings → Agent management: device master pause, model-task, Internet,
  local-content and phone-app controls; operation revocation cancels managed agent
  work. Individual grants and emergency stop remain independent.
- Six disabled-by-default saved task profiles; chat task presets change only the
  current draft. Save applies them; Cancel preserves it. Main grants are unchanged.
- Node tools actually occupy ten slots, not eight. Legacy over-capacity selections
  must remove groups. Planner model templates, strict JSON wrapper handling and
  exact selected-tool binding fix concrete failures without replacing the stack.
- Actual tool activity and saved clickable source cards distinguish search hits
  from fetched pages. No background favicon calls or invented source receipts.
- Android Online providers: explicit OmniRoute host dashboard, searchable real
  model catalog, cancellation of stale requests, encrypted saved-key status/removal.
  The host must exist; Android does not install OmniRoute. API base and dashboard
  address are separate; a desktop loopback address is not a phone address.

## Where, why, how and verification

| Symptom | Inspect | Fix/verify |
| --- | --- | --- |
| Tools will not enable | ChatToolSelection, ChatOptions, OperationGate | Check ten-slot total, supported on-device planner and separate saved grants; never silently grant from chat |
| Weird JSON or no action | LocalChatTools, LocalToolPlanningPrompt, LocalToolPlanParser | Preserve known model template, exact one action object and unselected-tool rejection; run core-mcp/app tests |
| No web icon or links | ChatSources, ChatController, modern ChatSources UI | Trigger real successful tools; verify search-vs-page labels, safe links, failure/Stop cleanup and reopen persistence |
| Agent off but UI offers Run | AgentAccessPanel, PowerDevicesScreens | Observe master policy; cancel active managed agent jobs; preserve human chat |
| Wrong provider model list | OnlineProvidersScreen | Refresh actual endpoint with bound key; editing endpoint/protocol/key/vault variable must cancel/drop prior results |
| OmniRoute only shows key form | GatewayDashboardEndpoint/Screen | Configure real host dashboard separately, open it, then select a returned model in API profile |

## Limits and next work

The October 31 checkpoint used an English keyword heuristic; the November 4
continuation above replaces it with explicit Agent mode. The chat tool planner
remains **on-device only**, with three calls or eight for phone tools. Ordinary cluster/hosted chat is
separate. A tiny starter model is not qualified for reliable agent planning.
Next: qualify explicit mode switching on a phone, add reviewed
hosted native tool-call adapters, evaluate capable models on task completion,
verify actions before final answers, and qualify interruption/recovery on phone.
Do not describe presets as independent autonomous teams or assume a role grants
permissions. Legacy agent/browser screens need separate master-pause review.

## Free-provider setup

The owner signed into OpenRouter with their own account and created a test key.
Temporary-email setup and a host plaintext key file were rejected by automatic
approval review; neither was completed. Use only the app's encrypted credential
store; never commit or print keys. `openrouter/free` returned HTTP success and
zero reported cost, but the first 32-token sample contained reasoning only, so
that sample is not a usable-answer success. A subsequent real request to `cohere/north-mini-code:free` returned `MESHLIT_OK` and zero provider-reported cost. Phone storage/execution is the next gate.
`OwnerProviderSetupDeviceTest` runs only with explicit owner setup arguments,
saves through OnlineProviders, and sends one synthetic test prompt. Never put
real chat history into provider qualification. Do not pass secrets in saved scripts.

## Validation and resume

The initial access/preset batch passed core-common and both app unit suites plus
V2 instrumentation assembly. All 786 unit tests passed (31 core-common, 155 core-mcp, 300 per Android flavor). Both debug APKs assembled. Full lint is still running in
`/tmp/meshlit-agent-checkpoint-final.log`; record their outcome before claiming
success. The explicit credential-setup test APK compiled and installed on Samsung. Credential transfer is pending: the system clipboard changed after the successful API test; the owner has been asked to copy the key again. No provider credential was written to the phone by the failed transfer attempt. The production APK update awaits lint completion.

```sh
python3 scripts/prepare-bundled-model.py
./gradlew --no-parallel --max-workers=2 :core-common:testDebugUnitTest :core-mcp:testDebugUnitTest :app:testMeshlitV1DebugUnitTest :app:testMeshlitV2DebugUnitTest :app:assembleMeshlitV1Debug :app:assembleMeshlitV2Debug :app:assembleMeshlitV2DebugAndroidTest :app:lintMeshlitV1Debug :app:lintMeshlitV2Debug
python3 scripts/update-doc-tracking.py --write
python3 scripts/update-doc-tracking.py --check
python3 scripts/validate-feature-map.py
```

Keep existing voice, pairing, resource-network, portable-build and security gates
open. Current physical device: Samsung SM-A207F. Do not clear its data or publish
APK/model/credential artifacts to source control.

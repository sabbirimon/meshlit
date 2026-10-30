# Agent and provider checkpoint — 2026-10-31
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-31: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

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

The chat tool planner remains **on-device only**, with three calls or eight for
phone tools, and an English keyword heuristic. Ordinary cluster/hosted chat is
separate. A tiny starter model is not qualified for reliable agent planning.
Next: replace keyword gating with an explicit agent-mode decision, add reviewed
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

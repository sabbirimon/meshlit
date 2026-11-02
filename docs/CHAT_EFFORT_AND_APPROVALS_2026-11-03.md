# Chat effort and workspace approvals — 2026-11-03
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-03: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

## Desktop Studio implementation

The Code OSS Studio chat composer now has independent effort and workspace tool
approval selectors. These controls apply to the selected conversation; they do
not change an upstream model's weights, OS permissions or the global device grants.
The earlier Compose desktop dashboards have not acquired these composer controls.

### Reasoning effort

- Auto is the default and omits reasoning/thinking fields, including fields
  supplied by the SDK. The bundled local engine remains Auto-only.
- Low, Medium and High require an exact endpoint/model capability declaration.
  In the effort menu, choose **Configure supported effort levels**, select the
  documented API field and only the levels documented by that model's provider,
  then review the native confirmation. A declaration is configuration, not proof
  that a provider accepted the parameter. Unknown support stays Auto-only.
- Compatible services can use the `reasoning_effort` adapter. OpenRouter has an
  explicit `reasoning.effort` adapter, bound to its host. Generic native Ollama
  `think` controls are not mapped into this adapter.
- A workspace default can be saved for new chats with this exact connection/model.
  Changing destination or model does not inherit another model's capability.
  An idle chat can change effort for its next turn without changing its tools,
  destination or conversation. Model/permission changes use a new visible chat.
- Output limits and usage admission remain separate. The explicit Chat Completions
  effort adapter uses `max_completion_tokens`; OpenRouter retains `max_tokens`.
  Configured reasoning adapters also omit inherited sampling controls and retain
  their compatible token-limit field on Auto, while omitting the effort field.
  A rejected request fails;
  no silent retry with a different effort or provider occurs. Higher effort can
  consume the existing output budget without producing a complete visible answer.

OpenAI documents effort as a reasoning control with model-dependent values,
separate from output-token limits. [Official Chat Completions request schema](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create),
[reasoning guide](https://developers.openai.com/api/docs/guides/reasoning).
OpenRouter documents the unified `reasoning` adapter and per-model differences.
[Official OpenRouter reasoning documentation](https://openrouter.ai/docs/guides/best-practices/reasoning-tokens).
Ollama's native `think` values are model-dependent and use a different API.
[Official Ollama thinking documentation](https://docs.ollama.com/capabilities/thinking).

### Workspace approvals

| Mode | Behavior |
| --- | --- |
| Ask each time | Default. Review each workspace tool request. |
| Auto workspace reads | Automatically list/read bounded text inside the approved workspace; ask before preparing edits. |
| Selected workspace tools | Choose list, read and/or prepare-edit operations; skip only their initial request prompts. |

Broader workspace modes require a native human confirmation. The existing path,
private-file, symbolic-link, file-size, read-receipt and mode/write-count checks
continue to apply. Every edit still opens a diff, requires **Apply**, and remains
unsaved until the human saves it. Stop/trust revocation is rechecked after waits
and before applying a change. Changing approval policy after a chat starts opens
a new chat; it does not expand the previous agent's authority.

MCP tools and desktop app access keep their separate per-call approvals in all
three modes. MCP tools have arbitrary semantics; connector selection alone does
not establish that they cannot modify credentials or policies. These modes do
not claim unrestricted filesystem, shell, credential, root or policy access.

## Android implementation

Chat's composer shows an independent **Auto** effort menu and a shield access
menu. Conversation settings contains the same controls. Auto is available on
every runner; Low, Medium and High require model-specific API support. Bundled
RunAnywhere, native local models, Colibri and mixed local/API routes remain
Auto-only. There is no prompt pretending to change a runner's internal effort.

In **Settings → Online providers**, edit a compatible profile, enter the exact
endpoint and model, then choose **Configure documented effort support**. Review
the API field and its supported levels, confirm, and save the profile. Changing
the endpoint or model invalidates the old declaration. OpenRouter's nested
adapter is restricted to its official host. Native Gemini, Anthropic and Ollama
thinking budgets are not mapped to these adapters. API-only model routes expose
only levels supported by every participating model; automatic route selection
uses the conservative intersection of all enabled routes.

Effort is saved with the conversation. Requests recheck current profile support
before reserving usage and before dispatch. Unsupported requests fail rather
than silently changing effort. Configured compatible profiles permit an explicit
output ceiling up to 16,384 tokens; routes retain their existing 1,024-token
ceiling. Reasoning may consume the output budget, so High does not guarantee a
complete answer or improved correctness.

| Android mode | Behavior |
| --- | --- |
| Ask each time | Default. Show the exact selected tool and JSON arguments before dispatch. |
| Auto approved reads | Skip the chat prompt for first-party classified reads and already approved bounded contact checks. Other calls ask. |
| Selected approved tools | After human confirmation, selected built-in tools use existing grants without another chat prompt. |

Android approval choices are per conversation and **session-only**. App restart
restores Ask each time; serialization deliberately excludes the broader choice.
An approval applies to one pending request, expires after 60 seconds, and cannot
authorize a later request. Deny, timeout, Stop and cancellation do not dispatch
the call. Existing automation, source, account, device and OS permissions are
rechecked after the decision. Credentials, policy edits and root remain
human-only. Chat tools still require the on-device model route; selecting an
external model does not grant it phone tools.

### Android acceptance scope

Both Android app unit suites passed 341 tests with zero failures. Unit checks cover exact endpoint/model binding, stale declarations, route-level
intersection, compatible request fields, larger explicit output budgets,
session-only approval reset, unknown/imported tool classification, expiry,
replayed decisions and Stop. The HTTP request test uses an isolated loopback
server. It proves request construction, not live hosted-provider acceptance.
Physical Samsung checks and final build results are recorded separately in
`desktop/evidence/android-chat-effort-approval-20261103.json`.

## Desktop verification boundaries

Tests cover SDK-host effort propagation across turns, immutable destinations,
local/unknown rejection, request-field construction, exact model binding,
workspace defaults, cancelled setup, real composer event handling, scoped read
approvals, mandatory edit Apply and new-chat policy changes. Captured fixtures
are synthetic; they are not live provider acceptance, performance or native
desktop visual evidence. Current build/test results are recorded separately in
`desktop/evidence/desktop-chat-effort-approval-20261103.json`.

# DeepSeek Harness integration plan and implementation
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-31: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-30. Optional integration into both Meshlit Studio and Android;
source pin `f3803c96974ad4a94195fb97e5c637e9c2a3afe3`, npm `0.2.2-alpha.2`.

## Product boundary

Reuse upstream's plugin architecture, model catalog, conversations, approvals and
web workspace. Keep the existing Meshlit editor and independent chats available.
Do not fork the entire IDE, relabel the Harness UI, or replace offline inference.
This developer-preview runtime can change APIs; updates require explicit pin,
license/dependency review and repeatable qualification before rollout.

## Delivery stages

| Stage | Implementation and acceptance |
| --- | --- |
| H1 · hosted workspace | Implemented: Studio command/rail, Android settings/tools/workspace entry, actual upstream UI hosted in-app, clean-address persistence, explicit host permission notice, separate host login, TLS-or-loopback connection policy; pinned npm installation and private-home launcher. Real host auth/privacy HTTP checks and adapter tests; Android compilation/build/lint tracked in evidence. Physical-phone and native rendered UI qualification remain open. |
| H2 · Meshlit model broker | Planned: add a versioned DeepSeek SDK/ACP adapter behind the existing agent-host interface. Every completion, including compaction/titles/subagents, must pass Meshlit's credential binding, reservation, usage and cancellation path. No external key reaches the SDK. Preserve unknown price/token values; no provider billing-cap promises. Test local, distributed and gateway routes, streaming failures, concurrent chats and Stop. |
| H3 · scoped tools and pairing | Planned: disable default local execution and instruction/plugin discovery in the embedded agent profile; expose only typed Meshlit operations with exact per-session grants. Map requests/approvals/cancellation to enrolled devices; require transport identity, revocation and replay checks. Pairing alone grants no shell, computer control, files or MCP tools. |
| H4 · mobile task controls | Planned: a small native task list, progress, review and Stop client over the paired Studio transport; persistent reconnect cursors and bounded event history. Keep local phone chat working when the host is offline. Native Android Harness execution requires a real supported runtime/ABI and thermal/battery evidence; it is not advertised by H1. |
| H5 · release qualification | Pending: install/update/uninstall and storage accounting, Windows/Linux/Apple Silicon runtime tests, Android WebView responsive/accessibility testing, TLS/private-network testing, signed app packaging and live-provider opt-in qualification. No public release made by H1. |

## Contracts before H2/H3

- `EngineSession`: owner, workspace identity, runtime revision, exact route,
  independent conversation ID, permissions revision and cancellation generation.
- `CompletionReservation`: immutable provider binding and request ID, maximum
  output/context/calls, known estimate or unknown cost, durable reserved/settled
  state; count all auxiliary model calls. Unknown is never interpreted as free.
- `ActionProposal`: typed operation, resolved target, immutable arguments hash,
  origin session, human decision, expiry, and checked again at execution. Treat
  MCP output, repository instructions and page content as untrusted evidence.
- `SessionEvent`: increasing cursor, redacted payload, engine source, timestamp
  and terminal status. Reconnect never automatically repeats a mutation.
- `Stop`: request cancellation, wait for acknowledgement, then report stopped or
  unknown. Closing a viewer is not cancellation and does not revoke cookies.

The above are design contracts, not shipped shared DTOs or existing integrations.
Implement them at the current agent-host boundary, with adapters tested against
the pinned upstream protocol, before showing a DeepSeek engine selector in native
Meshlit chat. The current Harness view uses upstream model/permission controls and
does not inherit Meshlit budgets or emergency stop.

## Evidence and operation

See [setup](../../companions/deepseek-harness/README.md) and
[qualification record](../desktop/evidence/deepseek-harness-20261030.json).
No performance improvement, HyperL backend, distributed tensor execution or free
provider entitlement is inferred from adding an agent harness. HyperL and
meshlit-inference remain independent compute backends with separate licensing and
qualification; an eventual tool/compute adapter must use their public contracts.

# Desktop recovery, self-healing and reset
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-10. User requests desktop/server parity with old and current Android.

## What the Android sources actually implement

`PersonalizationScreen` offers Automatic local model recovery and explains:
“Retries a failed local text request once after reloading the same installed model.”
It explicitly excludes app-code/Android repair, replacement downloads, tool retries
and bypassing Stop or permissions. `ChatController` checks a native error, the saved
recovery policy, original installed model and loaded path before that attempt.

`RecoveryScreen` exposes native CPU KV checkpoints and a separate manual journal
workflow. Their existence does not establish automatic replicated failover.
The current feature catalog still labels AI-assisted repair/source evolution and
signed update/rollback channels **planned-not-implemented**.

Sources: [PersonalizationScreen](../../app/src/main/kotlin/com/meshlit/ui/modern/PersonalizationScreen.kt),
[ChatController](../../app/src/main/kotlin/com/meshlit/chat/ChatController.kt),
[RecoveryScreen](../../app/src/main/kotlin/com/meshlit/ui/modern/RecoveryScreen.kt),
[feature catalog](../feature-map.json), [native checkpoint design](../native-checkpoints.md).

## Desktop implementation and requested port

The desktop currently owns Stop/cancel/unload for its local model process.
It has no implemented automatic retry, native KV checkpoint UI, system repair,
rollback or reset workflow. Do not label Stop as “full auto repair”.

| Mechanism | Required desktop behaviour | Acceptance |
| --- | --- | --- |
| Bounded model recovery | Optional single retry of the same installed verified model, only for eligible native errors; no duplicate successful text | Actual native failure injection, Stop/revocation test and one-retry ceiling |
| Diagnosis | Inspect observed health/logs without changing the system; show source and uncertainty | Real unavailable sensor/runtime/error handling |
| Repair proposal | Explain exact files/services/packages affected, show a diff/plan, bind to target and required privileges | Human review, bounded execution, independent agent grant and rollback test |
| Automated repair | Explicit saved user opt-in for a limited class of reversible actions; separate agent and host authority | Audit, cancellation, idempotency, failure rollback and no silent privilege escalation |
| Native checkpoints | Verified compatible model/engine/context, encrypted bounded snapshots | Save/restore/reject corrupt or incompatible snapshot with a real native engine |
| Cluster recovery | Fenced membership, committed journal, exact worker identity and staged state restoration | Independent-device interruption and recovery; do not auto-replay external commands |
| Selective reset | Separately reset appearance, runtime settings, caches, credentials, trust, model references or chats | Preview exact data, backup/export where supported, explicit confirmation and failure reporting |
| Full application reset | Reset only documented Meshlit-owned state; downloaded/user model deletion is separate | Explicit data-loss confirmation and recovery-build retention |
| Signed update/rollback | Verify publisher/manifest/artifacts before update; preserve settings compatibility | Package signing, rejected tampered artifact and actual rollback on each OS |

A full PC/OS recovery/reset is not implied by “application reset”. System-level
operations require a separately reviewed backend and visible OS authentication.
Track delivery in [desktop features](../DESKTOP_FEATURE_TRACKER.md) and [build log](../DESKTOP_BUILD_LOG.md).

# Existing Android project reuse review — 2026-10-06
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Source reviewed read-only: `/Users/code/AndroidStudioProjects/mllm`.
Both checkouts started at `ff0cd771c12f3daa63cb8fe45e1476245ab7b01b`.
The source has untracked agent instructions, copied deliverables and
other files; its tracked status was clean when checked. Do not copy the
older generated-code directory back recursively or overwrite Opus's work.

## Plans and instructions reviewed

Read PLAN.md, TODO.md, REQUESTS.md, BUGS.md, progress headings, current-state
architecture and its shipped/gap sections, federation architecture, hooks,
bring-your-own MCP server, device compatibility, model classification, and
settings/device/cluster/inference agent notes. Large historical journals are
historical evidence, not a current release acceptance report.

The original BUILD_GUIDE headings identify phone-first participation,
AI participants, heterogeneous hardware, trust tiers, onboarding, settings,
SSH, files, observability and cross-OS adapters. The source orchestration
CLAUDE still forbids layer splitting; the user's explicit authorization
supersedes that rule in this checkout. Leave the other checkout untouched.

## Reuse decisions

| Existing source | Decision in this checkout | Verification boundary |
|---|---|---|
| `devices/QrScanner.kt` | Reused by layer-worker QR enrollment; typed cancellation and unavailable-scanner errors retained | Requires Play Services; manual enrollment remains available |
| `ui/screens/FilesScreen.kt` | Kept; newly reachable from modern Settings → Files and storage | SAF grants remain user-controlled; physical storage operations need device checks |
| `network/termux/TermuxBridge.kt` and TermuxIntegrationScreen | Kept; newly reachable from Advanced settings | Actual probe, permissions, test command and audit; Termux must be installed separately |
| help/HelpHubScreen, UserManualScreen, UiTourScreen, FirstRunSetupRepository | Preserve as foundations for the later guide/tutorial; existing legacy Tools → Help stays available | Rewrite stale screen/model/cluster claims before putting the old tour into modern navigation |
| settings repository/theme/hook engine | Reused existing backend; modern search/navigation exposes appearance and hooks | Existing hooks are local; hook assertions are not universal authorization enforcement |
| network capture, PCAP parser, PCAPdroid integration | Preserve under legacy Tools | HTTP tab is an empty placeholder; capture UI's local running flag does not prove service health. Don't promote it as a complete network dashboard |
| federation client/codecs/peer trust | Preserve alongside separate native layer-worker transport | Independent job forwarding and shard-layout metadata are not proof of dense layer execution |
| old SDK download/mirror path | Do not restore over the new managed library | New GGUF verification, resumable download and content-path registration address the reported failures |

## Claims that must not be copied as facts

PLAN describes a PowerMonitorController/Gauges implementation, but those
referenced files are absent from this source snapshot. The modern monitor
uses real Android sensors; it does not inherit the plan's gauge claim.
DEVICE_COMPATIBILITY lists iPad/iPhone, multi-runtime JIT, training, automatic
failover and large-model performance without corresponding evidence here.
Use measured capability probes, not brand/tier tables, to admit a worker.
The old bring-your-own-server page describes OpenClaw as a generic npm MCP
scaffolder. Use `docs/openclaw-integration.md` and the reviewed upstream
protocol instead; those examples were not verified and must not guide setup.

## Follow-up acceptance

Later tutorial: optional Skip/Resume/Replay; use the real permission and model
operations; simulated cluster examples visibly labeled. No automatic trust
or permission grants. Reuse existing feedback and issue URL code, but export
redacted logs only. Device groups, authenticated browser/API enrollment,
replicated phone journals and failover remain explicit work items in PLAN.

The unsupported bring-your-own MCP setup page in this checkout was replaced
with verified integration paths; its original text is preserved under docs/history.

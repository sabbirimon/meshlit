# Meshlit shared workspace redesign
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Requested 2026-10-08. Covers the Android phone, tablet and wide-window/desktop
mode of both Meshlit flavors. Native Windows/macOS/Linux Meshlit packaging and
the separate HyperL desktop workbench have independent delivery and test gates.

## Design and build plan

1. Compare the owner's older Android Studio checkout at
   `/Users/code/AndroidStudioProjects/mllm` without modifying it. Record every
   primary destination and whether its current backend exists. Preserve working
   tools and explicitly identify old placeholders.
2. Replace chat-history-first navigation with a searchable workspace catalog.
   Monitor, Networking, SSH and Security Lab get direct shortcuts. All connected
   settings and current modern workspaces are reachable; opening a lab never starts
   a VM, grants permissions or runs a security tool.
3. Use the same catalog in phone drawers and a persistent sidebar in wide windows.
   Support an adaptive layout and a focus mode, plus selectable cards/rows.
   Child pages retain their actual state and Back behavior. Avoid stacked headers.
4. Add curated Graphite, Aurora, Ocean, Paper and Coffee appearances on top of the
   existing persisted accent, font, light/dark/system/scheduled and accessibility
   preferences. Keep UI text sans-serif, code monospaced, touch targets at least
   48 dp, restrained outlines, compact headings and consistent card spacing.
   Glass remains an inexpensive tint; constrained devices use solid surfaces.
5. Apply the shared colors/type/layout conventions to chat, menus, settings and
   workspaces. Conversation text has a readable width; code editors can occupy
   available workspace width. Colors must communicate actual runtime state,
   with text labels alongside status colors.
6. Validate real phone navigation, model controls and themes, narrow/large-text
   layout, wide-window sidebar behavior, persistence, both Full builds, lint and
   the narrower Play Review overlay. Retain measured failures and exact APK/report
   hashes. Screenshots are actual rendered builds, not evidence of future backends.

The references inform hierarchy, restraint and interaction rather than copied
brands/assets: [Grok Bot design](https://x.ai/news/designing-grok-bot),
[Claude desktop workspace](https://claude.com/blog/claude-code-desktop-redesign),
and the owner's attached dark, light, card and glass examples.

## Older build navigation parity

Source: old `ui/nav/TopLevelDestination.kt` (17 destinations),
`ui/v2/MeshlitAppV2.kt` (20 entries) and both drawer implementations. These
files establish menu presence, not successful hardware/backend qualification.

| Old destination | Current workspace or restoration target | Actual boundary |
| --- | --- | --- |
| Devices / Devices hub | Networking and device profile | Discovery/pairing and approved capabilities; not automatic worker readiness |
| Jobs | Task manager / Operations | Current task/job controls; the historical Jobs UI is not restored |
| Agent | Chat and Agent management | Existing model conversation and scoped tool/job controls |
| Models | Models | Verified import/download/load; starter and fresh download separately tested |
| Voice | New voice conversation dialog | Separate local/online speech adapters and current chat controller; no historical Voice UI restore |
| Structured | Modern structured output | Current coordinator and JSON syntax checks; constrained/schema decoding not qualified |
| Vision | Photo and camera | Actual imported/captured input; requires a capable model |
| Catalog | Models | Current unified download/import library; historical Catalog UI is not restored |
| Advanced | Searchable settings / workspace groups | Connected destinations remain discoverable independent of Basic/Advanced view |
| Files | Files and storage | Granted-storage operations and archive bounds |
| Sessions | Manual commands / Termux / Linux runtime | Current connected settings; historical terminal screen is not restored |
| Cluster | Monitor / Networking | Actual metrics and approved worker controls; one phone is not cluster proof |
| Network | Networking | Pairing/device networking remains; historical capture screen has not been ported into the new workspace |
| Users | Cloud/accounts and device roles; multi-user administration planned | Old V1 route fell through to `ScreenStub`; no functioning user management is inferred |
| Settings | Settings and Appearance | Persisted preferences and actual platform availability |
| Cloud | Cloud and credentials | Real configured providers/hosts required |
| Help | Guide and tutorial | Existing offline documentation |
| Permissions (V2) | App permissions | Human-owned Android grants and protected administration UI |
| Loader preview (V2) | Existing app bootstrap | A preview is not a backend/service capability |
| Sync / Boost / About cards | Catalog controls / acceleration settings / Guide | Old Boost toggles a preference; it does not prove GPU/NPU dispatch or performance gain |

The newer gateway, SSH, Linux sandbox, lab packages, manual commands, security
lab, HyperL, audit, recovery, router and fine-tuning pages are added to the same
navigation catalog. Each retains its own operational policy and availability.

The old source was used only to inventory menu/features. Its UI and LLM response
mechanism are not restored. The owner subsequently requested removal of the four
bottom tabs. The shared modern shell now uses the searchable sidebar and top
menu, leaving more vertical space for replies and the composer. Selection persists across restarts; device
tests restore the owner chat/model and do not leave their 16-token request policy
selected. Model details in chat and Models separate text capability, catalog tags,
unknown quality and additional image/audio/tool adapter requirements.

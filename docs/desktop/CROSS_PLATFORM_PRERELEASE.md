# Four-platform experimental prerelease
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-31: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Owner-selected scope: Android, macOS, Windows and Linux (2026-10-26).

| Package | Intended capability | Qualification boundary |
| --- | --- | --- |
| Android V2 arm64 APK | Bundled starter model, mobile dashboards and device-local usage vault | Samsung SM-A207F targeted checks; wider device acceptance remains open |
| Intel macOS DMG/PKG | Studio with pinned offline CPU model/runtime and optional host connection | Local packaging and installed-app acceptance must be recorded per artifact |
| Windows MSI | Studio client and optional authenticated host connection | CI build/contracts; no bundled local model/native engine or physical Windows qualification |
| Linux DEB | Studio client and optional authenticated host connection | CI build/contracts; no bundled local model/native engine or physical Linux qualification |

Windows/Linux model inference requires a separately configured compatible host; local runtime actions report unavailable when their runtime is absent. Package success does not establish feature parity. Linux packages target Debian-family distributions; other distributions remain outside this prerelease.

These are experimental packages, not verified store releases. Android debug signatures, macOS ad-hoc signatures and unsigned Windows/Linux packages do not establish a verified publisher. Keep existing installations/data backed up before testing. Do not disable operating-system protection globally. Stable publisher signing/enrollment, notarization where applicable and clean-machine installation acceptance remain release gates.

Budgets are device-local estimates, not provider billing caps. Optional gateways and Tailscale require explicit setup; no paid entitlement or working tailnet is bundled. G1 credential lifecycle and routing coverage remain partial. Automatic paid routing is deferred.

GitHub workflow artifacts include source commit, platform, checksums and qualification scope. Publish only successful artifacts from the intended commit; do not rename an Intel macOS build as Apple Silicon, Windows or Linux.

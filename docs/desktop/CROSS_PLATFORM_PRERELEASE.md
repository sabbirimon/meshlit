# Four-platform experimental prerelease
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-24: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Owner-selected scope: Android, macOS, Windows and Linux (2026-10-26).

| Package | Intended capability | Qualification boundary |
| --- | --- | --- |
| Android V2 arm64 APK | Bundled starter model, mobile dashboards and device-local usage vault | Samsung SM-A207F targeted checks; wider device acceptance remains open |
| Intel macOS DMG/PKG | Studio with pinned offline CPU model/runtime and optional host connection | Local packaging and installed-app acceptance must be recorded per artifact |
| Windows x64 MSI | 2.0.48 inference preview includes a CPU server and Qwen2.5 1.5B | Extracted MSI generated offline in hosted Windows CI |
| Linux x64 DEB | 2.0.48 inference preview includes the same CPU server and model | Extracted DEB generated offline on hosted Ubuntu |
| Apple Silicon DMG | 2.0.48 inference preview includes the same CPU server and model | Mounted DMG generated offline on hosted macOS |

The beta.5 Windows and Linux installers were host clients. The full-inference preview bundles local generation. The portable Full Studio edition adds an offline IDE, integrated terminal and developer JDK. For each edition, match the artifact to the operating system and architecture. [Inference installer run](https://github.com/sabbirimon/meshlit/actions/runs/45188431201) · [Full Studio installation](FULL_STUDIO.md)

These are experimental packages, not verified store releases. Android debug signatures, macOS ad-hoc signatures and unsigned Windows/Linux packages do not establish a verified publisher. Keep existing installations/data backed up before testing. Do not disable operating-system protection globally. Stable publisher signing/enrollment, notarization where applicable and clean-machine installation acceptance remain release gates.

Budgets are device-local estimates, not provider billing caps. Optional gateways and Tailscale require explicit setup; no paid entitlement or working tailnet is bundled. G1 credential lifecycle and routing coverage remain partial. Automatic paid routing is deferred.

GitHub workflow artifacts include source commit, platform, checksums and qualification scope. Publish only successful artifacts from the intended commit; do not rename an Intel macOS build as Apple Silicon, Windows or Linux.

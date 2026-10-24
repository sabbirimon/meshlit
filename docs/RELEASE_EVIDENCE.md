# Release publication and qualification evidence
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: cross-platform tracking; feature and device gates remain separate. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-10. Source review and release assets have separate states.

## Intel Studio build 40.1

Application/native packaging source: 12bd20a; source/evidence checkpoint 630cba2 is included in review commit e7a07c9. Draft PR: https://github.com/sabbirimon/meshlit-v2/pull/2.

Tag: v2.0.0-macos-intel-build40.1. Release ID: 408390281. Current state: **published public prerelease**. All eight names/sizes/uploaded states/GitHub SHA-256 digests match the local files. Release/tag source is c1f2d60ffeff043ef2da210c552ccaf73b65ea8a, with the later documentation and test-fixture correction; Intel application bytes remain from 12bd20a. [Download](https://github.com/sabbirimon/meshlit-v2/releases/tag/v2.0.0-macos-intel-build40.1). Structured proof: desktop/evidence/github-studio-release-check.json. The release target was updated while still a draft; no published tag was rewritten.

Both DMG/PKG are built, verified/extracted and their launcher uses its own portable Java runtime. Actual native generation/authentication rejection/Unload and HyperL CPU pass; PKG sampling/render pass. Both recovered payloads match the signed app, executable bits, starter and sealed native hashes. DMG OS mounting failed, so HFS+ extraction was file-based. App ad-hoc signed; PKG unsigned; no notarization or clean-machine certification. The final assets include PACKAGING.json, VALIDATION.json, RUNTIME_SOURCE.json, matching Java sources and SHA256SUMS. Full records: DESKTOP_BUILD_LOG.md and desktop/evidence.

Current local valid JVM files total 521 executions and crawler 9 cases; both current Full Android assemblies/lints pass. GitHub host contract CI at release checkpoint c1f2d60 passes on Ubuntu, macOS and Windows after the earlier Windows path-fixture correction. Android V1 CI passes; Android V2 is still running at this snapshot. Windows retains two explicit POSIX execution skips; this is contract evidence, not physical Windows or cluster qualification. No fresh phone build/install or physical cluster proof is inferred from this desktop release.

## Historical Android beta.1 publication


Release: [https://github.com/sabbirimon/meshlit-v2/releases/tag/v2.0.0-beta.1](https://github.com/sabbirimon/meshlit-v2/releases/tag/v2.0.0-beta.1)

Source/tag commit: `ad2d3a86f5060ace52c1b46c7a5501253dcc01f7`. Owner: IMON. Published public prerelease
v2.0.0-beta.1; eight assets have GitHub SHA-256 digests matching local files.

Validation: 639 targeted unit tests, one Android audit instrumentation test;
all three app lint reports have zero fatal/errors. See PROGRESS.md and
SESSION_HANDOFF.md for scope, warnings, failed-run history and remaining gates.
Local structured publication proof: `../meshlit-validation/github-beta-publication-results.json`.

Debug-signed experimental artifacts; no Play approval, physical phone oversized
sharding/recovery, hosted Grafana or complete integration coverage is claimed.

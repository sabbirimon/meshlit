# Release gates by platform and channel
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-10. Each release records its source, admitted feature scope, actual checks, failures and unqualified gates. Historical phase/slice numbers and old fixed test counts are not current acceptance criteria. Owner authorization is required for publication; source branch naming is recorded rather than assumed to be dev.

## Common integrity and evidence

- [ ] Name exact source/application commit, review branch, platform/ABI, channel, model and native/runtime revisions.
- [ ] Scope the release to implemented/qualified features; keep Experimental and Core/production candidates separate.
- [ ] Run changed contracts and the required platform build/lint tasks; report existing warnings and skipped/unrun cases accurately.
- [ ] Validate model SHA/size, native dependencies/ABI, embedded licences and matching Java source obligations where applicable.
- [ ] Record current CI head/results separately from local tests; an earlier green run is not proof of the new head.
- [ ] Keep credentials, private logs, models and binaries out of Git; sanitized release assets remain outside the source tree.
- [ ] Update PLAN, PROGRESS, BUILD_MILESTONES, TODO, REQUESTS, BUGS and the relevant feature/build/release ledger.
- [ ] Run scripts/update-doc-tracking.py --check and scripts/validate-feature-map.py.

## Intel Experimental desktop

- [ ] Build pinned baseline SSE4.2 and optional AVX2/FMA/F16C without unsupported ISA assumptions; verify CPU/OS vector state and final signed hashes.
- [ ] Package verified Qwen starter, private portable runtime with audited Java modules, all notices/source provenance and separately licensed HyperL CPU JNI.
- [ ] Reject developer-path native dependencies; validate all native files are Intel. Sign app for actual integrity and report Developer ID/notarization truthfully.
- [ ] Create fresh DMG/PKG; hdiutil verify DMG, recover both payloads and compare file bytes, execute bits and sealed model/native hashes.
- [ ] With JAVA_HOME/JDK_HOME/CLASSPATH unset, run recovered `--local-check` (real native reply/usage, missing-auth rejection and Unload) and `--hyperl-check`.
- [ ] Run actual monitor sampling and native management rendering; inspect the resulting image. Tests of graceful process Stop/interactive SSH are independent cases.
- [ ] Record mount/extraction method and limitations. Same-host payload execution does not establish clean-machine or every supported macOS version.
- [ ] Ship INSTALL, PACKAGING, VALIDATION, RUNTIME_SOURCE, matching Java sources and SHA256SUMS with the installers.

## Android Full/Core Candidate/Play Review

- [ ] Validate pinned real starter and policy/notice copies before packaging.
- [ ] Run both Full flavor assemblies/lints and affected app/core tests; use current flavor-specific tasks from BUILD.md, not obsolete assembleDebug assumptions.
- [ ] Check restricted Core/Review manifest, identity, permissions/services and real native packaging/alignment; no silent debug signing fallback for production.
- [ ] Test actual device Load/Send/Stop/Unload, startup/background/thermal/privacy and revoked grants on the intended artifact. Prior Samsung results keep their original build number.
- [ ] Record actual native token usage or unknown; never count chunks/characters as tokens. Bundle/download paths require complete validated artifacts and actual inference.
- [ ] Physical cluster qualification needs independent workers, actual contribution/allocation, oversized-model memory admission, outputs, latency, worker-loss/Stop and compatible recovery.
- [ ] Store signing, SDK Data safety, private privacy contact, reporting/content requirements, actual 16 KiB runtime and Console acceptance are production/Play gates. No submission is implicit.

## Other OS and advanced operation gates

Windows/Linux/Apple Silicon/NEXT/iOS packages require their own build/runtime/device evidence. NEXT source is not a HAP; a CI JVM contract pass is not an installed platform application. Desktop agents/MCP/A2A, OS firewall/capture, VM/container/Kubernetes, remote clusters, power/cost and full repair require real controllers, permission/revocation/Stop tests and scope-specific native/account/hardware acceptance. Do not mark these passed from a menu or library.

## Publication and cleanup

- [ ] Create a new draft prerelease for an Experimental artifact; do not replace published history or merge merely to publish.
- [ ] Upload all intended assets and verify remote name/size/state/SHA-256 against local files before publishing the draft.
- [ ] Verify public release/tag target and download URLs after publication; record the structured asset proof.
- [ ] Keep a working artifact and primary model/native/JDK/source inputs; remove only known generated failed/duplicate scratch after evidence is saved.
- [ ] Update release/workstream/documentation tracking to the observed state. Do not create a production label from an Experimental release.

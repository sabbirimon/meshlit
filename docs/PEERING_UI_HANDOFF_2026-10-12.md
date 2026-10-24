# Android and desktop peering/UI handoff — 2026-10-12
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Status: **paused at the owner's request**. Resume implementation only when the
owner returns. A future build plan is in [PEERING_UI_BUILD_PLAN.md](PEERING_UI_BUILD_PLAN.md).
No reminder or scheduled automation was created.

## Correct project and source boundaries

- Target: `/path/to/workspace/meshlit-v2-dev` — both Android and desktop.
- Reference: `/Users/code/AndroidStudioProjects/mllm` — another agent owns ongoing work there.
- Earlier repair/test copy: `/path/to/workspace/mllm-repair`.
- Local baseline: `e7a07c9`. At the initial pause, changes were uncommitted and no push or release had occurred.

The initial request was misinterpreted: the first repair was applied to the
Android Studio reference instead of this Documents project. The owner welcomed
those fixes, then clarified the target and requested both Android and desktop.
Do not overwrite either project wholesale or repeat the earlier source/target mix-up.

The other agent's supplied snapshot says it accepted the earlier PeerCrypto,
PairingAuthority, ClusterInvite and ContributionLedger work and removed its
redundant NodeIdAttestation. Its ongoing builds are independent of this checkout.
Do not edit that Android Studio checkout or send it messages without owner authorization.

## Existing local work preserved

Before this continuation, `desktopApp/.../Main.kt` had a `--models-check` change;
`ModelInventory.kt` and `docs/desktop/STABILIZATION_NEXT.md` were untracked.
Those changes remain. The handoff snapshot includes current source files, including
this earlier work, without attributing all of it to this repair.

## Source added or adapted in this checkout

- New `core-peer-security` JVM module: P-256 identity proofs, encrypted recovery,
  signed contribution receipts, invite parsing and shared handshake messages.
  JDK toolchain 21, JVM compatibility 17, consumed by Android trust and desktop.
- `core-trust`: signed/pinned handshake, owner approval/revocable sessions and
  failure-safe trust-store publication. These are separate from native worker TLS.
- Discovery: compact BLE frames, typed transport availability, NSD loss/address
  handling, stable repository flow and restart/transport-toggle fixes.
- Android settings: endpoint insertion now actually inserts; address/key edits
  invalidate trust; active IDs are checked; appearance reset retains unrelated settings.
- Android source wiring: encrypted identity/session stores, rewards receipts,
  NFC controls, relay manager, identity/handshake HTTP endpoints, authenticated
  inference and hosting status. New routes use the existing modern settings shell.
- Android metrics retain nullable native token accounting. Forwarded jobs do not
  earn local receipts; unknown token counts are not replaced with event/character counts.
- Desktop: bare endpoint normalization, bounded typed connection probe, address-book
  v1/v2 loading, saved diagnostic metadata, session-only scoped test keys, new
  workspace rail/header and encrypted identity/receipt wallet source.
- Prepared the existing pinned Android SmolLM2 starter from a local verified artifact:
  105454432 bytes; SHA-256 `2e8040ceae7815abe0dcb3540b9995eaa1fa0d2ca9e797d0a635ae4433c68c2d`.
- Created ignored `local.properties` pointing at the installed Android SDK.

## Validation actually completed

Latest command:

```sh
./gradlew :core-trust:test :core-discovery:test :desktopApp:test \
  --no-configuration-cache --max-workers=2 \
  -Pkotlin.compiler.execution.strategy=in-process
```

- `core-trust`: 12 reported tests, zero failures/errors; aggregate test task completed.
- `core-discovery`: 20 reported tests, zero failures/errors; aggregate test task completed.
- `desktopApp`: stopped during Kotlin compilation at the owner's pause; no completed test report.
- Combined command interrupted, exit 130. This is **not** a green combined build.
- Current Android app compile, app tests, APKs, both lints and desktop rendering
  have **not** been validated after these changes.
- No current physical two-device evidence. Owner selected manual checklist verification.

Log: `/path/to/workspace/repair-tools/documents-foundation-checks.log`.
Earlier attempts failed on uncached Kotlin plugins, missing SDK configuration and
new-module JVM metadata; those setup issues were addressed before the interrupted run.
The 154-test/build result from the earlier Android Studio repair must not be
reported as validation of this Documents project.

## Review first on resume

1. Compile desktop and Android without starting multiple competing Gradle builds.
2. Check `PeerWalletPanel`, `WorkspaceChrome`, Nodes schema/UI and HostClient diagnostics.
   The wallet source has no dedicated completed test or render proof yet.
3. Finish Android nested navigation/back headers and compact/large-font layouts;
   verify injected identity/session/relay services and failed hosting state.
4. Ensure cancelled/error inference completions cannot earn rewards. Preserve
   unknown token usage and all newer native runtime/model admission protections.
5. Review old forwarding registry edits against the target's newer behavior.
6. Desktop HostClient still implements OpenAI/Open WebUI/buffered Meshlit protocols.
   A full signed desktop-to-Android `/v1/infer` pairing/SSE adapter is **pending**.
   Do not describe address saving or the wallet as compute enrollment.
7. Review identity restore against existing scoped credentials and stored public pins.
   No recovery operation may silently regrant roles or replace a different wallet.
8. Review all new controls against the retained first-use legal gate, owner opt-ins,
   Play/core-channel restrictions and strict lint (`abortOnError = true`).

## Boundaries

Local credits are signed self-attestations, not spendable MCC, independent work
verification or payment. Desktop hosting is not connected to the wallet. Personal
chat does not mint hosting rewards. Blockchain consensus/issuance/transfer remains
future work. DeepSeek-OCR is future integration, not shipped compression.

Do not remove native layer sharding, ModelLibrary load locks, provider/agent scope
checks, platform limitations, licence gates or existing advanced destinations.

## GitHub checkpoint requested after pausing

The owner requested a copy on a separate GitHub branch, keeping main unchanged.
Checkpoint branch: `feature/peering-ui-2026-10-12` in
`sabbirimon/meshlit-v2`. This checkpoint preserves incomplete source and the
future build plan; it is not a merge, release or successful desktop/app qualification.
Continue from this branch after the owner resumes work.

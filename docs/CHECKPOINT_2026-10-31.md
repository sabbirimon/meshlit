# Desktop integrations and settings checkpoint
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Saved 2026-10-31 at the owner's request to conserve the remaining ChatGPT allowance.
This is a source checkpoint, not a security-cleared release or a claim that all
requested upstream features and hardware combinations are qualified.

## Resume from here

The requested plan is [Desktop evolution](DESKTOP_EVOLUTION_PLAN.md). Follow the
[detailed manual](USER_MANUAL.md), [Android settings audit](ANDROID_SETTINGS_AUDIT.md),
[settings authority review](SETTINGS_MANAGEMENT_REVIEW.md),
[companion setup](DESKTOP_COMPANIONS.md), and
[upstream security review](UPSTREAM_SECURITY_REVIEW.md).

Priority remains the three requested repository integrations, then settings/routing
and automation validation, the reported Android live-voice failure, stronger device
management, and complete qualified builds. Preserve previous voice functionality.

## Source implemented

- Studio Settings hub with search, colored headings, bordered feature groups and
  separate editor preferences; fewer top-level vendor shortcuts.
- Protected project notes, bounded recall, context preview and optional reviewed
  action history. Local and remote recall are independently off by default.
- Pinned claude-mem/OpenMausBot/OpenBot source preparation, local health/UI handoff,
  supported MCP attachment and protected OpenMausBot pairing credentials.
- Android grouped settings, direct voice access, tool-limit explanations and
  explicit supported-route selection; Save/Cancel and persistence corrections.
- Full-screen live voice with visible controls, input diagnostics, manual speech
  boundary and explicit retry after no speech. Real human conversation still needs
  physical acceptance; previous playback tests do not establish it.
- Corrected desktop agent-memory consent, shared engine preferences and HyperL
  resource verification. Native C++ inference and numerical kernels stay distinct.
- Hook execution owns cancellation, checks live enablement, isolates per-run
  results and reports unsupported remote dispatch honestly.
- Portable workbench packaging code bundles explicit runtime inputs with relative
  paths and integrity checks. A complete installed portable app remains a build gate.

## Validation state

- Desktop extension contract suite: **71 passed**, including recall/Stop races,
  companion/auth contracts, settings command coverage and owned voice lifecycles.
- Earlier core/desktop batch passed: shared-workspace29, desktop-engine12,
  desktop-ssh4, desktopApp75, core-inference298, core-hyperl18, core-gpu36.
  Later desktop/hook/persistence changes require the final checkpoint test batch.
- Both Android APK variants and V2 instrumentation APK assembled from the first
  source snapshot; later fixes supersede those artifacts. The combined lint batch
  hit `lintAnalyzeMeshlitV2DebugUnitTest` failure and did not finish cleanly; it is
  not a passed gate. Final current-source builds/lints must be completed.
- Four webview renders checked search/dispatch and responsive overflow. Rendering
  used isolated local browser pages, not a claim of installed-app qualification.
- Initial portable packaging failed while Swift tried to write its global compiler
  cache. The helper now uses a temporary cache and bounded compiler timeout; final
  packaged application qualification remains pending.
- Detailed source/build/security evidence for optional upstreams is separate.
  Dependency advisory matches are unresolved, not proof of exploitation and not
  grounds to advertise these services as security-cleared.

The final checkpoint test result and exact outstanding commands are recorded below
before the checkpoint is pushed. No new APK is installed as a fully qualified fix
until its current source and physical tests pass.

## Required next gates

1. Complete current-source desktop/app tests and both Android builds/lints. Use
   `--no-parallel --max-workers=2` on this host to keep build resource use bounded.
2. Test `ChatToolsSettingsDeviceTest` and settings navigation on the connected
   Samsung SM-A207F. Preserve owner preferences and existing app data.
3. Run `LiveVoiceConversationDeviceTest` only with the owner ready to speak after
   the Listening phase. It uses real microphone→offline STT→local model→speech;
   fixed audio fixtures cannot close this acceptance gate.
4. Produce a new portable Studio candidate from final sources, verify signatures,
   relocation and real local inference/HyperL. No Developer ID/notarization or
   clean-machine qualification is claimed. Windows/Linux/other devices need their
   own runners and evidence.
5. Triage upstream advisories by reachable production path and reviewed patched
   versions. Keep optional services inactive by default. Do not auto-enable paid
   providers, cloud observers, telemetry, unreviewed hooks or computer authority.
6. Continue the settings/routing inventory and physical pairing/revocation tests.
   A source route map is not evidence that every setting and hardware path works.

## Local working locations

- Checkout: `/Users/code/Documents/Codex/meshlit-unified`.
- Upstream sources/build evidence: `/Users/code/Documents/Codex/meshlit-companion-cache`.
- Portable Python staging: `meshlit-code-oss-cache/portable-python/cpython-3.12.13-macos-x86_64-none`.
- Rendered views: `/Users/code/Documents/Codex/output/meshlit-evolution-20261031`.
- Final test log: `/tmp/meshlit-checkpoint-tests.log`.
- Earlier logs: `/tmp/meshlit-evolution-core.log`, `/tmp/meshlit-evolution-android.log`,
  `/tmp/meshlit-evolution-node-final.log`, `/tmp/meshlit-evolution-package.log`.

Models, caches, upstream checkouts, compiled runtimes and APKs remain outside Git.
No owner credentials were requested in chat. Exact remaining ChatGPT tokens are
unavailable; the owner supplied screenshots showing 17%, then 14% weekly allowance and no
additional credits, which triggered this checkpoint.

## Final checkpoint checks

The final combined batch passed: **538 Android unit tests** (269 per flavor),
**78 desktop tests passed with one physical-phone SSH test skipped**, and V2
instrumentation APK assembly. The two final revocation cleanup edits then compiled
successfully for both Android flavors. All **71 workbench Node tests** passed.
Full current-source APK/lint, installed portable desktop and actual live-human
voice qualification remain open. [Machine evidence](desktop/evidence/checkpoint-20261031.json).

For continuation with a smaller/older coding model, including Android, use the
[where/why/how-to-fix/verify guide](SMALL_MODEL_MAINTENANCE_GUIDE.md).

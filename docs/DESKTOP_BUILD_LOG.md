# Desktop/server build update ledger
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-10. Source implementation, tests, native/package execution and
hardware/production acceptance are separate columns. Append corrections and
actual results; do not report a checklist or successful submission as execution.

[Requested feature tracker](DESKTOP_FEATURE_TRACKER.md) · [Old/new Android inventory](ANDROID_DESKTOP_PARITY.md) ·
[Earlier phased studio plan](DESKTOP_STUDIO_PLAN.md) · [Project evidence](../PROGRESS.md).

## Revision history

| Date / source | Change | Observed evidence | Remaining gates |
| --- | --- | --- | --- |
| 2026-10-09 / `a30081f` and earlier build 40 | Thin Compose host client/CLI, shared visual contracts | Historical host/TCP/offscreen records in PROGRESS; no desktop local engine in that old UI | This is the old screenshot's limited settings surface; no feature parity implied |
| 2026-10-10 / `1c1572f` | Pinned desktop-only Qwen2.5 1.5B GGUF, CPU llama-server lifecycle and Mac packaging source | Full original model size/SHA verified and CPU binary built; source tests recorded earlier | Actual packaged generation/load/unload and successful DMG/PKG payload tests pending |
| 2026-10-10 / `1922916` | Provider presets, scoped endpoint keys, Open WebUI /api contract | Provider contract tests; exact /api and /v1 routes | Actual operator-owned provider instances/billing not verified |
| 2026-10-10 / working tree after `1922916` | Grouped desktop menus, model registry/HF discovery, nodes, monitor/task manager, crypto, JNI HyperL adapter, bidirectional scoped SSH | Portable Temurin build: desktopApp 16 tests, desktop-ssh 4 tests, createDistributable successful before subsequent terminal/policy additions | New app package/native monitor/HyperL/LLM acceptance and all other OS/device checks pending |
| 2026-10-10 / current working tree | Human terminal, Ghostty SSH handoff, local response policies, exhaustive trackers | Terminal real exit/output/cancel/limit and Ghostty pin/argv cases added; latest full rerun pending | Ghostty interactive remote login, embedded PTY and agent bridge pending |

## Failures and corrections retained

- First packaging attempt used `pkgbuild --component`; its bundle analysis rejected
  the supplied app. Packaging source now uses an app-only `--root` payload.
  No successful new PKG/DMG should be claimed until rebuilding and inspecting both.
- Initial offline check assumed `/models` required auth. This llama.cpp build
  exposes model metadata on loopback. Qualification now tests the actual protected
  generation route without its key, then authorised generation and unload.
- Response-policy test exposed a real source bug: shared `ChatTurn` intentionally
  accepts only user/assistant roles. Local system instructions had incorrectly
  been passed through that type. The desktop serializer now receives a separate
  bounded system prompt; shared Android contracts are unchanged. Serializer contracts and real pinned local generation now pass.
- Ghostty 1.3.1 is installed and its version was read. That is runtime detection,
  not a successful SSH session or an embedded-terminal test.
- This documentation comparison uses old menu `98faaf0` (43 destinations), current
  Android (49) and first structured catalog `966015b`. The pre-catalog `ff0cd771`
  baseline has no feature-map JSON; it must not be claimed as a generated catalog.

## Current host evidence

The current desktop classes and application image build successfully with portable
Temurin. Targeted JVM executions: desktopApp 30, desktop-engine 12 (shared GGUF and
placement source), desktop-ssh 4, shared-workspace 4, core-inference 274: **324**,
zero failures/errors/skips on Intel Mac. These are not 324 independent hardware
checks. Core inference lint has zero errors; Android full flavor lints/builds are
running after the shared planner and API-24/voice permission corrections.

- Native Intel HyperL JNI: all twelve bounded CPU recipes plus precise reduction.
- Real OSHI samples 2.1 seconds apart: CPU/RAM and process rows; no stop/energy proof.
- Real HF HTTPS search and pinned file metadata: no new weight download/gated proof.
- Real starter generation, missing generation auth rejection and process unload.
- Three real native cache-key cases f16/q8_0/q4_0 with context 1,024 readback.
  q4_0 produced a poor arithmetic answer; functional text/token success is not
  quality success. It is excluded from normal settings; f16 remains default.
- Current offscreen Compose management render inspected: seven compact categories,
  scoped functions and pending ports distinguished.
- Both native Intel variants built from the same unmodified pinned source.
  Auto dispatch selected AVX2/FMA/F16C on this CPU/OS. A real default-context reply
  reported 82 tokens at 16.047 end-to-end tokens/sec, load 8.788 seconds, missing
  generation authentication rejected and Unload listener closed. This ran while
  Android lint was active; it is not a controlled comparison against the earlier
  baseline. Matched trials, installer payload checks and publication remain pending.

The default system Python crawler run failed for missing FastAPI; the existing
isolated crawler test environment then passed all **9** API/policy cases. No crawler
code changed. Kotlin daemon's sandbox timestamp-path error used the supported
compiler fallback and the desktop build succeeded; subsequent builds select the
in-process Kotlin strategy rather than writing outside the workspace.

## Current acceptance queue

- [x] Current desktop + shared JVM contracts and scoped SSH tests pass.
- [x] Current management components render with compact grouped navigation.
- [x] Actual host pinned offline answer reports native tokens and unload stops listener.
- [x] Actual host HyperL JNI runs all bounded native CPU recipes and precise sums; recovered-package check pending.
- [x] Monitor samples actual host CPU/RAM/processes; no fabricated gauges or costs.
- [ ] HF actual metadata/verified transfer and cancellation acceptance recorded.
- [x] New DMG and PKG created, recovered and payload-tested; native hashes and provenance match.
- [ ] Intel Mac install/open accepted; unsigned/ad-hoc/notarisation status explicit.
- [x] Source/review and release publication URLs/checksums verified on GitHub.
- [ ] Windows, Linux, independent phones, server daemon and other platform proof separately recorded.

## Template for each next build

```text
Date/time and build/channel:
Source commit + dirty-tree status:
Feature IDs changed:
Commands and results (include failures):
Runtime/device/OS/ABI:
Observed outputs, timings and token provenance:
Permission/Stop/revocation checks:
Missing or unqualified capabilities:
Artifact names, SHA-256, signing status and payload checks:
GitHub review/release URL and uploaded digest verification:
```

A production candidate needs its own immutable admitted-feature set and actual
qualification. These desktop changes are Experimental and do not promote the
old Android candidate or another platform to production readiness.

## Validation retry and generated-file cleanup

The first combined Android assembly/lint run stopped progressing in app analysis;
the dedicated Gradle daemons were stopped and checks retried with one worker,
parallel tasks disabled, a 4 GiB build heap and the in-process Kotlin compiler.
A generated 3,321,165,255-byte heap dump and the known failed installer scratch
(application input/image/runtime only, about 1.3 GiB) were removed. Primary
models, source/JDK/native build inputs and actual evidence were retained. Heap
dumps are now ignored and must never be committed or published.

The first Studio package attempt was rejected by the native dependency gate:
the supplied HyperL dylib's own install name was an absolute development path.
The gate was preserved. A reproducible Intel/JNI builder now compiles the same
unmodified CPU/JNI sources with an `@rpath` install name; the new library and
installer payload still require their actual runtime checks.

## Android/shared regression gate — current Studio source

The serial validation completes successfully in **13m34s**: both Full V1/V2 debug
APK assemblies and both flavor lints, core-inference lint, and MCP/sandbox/network/
inference unit tasks. Both app flavors report **0 fatal/errors, 374 warnings**;
core-inference reports **0 fatal/errors, 7 warnings**. Warnings are not erased.
Together with desktop/workspace/engine/SSH suites, current valid JVM result files
contain **521** executions, zero failures/errors/skips on this Mac; shared-source
re-executions/cached unchanged suites are not distinct hardware evidence. Crawler
API/policy checks separately pass 9. No new phone installation or APK release is
claimed. Build daemons were stopped before the matched CPU timing trial.

The relocatable unmodified HyperL library then passes all twelve real native CPU
recipes and precise reduction. See `evidence/hyperl-relocatable-host-check.json`.

The first matched-timing harness aborted before collecting a trial because its
16-token warm-up violated the existing shared 64–4,096 output-budget contract.
The harness was corrected to a 64-token cap and rebuilt; no comparison or gain
was attributed to that aborted run. Ordinary chat budgets were unchanged.

## Matched Intel CPU case — four native trials

Same pinned model/prompt, 1,024 context, four generation/eight prompt threads,
512/128 batch/micro-batch, f16 KV, temperature 0, seed 42, 128-token cap, short
warm-up and SSE/AVX2/AVX2/SSE order. All trials report **128 native completion
tokens** and pass. SSE4.2 rates: **7.100, 2.499** end-to-end tokens/sec; AVX2 rates:
**17.409, 12.052**. AVX2 is faster in these cases, but wide run-to-run variation
precludes a stable speed multiplier. OS/thermal conditions are not isolated;
this is one small model and one host, not general performance or cluster proof.
Loading is measured separately; no build/packaging jobs ran during these trials.
Raw settings, outputs and hashes: `evidence/cpu-benchmark-host-check.json`.

## Installer runtime regression and correction

The first extracted Studio PKG failed before inference because its trimmed Java
runtime omitted `java.logging`, needed by OSHI/JNA. This was caught by launching
the recovered application with `JAVA_HOME`, `JDK_HOME` and `CLASSPATH` unset.
Those failed generated installer/scratch copies were removed; no working release,
source or primary model was deleted. `jdeps` over all packaged jars identified
logging, instrumentation, RMI and GSS modules, now included alongside explicit
TLS/management modules in both desktop packaging paths. The desktop distribution
rebuild passes with unchanged application contracts.

`hdiutil create -srcfolder` also failed with “Device not configured.” File-based
HFS creation followed by UDZO conversion and verification succeeds; this is now
the reproducible DMG path. The intermediate HFS file receives a `.dmg` extension
from hdiutil, which must be used as the conversion input. Final recovered-payload
checks and publication are tracked below when completed.

## Intel Studio 40.1 recovered-payload acceptance

Application source `12bd20a` packages both `MeshlitPreview-2.0.40-studio.1-macos-intel`
DMG and PKG, with private Temurin runtime, starter weights, both CPU variants,
verified CPU probe and relocatable HyperL JNI. `pkgutil --expand-full` recovers
the PKG. macOS refused the read-only DMG mount with “Device not configured”;
official [7-Zip 26.04](https://www.7-zip.org/download.html) directly extracted HFS+
instead (developer-only tool, not shipped; archive SHA verified against its
GitHub release digest). `hdiutil verify` passes.

Both recovered applications match the signed image file-for-file, including
executable bits, model and sealed native checksums. Strict/deep ad-hoc signature
verification passes on both. With external Java environment removed, each
launcher generates a real starter reply, reports native usage, rejects missing
generation authentication, unloads its listener, and passes twelve native HyperL
recipes plus precise reduction. The PKG launcher additionally passes actual
OSHI sampling and management rendering; the rendered PNG was inspected.
See `desktop/evidence/{pkg,dmg}-local-check.json`, corresponding HyperL checks,
`pkg-monitor-check.json` and `installer-integrity-check.json`. Download assets
carry PACKAGING.json, VALIDATION.json, matching Java sources and SHA256SUMS.

These are same-host recovered-payload checks, not clean-machine installation or
OS-mounted DMG acceptance. The app is ad-hoc signed, PKG unsigned, neither
notarized. macOS 11 is the declared packaging floor; only macOS 15.8.1 was tested.

After preserving the verified installers, signed working app and evidence,
generated extracted payloads, HFS staging and duplicate reduced runtime/input
were removed. Observed free-space increase: **5,363,302,400 bytes** (about 5 GiB).
Primary model/JDK/native sources and Java source archive were retained.

## Documentation reconciliation — 2026-10-10

The owner requests Markdown plan/progress/phase tracking. Active PLAN/TODO/
REQUESTS/BUGS/handoff, architecture, release gates, build instructions, platform/
user guides and desktop feature state were reconciled; earlier versions are
retained as dated history. Offline help's desktop chapter matches the new guide.
All tracked Markdown is classified: 153 including the generated index, 118 active
guides refreshed, 19 historical, 8 protected legal/attribution, one PR template
and six third-party documents. Protected text is not relicensed or version-bumped.

Independent named workstreams replace a misleading global phase/completion
percentage. Registered Gradle inventory is 39 (core-stable-diffusion is an
unregistered source directory), with 88 feature areas/38 operations and a
separate overlapping 41-area desktop tracker. The generator is idempotent;
`update-doc-tracking.py --check` and feature-map validation pass. This is source/
documentation work, not a new APK installation or change to qualified installer
model/native bytes. Release progress remains independently observed.

## Remote CI follow-up

At source/evidence checkpoint 630cba2, Ubuntu/macOS host contracts and Android V1
pass; Windows reports one Ghostty argument-contract failure and two explicit
POSIX execution skips. The fixture used `/tmp`/`/Applications` paths, which are
not absolute drive-qualified Paths on Windows. It now uses native absolute temp
paths while retaining host-key pin, literal key-with-spaces, forwarding denial
and relative-path rejection checks. No production controller/policy changes,
no extra skip and no Windows Ghostty execution claim. Local suite/rerun results
are recorded after completion; installer application source remains 12bd20a.

After the portable fixture correction, the Intel local desktop suite passes
all 30 cases in a 14-second run. Current runtime/model binaries did not change.
Documentation links, generator idempotence, unchanged protected text/archive
identity, feature/source references and 39 registered Gradle modules also pass
their checks. GitHub platform rerun is a separate observed gate.

## Verified Intel publication

Public Experimental release: https://github.com/sabbirimon/meshlit-v2/releases/tag/v2.0.0-macos-intel-build40.1. Release ID 408390281, source/tag
`c1f2d60ffeff043ef2da210c552ccaf73b65ea8a`, application packaging source 12bd20a.
Both installers, matching Temurin Java sources, INSTALL/PACKAGING/VALIDATION/
RUNTIME_SOURCE and SHA256SUMS upload successfully. All **eight** GitHub names,
sizes, uploaded states and SHA-256 digests match local files before publication
and remain identical after publication. The resolved public tag matches its
source target. Draft target updates do not rewrite an existing published tag.
Structured proof: desktop/evidence/github-studio-release-check.json.

The first release-create attempt used an abbreviated SHA rejected by GitHub; it
created no release/assets. The full SHA succeeded. Current remote CI is separate
from local/payload acceptance and does not imply physical Windows/NEXT/cluster
qualification. No PR merge or Play submission was performed.

Public HTTPS download HEAD checks for both DMG and PKG return **200**, with
redirects restricted to HTTPS. Structured proof includes those results.

At published source c1f2d60, [host contract CI](https://github.com/sabbirimon/meshlit-v2/actions/runs/38000011088) completes successfully on Ubuntu, macOS and Windows. The Windows fixture correction passes without adding a skip; two existing POSIX execution skips remain. [Android CI](https://github.com/sabbirimon/meshlit-v2/actions/runs/38000011128) has V1 successful and V2 still running at this snapshot. Later documentation commits trigger independent checks; these results describe the named release source.

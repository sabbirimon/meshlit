# Stabilization build and acceptance guide
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-10. Companion to the [proposed milestone](STABILIZATION_PLAN.md). This guide describes commands present in the checkout and future acceptance procedures. **No stabilization build or new test run is claimed by writing this guide.** Proposed session persistence, resumable transfer, shared authority and desktop distributed execution are not available merely because their acceptance procedure is documented.

## 1. Build matrix and prerequisites

| Target | Current route | Evidence boundary |
| --- | --- | --- |
| Intel macOS desktop | Compose/JVM plus pinned CPU/native builders and custom DMG/PKG packager | Previously tested on this Intel Mac, macOS 15.8.1; clean independent target still required |
| Android V1/V2 | Existing Gradle debug variants, real pinned Android starter | Build/lint success is separate from installation and physical peer tests |
| Desktop shared contracts | JVM tasks in CI on macOS, Windows and Ubuntu | Does not establish native model or installer support on every OS |
| Other desktop architectures, NEXT and iOS | Separate future qualification | Do not run the Intel native packager and label the result cross-platform |

For Intel packaging, use a portable x86_64 JDK 21 with jlink/jpackage and its matching upstream source archive; Python 3.11+ (the packager uses hashlib.file_digest); Git; CMake; Xcode command-line tools/clang; macOS codesign, pkgbuild and hdiutil. Android configuration requires the SDK; current compileSdk is 37, with NDK versions governed by module configuration. Use the checked-in Gradle wrapper and pinned dependency versions. Confirm SDK/NDK requirements from the checkout rather than upgrading dependencies to make an unrelated tool work.

The model alone is about 1.12 GB. Budget for the two native builds, Java sources, Gradle/SDK caches, app image, package staging and two installers. Check free storage before starting and use one known scratch directory; disk requirements depend on which caches already exist. Imported user model files are never build junk.

## 2. Select source, directories and portable Java

Run from the intended clean Meshlit worktree. Existing location on this Mac:

```sh
cd /path/to/workspace/2026-10-06/re/outputs/meshlit
git status --short
git rev-parse HEAD
```

Commit/review intended source changes before capturing release provenance. Do not discard uncommitted work to obtain a clean checkout. Continue in a feature branch; main requires PRs and passing CI.

Set these paths for the machine. Replace the absolute input placeholders before running the commands; the work/delivery paths must not point to user data or an existing published build.

```sh
export MESHLIT_REPO="$PWD"
export MESHLIT_WORK="/absolute/writable/meshlit-stabilization-work"
export MESHLIT_JDK="/absolute/portable-jdk/Contents/Home"
export ANDROID_HOME="/absolute/android-sdk"
export JAVA_HOME="$MESHLIT_JDK"
export PATH="$MESHLIT_JDK/bin:$PATH"
export GRADLE_USER_HOME="$MESHLIT_WORK/gradle-home"
mkdir -p "$MESHLIT_WORK/evidence"
"$MESHLIT_JDK/bin/java" -version
python3 --version
cmake --version
xcrun --find clang
df -h "$MESHLIT_WORK"
```

Previously qualified portable Java on this Mac is `/path/to/workspace/2026-10-06/re/work/meshlit-macos-build40/temurin/jdk-21.0.12.1+1/Contents/Home`. Existing reusable Gradle cache is `/path/to/workspace/2026-10-06/re/work/gradle-home`. Check existence and provenance before reuse. Do not assume `/usr/libexec/java_home` selects the portable runtime used for distribution.

Use this shell helper for bounded builds on the 16 GiB Intel host:

```sh
meshlit_gradle() {
  ./gradlew --no-daemon --no-parallel --max-workers=1 \
    "-Dorg.gradle.java.home=$MESHLIT_JDK" \
    '-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8' \
    -Pkotlin.compiler.execution.strategy=in-process "$@"
}
```

Resolve missing SDK packages through Android Studio's SDK Manager or the reviewed project CI configuration. Missing toolchains, denied cache access and exhausted disk are environment failures; retain their logs before retrying.

## 3. Verify native, model and Java inputs

The native builder reads `vendored/sources.lock.json` and checks the nested llama checkout revision. If absent, inspect the optional-source helper before running it:

```sh
python3 scripts/sync-optional-sources.py --help
python3 scripts/build-desktop-llama.py --help
python3 scripts/build-desktop-hyperl.py --help
python3 scripts/package-macos-preview.py --help
```

Desktop starter identity comes from `desktopApp/distribution/bundled-model.json`; Android uses its separate asset manifest and preparation script. Do not use the Android starter-preparation script to obtain the desktop Qwen model.

Prefer an existing file with the correct hash. For an explicit first download from the manifest's pinned HF URL, use a temporary file and activate it only after verification:

```sh
mkdir -p "$MESHLIT_WORK/models"
export MESHLIT_MODEL="$MESHLIT_WORK/models/qwen2.5-1.5b-instruct-q4_k_m.gguf"
export MESHLIT_MODEL_URL="$(python3 -c 'import json; print(json.load(open("desktopApp/distribution/bundled-model.json"))["url"])')"
test ! -e "$MESHLIT_MODEL"
curl --fail --location --proto '=https' --proto-redir '=https' \
  --output "$MESHLIT_MODEL.part" "$MESHLIT_MODEL_URL"
python3 - <<'PY'
import hashlib, json, os
from pathlib import Path
spec = json.loads(Path('desktopApp/distribution/bundled-model.json').read_text())
final = Path(os.environ['MESHLIT_MODEL'])
part = Path(str(final) + '.part')
assert not final.exists(), 'Use a new destination; do not overwrite a model'
assert part.stat().st_size == spec['sizeBytes'], 'Wrong model size'
with part.open('rb') as stream:
    assert hashlib.file_digest(stream, 'sha256').hexdigest() == spec['sha256'], 'Wrong model hash'
part.rename(final)
print('Pinned desktop model verified')
PY
```

Run the block step by step and stop on a failed command. Do not retry over an existing `.part` file if it belongs to an active transfer. For an existing model, perform the same size/SHA check directly against that file and set MESHLIT_MODEL to it. This manual acquisition block is not the proposed in-app resumable downloader.

Obtain the portable JDK archive and exact matching Java sources from their reviewed publisher metadata. Verify both archive digests before extraction/use. The current public release's RUNTIME_SOURCE.json records the previous version, publisher URLs and hashes; it is evidence for those inputs, not a template to claim a different JDK. Preserve the original runtime record, then prepare a new provenance file with the actual application source commit. Required packager keys include sourceSha256 and applicationSourceCommit; also retain runtime version, binaryUrl/binarySha256, sourceUrl, licence and modification description.

```sh
export MESHLIT_RUNTIME_SOURCE="/absolute/path/to/matching-jdk-sources.tar.gz"
export MESHLIT_RUNTIME_INPUT="/absolute/path/to/verified-runtime-record.json"
export MESHLIT_RUNTIME_RECORD="$MESHLIT_WORK/runtime-provenance.json"
python3 - <<'PY'
import hashlib, json, os, subprocess
from pathlib import Path
record = json.loads(Path(os.environ['MESHLIT_RUNTIME_INPUT']).read_text())
with Path(os.environ['MESHLIT_RUNTIME_SOURCE']).open('rb') as stream:
    assert hashlib.file_digest(stream, 'sha256').hexdigest() == record['sourceSha256']
record['applicationSourceCommit'] = subprocess.check_output(['git','rev-parse','HEAD'], text=True).strip()
Path(os.environ['MESHLIT_RUNTIME_RECORD']).write_text(json.dumps(record, indent=2)+'\n')
PY
```

The packager checks the Java source archive and model hash. It does not independently prove that an arbitrary extracted JDK matches the claimed binary archive; that input verification is a separate recorded prerequisite.

## 4. Compile and test desktop/shared contracts

```sh
meshlit_gradle :shared-workspace:jvmTest :desktop-engine:test :desktop-ssh:test :desktopApp:test
meshlit_gradle :desktopApp:createDistributable "-Pmeshlit.packagingJdk=$MESHLIT_JDK"
```

Inspect each module's `build/test-results` and `build/reports/tests`. Record executed, cached and skipped results separately. Do not add counts from repeated shared-source tests and call them independent device coverage.

The Compose image normally appears at `desktopApp/build/compose/binaries/main/app/MeshlitPreview.app`; confirm the actual output exists. This intermediate image supplies application classes/resources. The custom packager adds the verified model/native engines and creates the portable runtime. `:desktopApp:run` can open the development shell, but does not by itself prepare the complete offline distribution.

## 5. Build Intel native components

```sh
python3 scripts/build-desktop-llama.py \
  --output "$MESHLIT_WORK/llama-baseline" --jobs 2 --variant baseline
python3 scripts/build-desktop-llama.py \
  --output "$MESHLIT_WORK/llama-avx2" --jobs 2 --variant avx2
clang -O2 -std=c11 -Wall -Wextra -Werror -arch x86_64 \
  -mmacosx-version-min=11.0 desktopApp/native/cpu_features.c \
  -o "$MESHLIT_WORK/cpu-features"
python3 scripts/build-desktop-hyperl.py --jdk-home "$MESHLIT_JDK" \
  --output "$MESHLIT_WORK/hyperl-native/libmeshlit_hyperl.dylib"
```

These are Intel CPU builds. Keep baseline and AVX2 outputs in separate directories. The verified CPUID/XGETBV probe governs optimized dispatch; changing compiler flags does not qualify a new accelerator. HyperL retains its separate licence and is exercised only through explicit native CPU actions.

## 6. Package a local candidate

The current packager hardcodes build40.1 identity and filenames. These commands reproduce that packaging path into new local output directories. Before a *new public stabilization release*, update all version/identity/install text/provenance fields coherently in an implementation change and retest; do not overwrite the published build40.1 assets or reuse their validation report.

```sh
export MESHLIT_DELIVERY="$MESHLIT_WORK/candidate-delivery"
export MESHLIT_PACKAGE_WORK="$MESHLIT_WORK/candidate-package"
python3 scripts/package-macos-preview.py \
  --app-image "$MESHLIT_REPO/desktopApp/build/compose/binaries/main/app/MeshlitPreview.app" \
  --jdk-home "$MESHLIT_JDK" \
  --output "$MESHLIT_DELIVERY" --scratch "$MESHLIT_PACKAGE_WORK" \
  --runtime-provenance "$MESHLIT_RUNTIME_RECORD" \
  --runtime-source "$MESHLIT_RUNTIME_SOURCE" --model "$MESHLIT_MODEL" \
  --server "$MESHLIT_WORK/llama-baseline/bin/llama-server" \
  --fast-server "$MESHLIT_WORK/llama-avx2/bin/llama-server" \
  --cpu-probe "$MESHLIT_WORK/cpu-features" \
  --hyperl-library "$MESHLIT_WORK/hyperl-native/libmeshlit_hyperl.dylib"
```

Output and package-work directories must be new. On retry, choose new names or inspect/remove only the failed generated candidate; never blanket-delete caches, source or model storage. Outputs include DMG, PKG, INSTALL, PACKAGING, matching Java source/provenance and SHA256SUMS. VALIDATION.json must be produced from actual candidate observations afterward; packaging does not manufacture acceptance results.

The app receives an ad-hoc integrity signature; the PKG is unsigned and neither is notarized. Developer ID signing/notarization and clean-machine acceptance are separate release gates.

## 7. Exercise actual packaged payloads

First test the staged app, then recover DMG and PKG into separate fresh directories and repeat from each recovered launcher. Use a disposable test account/profile for stateful scenarios; current smoke options do not isolate every platform preference automatically.

```sh
export MESHLIT_APP="$MESHLIT_PACKAGE_WORK/image/MeshlitPreview.app"
export MESHLIT_LAUNCHER="$MESHLIT_APP/Contents/MacOS/MeshlitPreview"
codesign --verify --deep --strict "$MESHLIT_APP"
env -u JAVA_HOME -u JDK_HOME -u CLASSPATH PATH=/usr/bin:/bin:/usr/sbin:/sbin \
  "$MESHLIT_LAUNCHER" --local-check "$MESHLIT_WORK/evidence/local.json"
env -u JAVA_HOME -u JDK_HOME -u CLASSPATH PATH=/usr/bin:/bin:/usr/sbin:/sbin \
  "$MESHLIT_LAUNCHER" --monitor-check "$MESHLIT_WORK/evidence/monitor.json"
env -u JAVA_HOME -u JDK_HOME -u CLASSPATH PATH=/usr/bin:/bin:/usr/sbin:/sbin \
  "$MESHLIT_LAUNCHER" --hyperl-check "$MESHLIT_WORK/evidence/hyperl.json"
env -u JAVA_HOME -u JDK_HOME -u CLASSPATH PATH=/usr/bin:/bin:/usr/sbin:/sbin \
  "$MESHLIT_LAUNCHER" --render-management-check "$MESHLIT_WORK/evidence/management.png"
```

Inspect JSON result fields and the rendered image. A process exit code alone is insufficient. `--local-check` exercises real model generation, native context/model identity, missing-authentication rejection and listener closure. `--monitor-check` does not prove process termination. `--render-management-check` is offscreen rendering, not interactive UX. `--hub-check` makes external HF requests and proves metadata behavior only; run it only for the explicitly online test. Engine-options/CPU-benchmark checks are optional diagnostics with separate quality/performance interpretation.

For PKG recovery use `pkgutil --expand-full <candidate.pkg> <new-directory>` and locate the recovered app. For DMG use `hdiutil verify <candidate.dmg>` followed by a read-only mount on a capable host. If mounting is unavailable, retain the failure and use a verified file extractor for payload inspection, marking OS-mounted installation as untested. Compare recovered app files/hashes/execute permissions to the signed source image, and rerun the launcher checks with distinct evidence filenames. Do not disable Gatekeeper or strip quarantine to label installation successful.

Then execute the S1–S4 manual workflows from the installed app: saved chat across restart/crash, download interruption/cancel, low disk in disposable storage, model switch/load cancellation, two-hour monitoring/lifecycle soak and a real disposable-process Stop. These acceptance harnesses are future implementation work where no current script exists.

## 8. Android regression and device preparation

```sh
python3 scripts/prepare-bundled-model.py
meshlit_gradle :core-inference:testDebugUnitTest :core-mcp:testDebugUnitTest :core-net:testDebugUnitTest :core-sandbox:testDebugUnitTest
meshlit_gradle :app:testMeshlitV1DebugUnitTest :app:testMeshlitV2DebugUnitTest
meshlit_gradle :app:assembleMeshlitV1Debug :app:assembleMeshlitV2Debug
meshlit_gradle :app:lintMeshlitV1Debug :app:lintMeshlitV2Debug
"$ANDROID_HOME/platform-tools/adb" devices -l
```

Run assemblies and lints sequentially on the constrained Intel host. Use the actual debug variants above; Full is a product/channel description here, not an invented `FullDebug` task. Find output APKs under `app/build/outputs/apk` and record hashes/signatures. Install the chosen compatible-signed debug variant on the explicitly selected serial with `adb -s <serial> install -r <apk>`. Preserve data; if signatures conflict, stop and resolve the build identity rather than uninstalling automatically. The user handles device unlocking and USB debugging authorization.

Verify current local chat/model/monitor behavior first. Record Samsung model/OS/API/ABI, model SHA and network path. Obtain the Windows/Xiaomi details before assigning additional cases. These are test devices, not assumed compatible workers.

## 9. Native protocol proof versus future physical-cluster proof

Existing host helpers are useful for bounded precursor checks:

```sh
python3 scripts/build-pipeline-native.py --backend cpu --jobs 2
python3 scripts/prove-layer-rpc.py --model "$MESHLIT_MODEL" \
  --output "$MESHLIT_WORK/evidence/host-layer-rpc"
```

Inspect model/backend compatibility before running. The output argument is a directory containing proof.json and native logs. This helper uses loopback native processes and cannot satisfy S5. For Android native inputs inspect `scripts/build-pipeline-native.py --help` and the documented NDK/ABI contract before building; a host executable cannot be copied to the phone as an Android worker.

There is no completed desktop enrollment/executor command to document yet. The S5 implementation must add the controller and its test harness before a physical run. Required run record: both paired identities, source/native/model hashes, per-device positive layer contribution, actual native memory, prompt/settings, timing/bytes, output and cancellation. Reproduce worker disconnect/revocation/suspend/deadline failures and explicit retry. Never expose the low-level RPC port publicly to make the demonstration work.

## 10. Evidence, review and release

For each run record gate/case, source SHA and dirty state, build/input hashes, device/OS/backend, exact command, exit status, observed behavior, limitations and evidence path. Exclude credentials, private transcripts and unrelated process details from committed/public reports. Record failures and fixes; preserve raw private evidence separately where needed.

```sh
python3 scripts/update-doc-tracking.py --write
python3 scripts/update-doc-tracking.py --check
python3 scripts/validate-feature-map.py
git diff --check
```

Update the milestone states, TODO, PROGRESS, build ledger and release evidence. Commit source/docs and small redacted evidence; keep weights, JDKs, build products and caches out of Git. Open/update the feature PR and require both Android checks and all three desktop host contract checks. Passing CI does not replace clean-target or physical-device gates.

Publish only after the applicable local/cluster release decision in the plan. Give the new release a unique version and immutable source target, recalculate checksums after adding final validation files, compare remote asset sizes/digests and test public download URLs. Keep the previous installer for rollback. This guide does not authorize a merge or a new release during plan refinement.

## Troubleshooting map

| Failure | Next action |
| --- | --- |
| Missing Java logging/management/TLS class in recovered app | Check full-classpath jdeps and explicit runtime modules; rebuild both installers and repeat payload tests |
| Native loader references a development/Homebrew path | Fix relocatable build inputs; keep the packager's dependency rejection |
| Illegal instruction or rejected AVX2 profile | Verify architecture/probe/hash; use qualified baseline rather than forcing unsupported instructions |
| Gradle daemon disappears or host swaps heavily | Retain logs, check free RAM/disk, use serial in-process Kotlin and portable JDK; remove only inspected generated heap dumps |
| Model checksum/size fails | Retain failure metadata, discard only the candidate transfer and reacquire the pinned artifact; never rewrite the expected hash to match corruption |
| Cache/SDK access denied | Select a permitted cache/SDK or obtain access; do not report the skipped work as passing |
| DMG Device not configured | Keep the mount failure distinct from file extraction and payload launch success; test installation on a capable clean target |
| ADB absent/unauthorized | Reconnect/unlock and let the owner authorize the device; other host checks may proceed |
| Cluster slower, incompatible or unavailable | Record measurements/blocker; do not silently replace the run with single-device inference and label it distributed |

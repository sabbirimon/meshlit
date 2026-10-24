# Meshlit build and validation
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](PLAN.md) · [Progress](PROGRESS.md) · [Document status](docs/DOCUMENTATION_STATUS.md).
Scope: cross-platform tracking; feature and device gates remain separate. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

**Paused checkpoint — 2026-10-24:** see [source status and resumption gates](docs/PAUSED_CHECKPOINT_2026-10-24.md). The commands below are for use after work resumes; the latest build45 run did not finish lint or final device qualification.


Read `AGENT_BUILD.md` for architecture and handoff details. Android Studio Opus checkout
is separate: `/Users/code/AndroidStudioProjects/mllm`. This checkout has not modified it.
Do not overwrite another agent's changes when porting these files.

## Detailed stabilization guide

See [desktop stabilization build guide](docs/desktop/STABILIZATION_BUILD_GUIDE.md) for portable Java/model/native inputs, bounded Gradle commands, Intel DMG/PKG packaging, recovered-payload checks, Android regression/device preparation and troubleshooting. The linked [milestone plan](docs/desktop/STABILIZATION_PLAN.md) defines future acceptance; the current planning update does not run those tests or implement missing features.

## Android
Requires JDK 21, Android SDK platform 37, build tools and NDK 28.2.13676358.
Use your own cache directories when the normal user cache is restricted.

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export ANDROID_HOME=/Users/code/Library/Android/sdk
./gradlew :core-inference:testDebugUnitTest :core-mcp:testDebugUnitTest :core-net:testDebugUnitTest :core-sandbox:testDebugUnitTest --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
./gradlew :app:testMeshlitV1DebugUnitTest :app:testMeshlitV2DebugUnitTest --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
./gradlew :app:assembleMeshlitV1Debug :app:assembleMeshlitV2Debug --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
./gradlew :app:lintMeshlitV1Debug :app:lintMeshlitV2Debug --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
```

Robolectric writes a dependency lock/cache under Java `user.home`. A restricted environment
can pass `-Pmeshlit.testHome=/absolute/writable/test-home` to select a writable test JVM home/cache; an access-denied dependency failure is not a
product regression or a passed check. Do not skip tests to report a green build.

## Native pipeline

```sh
python3 scripts/sync-optional-sources.py --help
python3 scripts/build-pipeline-native.py --help
python3 scripts/prove-layer-rpc.py --help
```

Pin upstream revisions and retain notices. Native host proof uses two loopback processes;
it does not establish physical-phone or oversized-model support. Generated JNI executables
must be built for the installed ABI before packaging. CPU is the validated host backend;
optional vendor backends need the relevant toolchain and device evidence.

## Optional companions
See `companions/crawler/README.md`, `companions/pipeline/README.md` and
`docs/openclaw-integration.md`. Gateways, Python/Chromium, Linux images and proprietary
GPU drivers are not installed silently. Keep credentials and model files out of Git.

## Device checks
Install both flavors separately as appropriate, review chat/models/settings/monitor on small
and large screens, then run `InstalledModelSmokeTest` with a verified GGUF in app-managed
storage. Record actual device model/API/ABI and test model SHA. Inspect service stop, background
behavior, revocation and worker loss. Physical phone cluster testing remains required.

For offline Robolectric runtimes in a restricted environment, set
`-Pmeshlit.robolectricDir=/absolute/path/to/verified/android-all-instrumented-jars`.
This points tests at preinstalled runtimes rather than creating an unauthorized
user-home download lock. Missing runtimes must be reported as environment failures.

## Required real starter model
Before any APK build, run `python3 scripts/prepare-bundled-model.py` from the root.
The asset manifest is revision/SHA/size pinned; Gradle rejects missing or corrupt
weights. Binary models remain ignored by Git. See `app/src/main/assets/models/README.md`.

## Latest continuation priorities (2026-10-06)

Keep genuine phone layer sharding/model execution and committed recovery as the
main product priority. Nodes also contribute storage, tools, capture, sensors,
actuation, preprocessing, DSP, routing and monitoring according to real evidence.
ESP32/Arduino/IoT membership does not imply transformer execution.
Read `docs/online-power-peripherals-and-configuration.md`,
`docs/declarative-federation-roadmap.md` and `docs/media-iot-and-radio-nodes.md`.
These record offline/online selection, free/public/gated model distinctions,
actual pricing provenance, power readings, hardware/vendor backend limits, open
source SSH, configuration transfer, media sources and owner-paired radio carriers.
Keep unsupported adapters visibly unavailable; never invent devices, samples,
throughput, tokens, costs, account entitlements or recovery success.

## Scenario routing and media continuation (2026-10-06)

Read `docs/model-router-and-media.md`. Scenario recipes, sequential chains/comparisons,
chat text attachments and explicit online vision/image/speech/video adapters are
wired in source. Validate real execution; paid provider media outputs and generic
music/sound/on-device diffusion/video remain separate gates. Do not substitute
text event counts for native tokens. Keep phone layer execution/recovery primary.

## Custom models and training continuation (2026-10-06)

Read [docs/local-model-behavior-and-training.md](docs/local-model-behavior-and-training.md). Custom local prompt behavior is default-off;
compatible custom weights are imported through Models. No toggle changes learned
refusals or hosted-provider rules. Settings → Fine-tuning drives the optional
Soup 0.75.0 POSIX host companion via pinned SSH, with real job/status/log/cancel
contracts. Android synthetic gradients are removed; phone autograd reports
unavailable. Actual Soup training, adapter quality/evaluation and GGUF deployment
remain acceptance gates. Keep phone inference layer sharding/recovery primary.

## Desktop/server contracts and Intel packaging

```sh
./gradlew :shared-workspace:jvmTest :desktop-engine:test :desktop-ssh:test :desktopApp:test
./gradlew :desktopApp:createDistributable -Pmeshlit.packagingJdk=/absolute/portable/jdk-home
python3 scripts/build-desktop-llama.py --help
python3 scripts/build-desktop-hyperl.py --help
python3 scripts/package-macos-preview.py --help
python3 scripts/update-doc-tracking.py --check
python3 scripts/validate-feature-map.py
```

Follow docs/MACOS_OFFLINE.md for pinned starter/native/JDK inputs. The checked packager creates Intel DMG/PKG with full model/native provenance, portable Java, ad-hoc integrity signing and no elevated install scripts. It requires fresh output/scratch paths. Recover both payloads and run actual `--local-check`, `--hyperl-check`, `--monitor-check` and render checks with external Java environment removed. See docs/DESKTOP_BUILD_LOG.md for current successes/failures and remaining clean-target/notarization gates. Docs-only changes do not imply a fresh APK or independent hardware run.

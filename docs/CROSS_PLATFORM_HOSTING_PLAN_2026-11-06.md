# Cross-platform application and hosting plan — 2026-11-06
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-06: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

This is a source/toolchain audit and implementation plan, not a claim of completed platform parity. The owner requests a full desktop/mobile application, supported local and remote hosting roles, a common Management hierarchy, credentials/SSH/app-lock/biometric controls, and an iOS application later with native Apple glass styling. There is no iOS hardware for acceptance now. Existing local chat remains usable without a mandatory password; optional protection must not silently turn every chat into an unlock workflow.

## Build families and evidence

There are three existing application families, not one already-portable binary:

- Android Kotlin/Compose application: [app build](../app/build.gradle.kts), two Meshlit UI flavors, `compileSdk=37`, `targetSdk=36`, `minSdk=24`. Default package ABI filters include arm64-v8a, armeabi-v7a, x86 and x86_64; that does **not** establish a functioning native engine on each ABI. [Native pipeline builder](../scripts/build-pipeline-native.py) explicitly targets arm64-v8a or x86_64.
- Compose JVM desktop application/backend: [desktop build](../desktopApp/build.gradle.kts), JDK 21 toolchain/JVM 17 bytecode, host-dependent native renderer and JavaFX dependencies, DMG/MSI/DEB formats. Current Studio uses its backend within a Code OSS shell; the previous Compose client remains a separate frontend/build lineage.
- Native HarmonyOS NEXT ArkTS/ArkUI preview: [ohosApp](../ohosApp/README.md), provisional Stage/Hvigor configuration targeting SDK 26.0.0. Its [C++ capability module](../ohosApp/entry/src/main/cpp/runtime_capabilities.cpp) explicitly reports local inference unavailable.

[Shared workspace](../shared-workspace/build.gradle.kts) currently declares only a JVM target. Shared contracts are not a complete Kotlin/Native/iOS application. No iOS/Xcode application target was found in the root module list. [Studio preflight](../companions/studio-runtime/qualify.py) deliberately accepts Darwin x86_64 only; [workbench preparation](../companions/studio-runtime/prepare_workbench.py) selects an Intel Mac SDK wheel, Mach-O runtime/resources and Apple bundle layout. Copying that bundle to another OS/ABI is not a port.

| Platform | App/build source or artifact | Hosting roles currently evidenced | Remaining gates |
| --- | --- | --- | --- |
| macOS Intel x86_64 | Current Code OSS Studio packaging and older Compose client | Bundled local CPU chat; local backend/agent orchestration; one experimental native Mac/Samsung layer run; optional host companions | Clean machine install, signing/notarization, complete management/security/voice acceptance; packaged managed cluster workflow still separate |
| macOS Apple Silicon arm64 | JVM UI is architecture-selective in source; current Studio preparation is Intel-specific | No arm64 Meshlit package/inference or host qualification established by this audit | Pinned arm64 workbench/SDK/Python/JDK/model engine, native helper build, Metal/CPU correctness, keychain, clean hardware tests; do not count Rosetta as native proof |
| Windows x64 | Historical Compose preview MSI/CI artifact exists | Remote compatible model client/source contracts; historical installer build tests only | Current shell port, Windows runtime/IPC/process lifecycle, native engine, OS secret store, terminal executable adapter, signing and physical install/hosting tests |
| Windows ARM64 | No qualified package found | No qualified native host role | Separate dependencies/native architecture qualification; x64 emulation is a distinct test lane |
| Linux amd64 | Historical Compose preview DEB/CI artifact exists | Optional Python node/storage/DSP source; compatible model-client source | Current shell packaging, native engine, service/Secret Service/permissions, Wayland/X11/accessibility and physical installation/hosting tests |
| Linux ARM64 / Pi / NAS | Optional Python node design targets owner-operated hosts; no qualified complete Studio package found | Storage/offline FFT companion source; runtime must report actual capabilities | Architecture-specific native engine/SDK, distro/container packaging, service hardening, model correctness and sustained resource tests |
| Android arm64 | Active APK/foreground services/native runtime source; prior Samsung evidence | Local inference; gateway/client permissions; native worker and coordinator implementation; narrow physical mixed-host proof | New build regression, managed pairing end-to-end, background/thermal/battery/Stop behavior, large-model and failure/recovery qualification |
| Android other configured ABIs | APK filters exist; native availability checked separately | Only capabilities backed by installed working binaries may be shown | Per-ABI engine availability and tests; do not equate APK installation with inference or worker support |
| Android-compatible HarmonyOS devices | Potential Android APK path only if that exact device/OS actually supports it | No Huawei/Harmony-specific acceptance evidence reviewed here | APK/SDK compatibility, native ABI, services/power behavior and hardware qualification |
| HarmonyOS NEXT | Native ArkTS client preview; HTTPS model/chat source, C++ capability boundary | Remote client source only; local engine explicitly unavailable; no established worker/coordinator | DevEco SDK/Hvigor compile/sign/HAP, simulator/device, native engine port, storage/authentication, lifecycle and host roles |
| iOS/iPadOS — later | Requested roadmap; no application target | No current client/inference/hosting claim | Xcode + signing + device access, native UI/client first, separately qualified local engine and foreground compute; background hosting cannot be promised |

Historical release evidence [prerelease45-validation.json](desktop/evidence/prerelease45-validation.json) says Windows/Linux CI tests and installers passed, physical installation was not tested, and bundled local inference was false. These results do not validate today's Studio shell.

[2026-11-06 physical USB evidence](desktop/evidence/studio-mac-samsung-usb-native-20261106.json) records Qwen2.5 0.5B layers 0–19 on Mac and 20–24 on Samsung, with a real answer through Studio. It explicitly marks workspace UI, managed pairing, TLS and oversized-model tests false. [2026-11-03 private WAN evidence](desktop/evidence/mixed-mac-samsung-wan-native-20261103.json) records a development Mac/Samsung completion over Tailscale LTE and credential rejection checks; managed UI execution, oversized-model proof, fair speedup and automatic distributed recovery remain unestablished. No new device test was run for this audit.

## Hosting is a capability contract, not a platform checkbox

Expose these roles separately under Management → Devices & hosting:

1. **Client/controller:** select local, hosted provider or an enrolled compatible endpoint; inspect actual model and permissions. Starting Chat does not start a listener.
2. **Local model host:** installed model plus qualified native runtime, explicit bind/address scope, authenticated clients, resource budget and visible Stop. The desktop [LocalEngine](../desktopApp/src/main/kotlin/com/meshlit/desktop/LocalEngine.kt) currently owns a private loopback process, not a public service.
3. **Gateway/agent host:** issue/revoke bounded client grants, model/tool scopes and request limits. Android [GatewayHost](../app/src/main/kotlin/com/meshlit/gateway/GatewayHost.kt) has this implementation; Play Review disables inbound gateway. Desktop backend IPC is not automatically equivalent to Android gateway service parity.
4. **Layer worker/coordinator:** matched native revisions, compatible model/operators, explicit pairing and authenticated transport, measured memory placement and Stop/revocation. Android [PipelineHost](../app/src/main/kotlin/com/meshlit/pipeline/PipelineHost.kt) checks installed native binaries and uses separately controlled worker/coordinator operation. Memory totals alone cannot qualify a shard plan.
5. **Storage/tool companion:** [Python node companion](../companions/node/README.md) implements bounded authenticated loopback storage reads and offline FFT, with private approved tunnel transport. It must not advertise transformer/GPU execution.

Each role needs `unavailable → installed → configured → reviewed → running → stopping/stopped/failed` states, runtime/ABI/model provenance, bind address, current grants and last real check. Do not infer readiness from a saved address. Keep enrollment, provider billing, SSH access, shell execution, model workers and data-sharing grants independent. Human SSH terminals are not stopped by the chat Stop button; the dashboard says so explicitly.

## Common Management and security hierarchy

Keep General Chat as the default independent main window, with Chat, Workspaces, Models, Bots and Settings visible. Opening a project launches its own coding window/chat. Management groups capabilities consistently across platforms while allowing native presentation:

| Group | Required controls and truthful status |
| --- | --- |
| Models & providers | Installed/local model, third-party connection, cluster endpoint, actual readiness, context/reasoning/tool support; unknown cost remains unknown |
| Devices & hosting | Device roles above, pairing, resource sharing, connection tests, revoke, logs and scoped Stop |
| Credentials & security | API key presence/replace/remove; optional protected Usage; project scope; future app-lock and biometric configuration shown unavailable until wired |
| SSH & terminals | Non-secret profiles; host trust; explicit connect; actual terminal result; distinguish config check from handshake/authentication |
| Agents, bots & tools | Workspace agents, bot lifecycle, MCP/hooks, independent tool grants and execution history |
| Network & services | Actual listeners, protocol status, approved tunnels and connected providers; no automatic firewall exposure |
| Appearance & accessibility | Native platform visual treatment, typography, keyboard/focus, contrast, reduced motion/transparency and screen-reader semantics |

Current [credential dashboard](../companions/studio-runtime/extension/credentials-dashboard.cjs) exposes saved model-key lifecycle without displaying key values and labels full-app lock, biometric unlock, TPM/Secure Enclave management unimplemented. Current [SSH dashboard](../companions/studio-runtime/extension/ssh-dashboard.cjs) uses fixed `/usr/bin/ssh` on macOS/Linux only, explicit confirmation, argv, existing known_hosts, disabled forwarding/ambient config; it saves key paths only. Windows/external terminal handoff remains a separate adapter. Current [native read-aloud](../companions/studio-runtime/extension/chat-voice.cjs) and [App Access](../companions/studio-runtime/extension/app-access.cjs) are Mac-specific; [managed OpenLive startup](../companions/studio-runtime/extension/voice-runtime.cjs) currently rejects Windows.

`core-users` has a [BiometricGate interface](../core-users/src/main/kotlin/com/meshlit/core/users/BiometricGate.kt), not a complete wired Android biometric app lock. An interface or successful mock is insufficient evidence. Implement optional full-app lock as a distinct lifecycle state with explicit recovery/fallback, cancellation and relock timeout, no automatic remote grants, and no mandatory password for ordinary local chat. Protect secret key use through the OS store where available; never store biometric templates or pretend a boolean UI gate encrypts data. Locking UI, encrypting persisted data and authorizing a tool are three different controls.

Native adapter research, verified against primary references on 2026-11-06:

- Android: use system authentication through open-source AndroidX [BiometricPrompt implementation](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-biometric-release/biometric/biometric/src/main/java/androidx/biometric/BiometricPrompt.java), including lifecycle/cancellation and authentication-bound key use. Review the exact dependency licence before adding it.
- Linux: [fprint/libfprint/fprintd](https://fprint.freedesktop.org/) is a primary free-software implementation; fprintd provides D-Bus authentication integration. Supported reader/enrollment and separate secret storage remain necessary. Do not bundle or copy code without licence review, or treat fingerprint-match success as key-store encryption.
- Apple: use native [LocalAuthentication](https://developer.apple.com/documentation/localauthentication/logging-a-user-into-your-app-with-face-id-or-touch-id), availability checks and fallback with platform key storage. The system API is not a claim that Meshlit ships an open-source biometric engine.
- Windows: use the appropriate desktop interop for [UserConsentVerifier](https://learn.microsoft.com/en-us/uwp/api/windows.security.credentials.ui.userconsentverifier). Verify cancellation, unavailable hardware/PIN policy, key-storage binding and lock-screen behavior on Windows hardware.
- HarmonyOS NEXT: evaluate native [userAuth](https://developer.huawei.com/consumer/en/doc/harmonyos-references/js-apis-useriam-userauth/) with its actual installed SDK. No adapter is implemented or qualified here.

## iOS later, native Apple appearance

Do not block current Android/desktop correction on unavailable iPhone hardware. First add platform-independent connection/cancellation/credential-policy contract tests. Later, create an iOS/iPadOS native SwiftUI shell with the same Management information architecture, authenticated endpoint client, streaming/cancel, model/source identity and Keychain/LocalAuthentication. Local inference follows only after an iOS-native engine/model import, memory-pressure and foreground lifecycle implementation is tested. A desktop process-spawn backend is not portable to iOS.

Use Apple's native standard navigation and controls where supported; follow [Materials guidance](https://developer.apple.com/design/human-interface-guidelines/materials) for limited glass in navigation. Keep transcripts/code/settings content readable and opaque enough; preserve Reduce Transparency, Increase Contrast, Reduce Motion, Dynamic Type, VoiceOver labels, focus and keyboard navigation. Apple's [Liquid Glass overview](https://developer.apple.com/videos/play/wwdc2025/219/) describes native adaptation to accessibility settings. A CSS blur in Studio must not be called native Liquid Glass. OS-version availability fallbacks and measured energy/performance are release gates.

Treat iOS as client/foreground participant first. Apple's [background execution strategies](https://developer.apple.com/documentation/BackgroundTasks/choosing-background-strategies-for-your-app) use specific system-managed mechanisms; they do not establish an unrestricted always-on Meshlit server. Validate any later host role against current OS lifecycle/background API behavior and device tests; checkpoint/cancel when the OS removes execution time.

## Toolchains observed on this development Mac

Read-only checks in this audit found Darwin x86_64, Homebrew OpenJDK 21.0.12, Node, Python 3, CMake and ADB. Android SDK includes `android-37.0`, build-tools 37.0.0 and NDK 28.2.13676358. `ninja` was absent from PATH; this does not prove it is absent from an SDK's CMake directory. No device identifiers were collected or device changes made.

`xcode-select -p` points to `/Library/Developer/CommandLineTools`; `/Applications/Xcode.app` is absent and `xcrun --sdk iphoneos --show-sdk-path` fails because the SDK cannot be located. DevEco application paths and common SDK directories were absent; `hvigor`, `hvigorw`, `ohpm` were not found in PATH. Thus this Mac can run existing Android/JVM/Node/Python checks, but currently cannot compile or qualify iOS or HarmonyOS NEXT. Windows/Linux installers require their respective build environments; this Intel Mac cannot supply native hardware acceptance for them or Apple Silicon.

## Checks that can run here now

These are recommended commands, **not new passing results** from this documentation audit. Run with bounded workers and record the actual result; coordinate with any already-running build rather than duplicating it.

```sh
node --test companions/studio-runtime/extension/*.test.cjs
python3 -m unittest discover -s companions/studio-runtime -p 'test_*.py'
python3 -m unittest discover -s companions/node/tests -v
./gradlew :shared-workspace:jvmTest :desktop-engine:test :desktop-ssh:test :desktopApp:test --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
./gradlew :app:testMeshlitV1DebugUnitTest :app:testMeshlitV2DebugUnitTest --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
```

For APK assembly, first validate/prepare the pinned real starter model with `python3 scripts/prepare-bundled-model.py`, then run both flavor assembly and lint commands in [BUILD.md](../BUILD.md). Offline missing dependencies/native binaries are explicit blockers, not reasons to report success. Local native tests can establish this Mac's behavior only. Current Studio packaging requires the known pinned portable runtime inputs in [prepare_workbench.py](../companions/studio-runtime/prepare_workbench.py); rebuilding Compose distributables alone does not rebuild the current shell.

Before release, use a separate CI lane per OS/ABI and publish a capability manifest per artifact. Require install/update/uninstall, fresh-profile launch into General Chat, project-window separation, actual model response, configuration persistence, key unavailable/revoked/cancelled cases, tool boundaries, Stop, resource pressure and accessibility. Test a real worker pair for each advertised hosting role, then loss/rejoin/recovery and oversized-model execution separately. A complete Management page does not certify those workflows.

## Delivery order

1. Finish current Intel Studio and Android regression; preserve separate General Chat/IDE windows and passwordless local usage. Ship truthful Management navigation and service status.
2. Add platform adapters and package current Studio for Windows x64, Linux amd64 and native Apple Silicon; qualify local model runtime and credential/terminal behavior per platform before declaring hosting support.
3. Complete managed Mac/Android device pairing, worker/coordinator controls and failure/Stop acceptance. Expand other desktop/Linux hosts after per-ABI native proof; measure end-to-end performance before claiming combined speed.
4. Implement optional app lock and native authentication adapters, beginning on available hardware; test unavailable/cancelled/enrollment-change/recovery paths and independent credential storage.
5. Build/sign/test the existing HarmonyOS NEXT client with official SDK/hardware; keep Android-compatible Harmony paths separate. Port native inference and worker roles only after client acceptance.
6. Add iOS/iPadOS later when Xcode and hardware are available, with native accessible glass UI and client/foreground role first. Do not label the current plan an iOS build.

Related: [Studio/Android settings parity](STUDIO_ANDROID_SETTINGS_PARITY_2026-11-06.md), [platform adapter plan](PLATFORM_ADAPTER_PLAN.md), [NEXT boundaries](MULTIPLATFORM_NEXT.md), [current architecture](architecture/current-state.md). Older broad completion statements must be interpreted within their dated build/evidence scope.

## Current increment evidence

The Management category is now implemented in the shared DashboardDirectory and
Android dashboard. Both Android variants compiled; seven settings navigation
tests passed for each. V1/V2 debug APK assembly passed; final lint result and
physical Samsung readiness are recorded in the current progress/evidence ledger.
The phone was ADB-authorized but locked, so no install or UI test is claimed.

Studio's Management, Credentials and SSH pages passed native navigation checks.
The SSH module now supports an explicitly reviewed macOS System Terminal handoff
as well as the built-in terminal, with fixed executable/application paths, safe
argument quoting, temporary-file cleanup and existing host-key checks. Its 14
contract tests do not prove a remote login. Ghostty/Termius and Windows terminal
adapters remain separate. No real credential, external account or biometric was
used by these checks.

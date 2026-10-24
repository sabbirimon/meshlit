# Meshlit desktop and native HarmonyOS NEXT preview
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: platform preview; NEXT device/build acceptance remains open. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-10. This is an incremental migration, not a replacement of the
working Android engine. New clients are Experimental; the separately installed
Android Core Candidate keeps its existing operation restrictions.

Desktop update 2026-10-10: the Intel Mac installer now bundles a Qwen2.5 1.5B
GGUF and private CPU engine; see [MACOS_OFFLINE.md](MACOS_OFFLINE.md). Host-only
descriptions below document the first build40 client and remain applicable to
other unqualified platform images. HarmonyOS NEXT is unchanged. The larger
desktop-only expansion is tracked in [DESKTOP_STUDIO_PLAN.md](DESKTOP_STUDIO_PLAN.md).

## Implemented source

- `shared-workspace` uses Kotlin Multiplatform 2.4.10. `commonMain` owns portable
  English/简体中文 selection, visual palette values, conversation bounds and truthful
  token-usage contracts. The JVM target is consumed by Android and desktop. The
  existing bounded CommonMark/GFM reply parser moved unchanged into `jvmMain`;
  Android keeps its native Compose reader, history, prompt and streaming engine.
- Android Appearance adds persisted interface language and Monochrome/Studio presets. Studio adds an ember accent, compact icon menu and actionable hero cards.
  English is the default. The modern chat and model-library resource strings have
  Simplified Chinese translations; advanced tools currently retain English. This
  does not translate model answers, rewrite prompts or change cloud/tool grants.
  Monochrome keeps font scale, accessibility and operation permissions.
- `desktopApp` is a Compose Multiplatform JVM client for an explicitly selected
  Meshlit/OpenAI-compatible host. It lists actual host models, streams real SSE
  text where the host streams it, bounds context/output, presents headings/bold/code/tables and explicitly
  supplied chart data, provides chat search/copy and retains session history only
  in memory. English/简体中文, four themes and text size persist independently.
  Intel Studio 40.1 additionally bundles the verified compact model/CPU runtime and exposes engine/management controls. Other platform packages need their own native bundle and hardware qualification. Starting the client makes no model-provider request.
- `desktopApp:cli` uses the same authenticated transport and bounds. It is a JVM
  CLI, not a Kotlin/Native executable. The desktop distribution can bundle its JVM
  on the build host. Cross-platform native inference and standalone Native CLI
  binaries remain future adapter/qualification work.
- `ohosApp` is a separate native ArkTS/ArkUI Stage application **specifically for
  HarmonyOS NEXT**, targeting the current 26.0.0 SDK. It does not load the Android
  APK, JNI libraries or RunAnywhere Android AAR. English is the default; 简体中文 is
  the second choice. Appearance persists with ArkData Preferences. The source
  supplies native selectable paragraph/headings layout, bounded authenticated
  HTTPS model-list/chat requests, Stop and reported token usage. This first NEXT
  client uses complete-response requests, not streaming. Its reader is simpler
  than Android/desktop CommonMark; rich table/code/inline parity remains work.
- The NEXT C++/N-API module reads an explicit native capability. It reports local
  LLM inference **unavailable**: no llama.cpp or ONNX runtime has been linked or
  tested for NEXT. Loading this capability module is not inference evidence.

Android and desktop currently share contracts and the JVM parser, not their
entire Compose UI, Android services, database, keys, agent kernel or federation
implementation. NEXT mirrors the portable contracts through its native adapter;
no Kotlin binary is linked into the HAP yet. This deliberate boundary avoids
reinitializing the repository or downgrading the working Android build.

## Connection and privacy

Enable host access explicitly and enter the exact `/v1` URL. Remote desktop hosts
require HTTPS with system-trusted TLS and a client bearer token. Plain HTTP is
accepted only on literal local loopback (`127.0.0.1` or `::1`), not a LAN name/IP.
NEXT accepts HTTPS only. Both deny redirects, disable unintended background access
and bound requests. Use a trusted TLS gateway for a phone/LAN/Internet host; these
clients do not create a public listener or automatically configure a tunnel.

Prompts and token go to the selected host. Whether that host runs offline on a
phone/cluster or forwards to a provider is determined by the host's configuration;
the client does not label an arbitrary host as an offline model. Host permission,
model loading and client-token issuance remain separate existing controls.
Neither preview stores tokens or chats in preferences or exports them to logs.
Desktop appearance, engine options and model/node references persist; provider secrets/chat remain session-only. NEXT persists appearance only. Closing the app clears the chat session. Optional network
and agent permissions are never changed by a language/theme choice.

The current Meshlit phone compatibility server returns a buffered SSE completion;
the desktop client cannot make that server emit live per-token updates. A compatible
streaming host can emit live text.

Output token count stays unknown until reported by the host. The displayed rate
uses reported completion tokens divided by elapsed request time, including latency;
it is explicitly **end-to-end**, not a native decode benchmark or cluster estimate.
Interrupted/failed responses are excluded from the next desktop request. Stop
revokes the current request; stale callbacks cannot append to a newer chat.

## Desktop build

Use the repository's JDK 21, Android SDK and Gradle wrapper. Do not change the
Android Kotlin/AGP pins. Commands from the repository root:

```sh
./gradlew :shared-workspace:jvmTest :desktopApp:test
./gradlew :desktopApp:run
./gradlew :desktopApp:cli --args='--help'
./gradlew :desktopApp:cli --args='https://trusted-host/v1 models'
# Set MESHLIT_CLIENT_TOKEN privately in the process environment, not CLI arguments.
# Chat reads the prompt from stdin; use the model id reported by the host.
./gradlew :desktopApp:cli --args='https://trusted-host/v1 chat your-model-id 2048'
./gradlew :desktopApp:createDistributable
```

The bundled launcher also accepts `--cli --help` and `--cli <endpoint> models`.
For an explicit offline graphics smoke check it accepts `--render-check <png-file>`;
this renders the real empty workspace and is not model-generation evidence.

`packageDmg`, `packageMsi` and `packageDeb` require their respective build OS and
packaging tools. A successful Mac build does not qualify Windows or Linux.
Compose 1.10.2 is pinned for Intel Mac support and the current Android dependency
line. Current 1.12.1 documentation lists arm64 macOS only, so silently adopting it
would exclude this Intel development Mac. JDK/runtime updates and the older Compose
pin need continuing compatibility/security review before production distribution.
The local packaging check warns about the Homebrew JDK. The development Mac image
was built with the one-command `-Pcompose.desktop.packaging.checkJdkVendor=false`
exception, then checked using its bundled launcher/native renderer. The exception
is not a persistent project default. Use a supported non-Homebrew JDK for release
packaging and validate on a clean target Mac; local proof does not establish
redistributable production qualification.

## Latest NEXT build boundary

Huawei currently documents SDK/target `26.0.0` and adaptation to HarmonyOS 7.0
in the NEXT native ecosystem. The application intentionally sets compile, target
and compatible SDK to `26.0.0`; older Android-compatible HarmonyOS is not targeted.
The NetworkKit `maxRedirects` API (introduced at API 23) is required for credentials
not to follow redirects. The native app uses system TLS and does not ship a trust
bypass, secret signing identity or fake local model.

Open `ohosApp` in the current DevEco Studio for HarmonyOS NEXT/26.0.0. Use its
installed Hvigor/SDK, resolve the IDE's official build plugin and configure signing
locally. No third-party Kuikly compiler fork is installed and no copied SDK wrapper
or signing material is committed. The supplied Stage project/Hvigor files require
verification with the actual installed SDK; JSON validation is not ArkTS compilation.

No DevEco Studio/SDK or NEXT device is available here. Therefore **no HAP, NEXT
native compilation, signing, simulator run or physical NEXT inference has passed**.
Local-runtime work requires separately pinned native sources, licence review,
NEXT NDK builds, model integrity/import/loading, real generation and lifecycle/
thermal/memory tests. Shared Compose-on-NEXT integration is also gated: Tencent's
Kuikly source currently uses a custom Kotlin compiler/older AGP stack and cannot
be dropped into this Android toolchain without a reviewed compatibility boundary.

## Sources checked 2026-10-09

- [JetBrains supported platforms](https://kotlinlang.org/docs/multiplatform/supported-platforms.html)
- [Compose compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
- [Compose 1.10.2 release](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.10.2)
- [Huawei NEXT development](https://developer.huawei.com/consumer/en/harmonyos/develop/)
- [Huawei 26.0.0 adaptation](https://developer.huawei.com/consumer/en/doc/harmonyos-releases/upgrade-adaptation)
- [Huawei 26.0.0 tool versions](https://developer.huawei.com/consumer/en/doc/harmonyos-releases/deveco-studio-new-features-2600)
- [NetworkKit HTTP API source](https://github.com/openharmony/interface_sdk-js/blob/master/api/%40ohos.net.http.d.ts)
- [Kuikly OHOS build configuration](https://github.com/Tencent-TDS/KuiklyUI/blob/main/build.2.0.ohos.gradle.kts)

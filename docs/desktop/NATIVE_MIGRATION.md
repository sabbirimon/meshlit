# JVM dependency migration
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-23: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

| Existing dependency | Replacement | Status |
| --- | --- | --- |
| Compose Desktop / AWT and the jpackage Java runtime | Rust desktop launcher, packaged VSCodium window and Meshlit extension | Native Studio entry point. General chat and a separate coding window are available. |
| Desktop Kotlin model orchestration | Extension host starts a hash-verified native llama.cpp server and calls its authenticated loopback API | Native Studio. The remote route uses the selected HTTP API implementation. |
| JDK compiler, debugger and shell supplied with the old suite | Zig C/C++ compiler drivers and standard libraries, the editor's native terminal and the host shell | Bundled in Native Studio. Java project tools can be installed separately by someone developing Java. |
| Gradle and JDK during full desktop packaging | Python packaging script, rustc and CMake | The Full Studio CI workflow builds directly from the native sources and pinned distributions. |
| Android Compose, Koin, Kotlin coroutines and Android app services | Android NDK C/C++ or Rust services, NativeActivity and JNI at Android-only system boundaries | Existing Android APK uses Kotlin/ART. Its UI, lifecycle, trust storage, pairing and background services need feature-by-feature parity checks before migration. |
| Existing layer planner | Pure C shared admission kernel with a thin Android JNI adapter, or Goose as a native host executable | The native Android route preserves automatic and manual admission, integer ties, estimates and budget rejection. The C kernel can be built without JNI or Java. |

HyperL already ships a standalone Rust processor engine and C kernels. Its desktop UI uses packaged Python/Tk, and its Android CPU diagnostic uses NativeActivity with no application DEX. Android SDK packaging tools can still require Java on the build host. Removing the desktop JVM changes startup and memory behavior. Compare cold and warm startup, peak memory, time to first token and tokens per second at identical model, context and thread settings before attributing a speedup.

Earlier Meshlit packages and source remain available while advanced dashboards, device enrollment, SSH and agent workflows are ported. A compatible remote model-server connection in Native Studio is separate from enrolling a shard worker.

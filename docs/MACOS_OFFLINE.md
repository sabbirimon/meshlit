# Intel Mac offline desktop preview
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-10. Desktop-only addition to build40. Android's bundled SmolLM2
starter and local inference defaults remain unchanged. The shared worker-admission
planner adds bounded whole-layer allocation; both Android flavor Kotlin compilations pass. The earlier host-only Mac preview is being replaced by a desktop studio;
new installer/native acceptance is recorded in [DESKTOP_BUILD_LOG.md](DESKTOP_BUILD_LOG.md).

The desktop bundle includes the publisher's Qwen2.5 1.5B Instruct Q4_K_M GGUF,
1,117,320,736 bytes, Apache-2.0. The immutable revision, source URL and full
SHA-256 are in `desktopApp/distribution/bundled-model.json`. `LocalEngine` checks
the full hash before loading the built-in file. The model manager records imported file SHA-256 and rechecks it on managed load.
This integrity admission does not certify the publisher or model compatibility.

The unmodified pinned standalone llama.cpp CPU server is built with static
libraries and system Accelerate, without Metal, CURL, OpenSSL or OpenMP. Two
explicit Intel builds provide SSE4.2 and AVX2/FMA/F16C; BMI2 and AVX512 are disabled.
A hash-verified native CPUID/XGETBV probe confirms both CPU and OS vector support
before Auto selects the optimized build. Compatible CPU can be selected explicitly;
forced AVX2 fails on unsupported hardware. Only system dynamic dependencies are used.
It starts after a human Load or Send on an unloaded local model, on
127.0.0.1 with a fresh 256-bit bearer in a private 0600 temporary file. One worker,
4,096 context tokens and up to four generation threads are the defaults. Context,
threads, batching, KV keys and idle unloading are configurable in Inference engine.
The owner-only
process stops on Unload, window disposal, JVM shutdown, source switch or Stop
during generation. Five-minute idle unloading never interrupts an active request.
The full GGUF hash, available-memory/KV admission and native model/context readback
must pass. Stale load callbacks cannot attach a superseded model.

Server-readiness polling has a deadline; full file hashing and setup add time. Chat uses the existing bounded SSE transport and
reports actual server completion tokens divided by total request time. This is
end-to-end throughput, not a decode-only benchmark. A large chat/context can fail;
there is no automatic summarization, cloud fallback or fake response. This model
is useful for compact text/chat/code tasks but is not a frontier-quality model,
vision model or autonomous tool executor.

Settings → Models manages bundled and imported GGUF references; imported weights
are not copied. Discover models searches Hugging Face after an explicit service
opt-in and admits revision-pinned single-file GGUFs only after full size/SHA checks.
Interrupted transfers are removed; resume is not yet implemented. Basic/Advanced
settings are grouped; monitor/process, crypto, terminal, SSH and optional native
CPU HyperL have desktop adapters. These are narrower than Android feature parity.
See [scope](DESKTOP_FEATURE_TRACKER.md) and [Android comparison](ANDROID_DESKTOP_PARITY.md).

Switching local/remote clears session history. Keys, prompts and grants are session-only;
appearance, navigation mode, model references and saved node addresses persist.
Custom or model-native local response instructions do not change remote host policies.

## Build and package

1. Verify the desktop model against its pinned manifest and obtain a checked
   portable Eclipse Temurin 21 Intel JDK and matching full upstream sources.
2. Build Kotlin desktop classes with `:desktopApp:test :desktopApp:createDistributable`.
   Use `-Pmeshlit.packagingJdk=<portable-jdk>/Contents/Home` for the Compose image.
   Only the application input/resources are reused; the packager replaces the
   Homebrew-dependent runtime and launcher with Temurin's jlink/jpackage output.
3. Run `python3 scripts/build-desktop-llama.py --output <scratch>/llama-cpu --jobs 2 --variant baseline`
   and the same command with `--output <scratch>/llama-cpu-avx2 --variant avx2`.
   Compile the original Meshlit `desktopApp/native/cpu_features.c` probe with
   `clang -O2 -std=c11 -Wall -Wextra -Werror -arch x86_64 -mmacosx-version-min=11.0
   desktopApp/native/cpu_features.c -o <scratch>/cpu-features`.
4. Build the separately licensed, unmodified HyperL CPU/JNI source with
   `python3 scripts/build-desktop-hyperl.py --jdk-home <portable-jdk>/Contents/Home
   --output <scratch>/hyperl-native/libmeshlit_hyperl.dylib`. This gives it a
   relocatable `@rpath` install name; absolute development-path dependencies fail packaging.
5. Run `python3 scripts/package-macos-preview.py --app-image <MeshlitPreview.app>
   --jdk-home <portable-jdk>/Contents/Home --output <new-delivery-dir>
   --scratch <new-scratch-dir> --runtime-provenance <runtime-provenance.json>
   --runtime-source <matching-source.tar.gz> --model <pinned-model.gguf>
   --server <llama-cpu>/bin/llama-server --fast-server <llama-cpu-avx2>/bin/llama-server
   --cpu-probe <scratch>/cpu-features --hyperl-library <libmeshlit_hyperl.dylib>` (one command).

The packager preserves licences, validates all native files are Intel and rejects
absolute dynamic dependencies outside system library paths. It creates an ad-hoc
integrity signature only. No Developer ID or notarization is asserted. It creates
a read-only compressed DMG with an Applications shortcut and a script-free PKG
containing the exact same app, plus SHA256SUMS, runtime/source provenance and
matching runtime source download. Only installers/assets go to GitHub Releases;
weights, JDKs and binaries must remain out of Git.

Run `MeshlitPreview --local-check <evidence.json>` from each recovered installer
payload with no external JAVA_HOME/PATH. This explicitly generates a real reply,
checks reported tokens, rejects missing local authentication and verifies Unload
closed the listener. Run `--render-check <workspace.png>` for the actual Compose
renderer. These checks do not prove a physical click-through installation on a
clean Mac or qualify other OS/GPU targets. Only macOS 15.8.1 Intel is currently
tested; the configured OS floor is 11.0. Production signing/notarization and a
second clean Mac remain release qualification work.

Studio 40.1 uses file-based HFS creation followed by UDZO compression, avoiding
a temporary mounted-device dependency during build. The reduced Java runtime
includes modules from full-classpath jdeps plus explicit TLS/management modules.
Both installer payloads pass actual bundled-runtime generation, unload and
HyperL JNI checks; DMG extraction was file-based because mounting was unavailable
in this build session. See the desktop build ledger and release VALIDATION.json.

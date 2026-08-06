plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    compileSdk = 37
    // minSdk = 24 — RunAnywhere SDK requires API 24+ (Android 7.0+).
    // The previous floor of 23 was kept when NanoHTTPD was the only
    // third-party native surface; the RunAnywhere integration raises
    // it by one API level which has a negligible install-base impact.
    defaultConfig { minSdk = 24 }
    namespace = "com.meshlit.core.inference"

    // Bundled GGUFs are uncompressed inside the APK so llama.cpp can
    // mmap them directly via AAsset_openFileDescriptor.
    androidResources {
        noCompress += "gguf"
    }
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-trust"))
    implementation(project(":core-firewall"))
    implementation(project(":core-observability"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    // Vendored SDK uses Square Wire's Message / WireEnum base classes
    // for its public types (STTPartialResult, TTSOptions, etc.).
    implementation(libs.wire.runtime)

    // Embedded HTTP/SSE inference server. We use NanoHTTPD (pure-Java)
    // instead of Ktor 3 because Ktor's bytecode requires DEX 040
    // output (default from API 33) which would block the user's
    // minSdk = 23 floor.
    implementation(libs.nanohttpd.core)
    // Used by the cluster-shard transport (ShardTransport) to fetch
    // and push model shards between peers. OkHttp is preferred over
    // Ktor 3 for the same DEX-039 compatibility reason as NanoHTTPD
    // above — Android 6+ devices (minSdk 24) are in scope.
    implementation(libs.okhttp.core)

    implementation(libs.kotlinx.serialization.json)

    // RunAnywhere SDK — Phase 2.x on-device LLM runtime.
    // The Kotlin core artifact gives us `RunAnywhere.initialize(...)` plus
    // download/load/generate flows. The llama.cpp backend artifact
    // pulls in libllama.so per ABI and is what actually executes the
    // model. The ONNX backend pulls in sherpa-onnx for STT/TTS/VAD
    // on the Voice screen — see `RunAnywhereVoiceEngine` for the
    // integration surface. Note: `runanywhere-onnx` already bundles
    // libonnxruntime.so per ABI, so we intentionally do NOT also
    // depend on `onnxruntime-mobile` — the two AARs both ship the
    // same native lib and AGP's merger rejects the duplicate.
    //
    // Vendored: the Kotlin core is now sourced from
    // vendored/runanywhere-kotlin (see that module's LICENSE /
    // MODIFICATIONS.md). The AARs below contribute the native `.so`
    // files at runtime AND the small Kotlin backend binding files
    // (LlamaCPP.kt, ONNX.kt, LlamaCPPBridge.kt, etc.) at compile time.
    implementation(project(":runanywhere-kotlin:runanywhere-kotlin"))
    implementation(libs.runanywhere.llamacpp)
    implementation(libs.runanywhere.onnx)
    // Pull in runanywhere-sdk's .so files (librunanywhere_jni.so +
    // librac_commons.so + libomp.so + librac_backend_cloud.so). The
    // vendored module's namespace (com.runanywhere.sdk.kotlin.vendored)
    // differs from this AAR's (com.runanywhere.sdk.kotlin) so the
    // manifest merger no longer flags the duplicate.
    runtimeOnly(libs.runanywhere.sdk)

    testImplementation(libs.junit)
}
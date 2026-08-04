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
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)

    // Embedded HTTP/SSE inference server. We use NanoHTTPD (pure-Java)
    // instead of Ktor 3 because Ktor's bytecode requires DEX 040
    // output (default from API 33) which would block the user's
    // minSdk = 23 floor.
    implementation(libs.nanohttpd.core)

    implementation(libs.kotlinx.serialization.json)

    // RunAnywhere SDK — Phase 2.x on-device LLM runtime.
    // The core artifact gives us `RunAnywhere.initialize(...)` plus
    // download/load/generate flows. The llama.cpp backend artifact
    // pulls in libllama.so per ABI and is what actually executes the
    // model. The ONNX backend pulls in sherpa-onnx for STT/TTS/VAD
    // on the Voice screen — see `RunAnywhereVoiceEngine` for the
    // integration surface. Note: `runanywhere-onnx` already bundles
    // libonnxruntime.so per ABI, so we intentionally do NOT also
    // depend on `onnxruntime-mobile` — the two AARs both ship the
    // same native lib and AGP's merger rejects the duplicate.
    implementation(libs.runanywhere.sdk)
    implementation(libs.runanywhere.llamacpp)
    implementation(libs.runanywhere.onnx)

    testImplementation(libs.junit)
}
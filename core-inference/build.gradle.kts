plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    compileSdk = 37
    defaultConfig { minSdk = 23 }
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

    // Phase 2.x — second shipped inference backend. ONNX Runtime
    // Mobile is a single-aar JNI binding for `.onnx` models. The aar
    // pulls in libonnxruntime.so (~8 MB arm64) plus a thin Java
    // surface. The engine code below mirrors LlamaCppInferenceEngine
    // and declares the JNI entry points as `external` until the
    // upstream ORT JNI symbols are linked.
    implementation(libs.onnxruntime.mobile)

    testImplementation(libs.junit)
}
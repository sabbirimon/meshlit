plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    compileSdk = 37
    defaultConfig { minSdk = 23 }
    namespace = "com.meshlit.core.federation"
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-trust"))

    // OkHttp is the canonical HTTP client already used by `:core-net`.
    // mTLS is configured via `OkHttpClient.Builder.sslSocketFactory(...)`
    // — the actual key material comes from the Android Keystore-backed
    // trust store (`:core-trust`), never from a PEM on disk.
    implementation(libs.okhttp.core)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // MockWebServer speaks HTTP/1.1 over loopback — perfect for the
    // wire-codec unit tests. The full TLS path is exercised by the
    // Android instrumentation tests on two physical devices.
    testImplementation(libs.okhttp.mockwebserver)
}
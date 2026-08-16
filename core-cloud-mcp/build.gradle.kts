plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    compileSdk = 37
    defaultConfig { minSdk = 23 }
    namespace = "com.meshlit.core.cloudmcp"
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-trust"))
    implementation(project(":core-observability"))

    // OkHttp is the project's HTTP convention — see
    // RemoteInferenceClient.kt:20-24 for rationale. The cloud-MCP
    // transport reuses the same client; SSE parsing is hand-rolled
    // (see SseParser.kt) to stay consistent.
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)

    // Android Keystore-backed EncryptedSharedPreferences for
    // cloud-provider tokens. The wrapper lives in core-trust;
    // this dep pulls the underlying androidx.security-crypto
    // library into the consumer graph.
    implementation(libs.androidx.security.crypto)

    // Room for the local RAG store. KSP / kapt for the annotation
    // processor is intentionally NOT wired here — see
    // LocalRagStore.kt for the in-memory fallback that ships
    // first. Adding KSP is a follow-up that touches the build
    // plugin graph project-wide. We keep room-runtime on the
    // classpath so a follow-up PR can flip the local store to a
    // real DAO without graph churn.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    // UI Automator — Google's official Android UI Automator
    // library. Pulled into the production classpath because
    // MeshlitAccessibilityService binds against UiDevice / By /
    // UiObject2 at runtime (see AndroidUiAutomatorBridge.kt), not
    // just during tests. The library is API 21+ and Play-
    // distributed, so the production inclusion is on the same
    // policy footing as Espresso / Compose UI Automator.
    implementation(libs.androidx.uiautomator)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
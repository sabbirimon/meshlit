plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    compileSdk = 37
    defaultConfig { minSdk = 23 }
    namespace = "com.meshlit.core.agentmemory"
}

dependencies {
    // Phase 0 :core-agent-memory spike. Per the TencentDB review
    // (./Users/code/.puku-cli/plans/tencentdb-agent-memory-review.md),
    // the architecture is borrowed but the runtime dep is intentionally
    // NOT pulled — this module owns its own storage + retrieval +
    // distiller surface, all on-device.
    implementation(project(":core-common"))
    implementation(project(":core-trust"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
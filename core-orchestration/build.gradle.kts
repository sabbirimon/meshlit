plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    compileSdk = 37
    defaultConfig { minSdk = 23 }
    namespace = "com.meshlit.core.orchestration"
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-trust"))
    implementation(project(":core-discovery"))
    implementation(project(":core-inference"))
    implementation(project(":core-mcp"))
    implementation(project(":core-training"))
    implementation(project(":core-files"))
    implementation(project(":core-ssh"))
    implementation(project(":core-firewall"))
    implementation(project(":core-guardrails"))
    implementation(project(":core-tunnel"))
    implementation(project(":core-users"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
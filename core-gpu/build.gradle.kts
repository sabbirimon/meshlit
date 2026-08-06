plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    compileSdk = 37
    defaultConfig {
        minSdk = 23
        // Spec gate: 0 = CPU only stub, 1 = Vulkan compute (eGPU-capable).
        buildConfigField("int", "GPU_STACK", "1")
    }
    namespace = "com.meshlit.core.gpu"
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-inference"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}

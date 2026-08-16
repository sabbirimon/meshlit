plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    compileSdk = 37
    defaultConfig { minSdk = 23 }
    namespace = "com.meshlit.core.bootstrap"
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-config"))
    implementation(project(":core-flags"))
    implementation(project(":core-registry"))
    implementation(project(":core-lifecycle"))
    implementation(project(":core-probe"))
    implementation(project(":core-role"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

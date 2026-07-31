plugins {
    alias(libs.plugins.android.library)
}

android {
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    namespace = "com.meshlit.core.trust"
}

dependencies {
    implementation(project(":core-common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
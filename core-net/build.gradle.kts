plugins {
    alias(libs.plugins.android.library)
}

android {
    compileSdk = 37
    defaultConfig { minSdk = 23 }
    namespace = "com.meshlit.core.net"
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-observability"))
    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.opentelemetry.api)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}

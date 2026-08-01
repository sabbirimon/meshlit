plugins {
    alias(libs.plugins.android.library)
}

android {
    compileSdk = 37
    defaultConfig { minSdk = 23 }
    namespace = "com.meshlit.core.firewall"
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-trust"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
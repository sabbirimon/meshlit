plugins {
    alias(libs.plugins.android.library)
}
android {
    compileSdk = 37
    defaultConfig { minSdk = 24 }
    namespace = "com.meshlit.core.sandbox"
}
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

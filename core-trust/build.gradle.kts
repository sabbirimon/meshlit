plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    compileSdk = 37
    defaultConfig { minSdk = 23 }
    namespace = "com.meshlit.core.trust"
}

dependencies {
    implementation(project(":core-common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    // Android Keystore-backed EncryptedSharedPreferences for
    // cloud-MCP provider tokens (NaraRouter API key, AWS access
    // keys, etc.). See EncryptedCredentialStore + CloudCredentialStore.
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
}
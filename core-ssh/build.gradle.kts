plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    compileSdk = 37
    defaultConfig { minSdk = 23 }
    namespace = "com.meshlit.core.ssh"
}

dependencies {
    implementation("com.github.mwiede:jsch:2.28.7")
    // Pinned stable server transport; API 26+ path only.
    implementation("org.apache.sshd:sshd-core:2.20.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation(project(":core-common"))
    implementation(project(":core-trust"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}

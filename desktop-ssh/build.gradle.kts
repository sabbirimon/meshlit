plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    sourceSets.main { kotlin.srcDir("../core-ssh/src/main/kotlin") }
    sourceSets.test {
        kotlin.srcDir("../core-ssh/src/test/kotlin")
        kotlin.include("**/NodeSshServerTest.kt", "**/NodeSshProtocolTest.kt")
    }
}
dependencies {
    implementation("com.github.mwiede:jsch:2.28.7")
    implementation("org.apache.sshd:sshd-core:2.20.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    compileOnly("androidx.annotation:annotation:1.9.1")
    testImplementation(libs.junit)
}

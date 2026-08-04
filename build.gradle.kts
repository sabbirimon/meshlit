// Top-level build file. Project-level config lives in module build.gradle.kts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Global dependency constraints.
//
// The RunAnywhere SDK 0.20.12 transitively pulls
// `kotlinx-coroutines-core:1.11.0` → `kotlin-stdlib:2.4.0`, but the
// project pins Kotlin 2.1.0 (compiler version 2.2.0) which cannot
// read 2.4.0 metadata. Without these constraints every compile of
// `:core-inference` fails with "Class 'kotlin.Unit' was compiled
// with an incompatible version of Kotlin". Forcing the stdlib back
// to the Kotlin 2.1.x line + coroutines to the 1.10.x line lets the
// compiler resolve to versions it understands.
//
// Add new constraints here when a new SDK pulls in a higher Kotlin
// minor than the compiler supports. The compileSdk target (Kotlin
// 2.1.0 in `gradle/libs.versions.toml`) is the source of truth.
allprojects {
    configurations.all {
        resolutionStrategy {
            // Match the version the Kotlin 2.1.x compiler ships.
            force(
                "org.jetbrains.kotlin:kotlin-stdlib:2.1.20",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.1.20",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.20",
                "org.jetbrains.kotlin:kotlin-reflect:2.1.20",
                // 1.11.0 introduced `kotlin.Result` overloads that
                // require kotlin-stdlib 2.3+, which we can't read.
                // Pin to 1.10.0 (the version pinned in libs.versions.toml).
                "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.0",
                "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.0",
            )
        }
    }
}

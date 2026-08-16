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
// `kotlinx-coroutines-core:1.11.0` → `kotlin-stdlib:2.4.0`, which
// the project's Kotlin 2.4.10 compiler (declared in
// `gradle/libs.versions.toml` as `kotlin = "2.4.10"`) reads natively.
// No force block needed; Gradle resolves everything to 2.4.x / 1.11.x
// without intervention.
//
// If a future SDK upgrade transitively pulls a Kotlin metadata version
// newer than 2.4.x, either (a) bump the project's Kotlin plugin in
// `gradle/libs.versions.toml`, or (b) re-introduce a force block here
// to pin to a compiler-readable line. Don't silently invent a force.
//

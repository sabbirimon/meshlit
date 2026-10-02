plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    compileSdk = 37
    defaultConfig { minSdk = 23 }
    namespace = "com.meshlit.core.common"
}

// Slice 1 — explicitly register src/test/kotlin so the new CapabilityMatrix
// unit tests (and any future Kotlin tests in :core-common) get picked up by
// the Kotlin compileDebugUnitTestKotlin task. The convention plugin applies
// kotlin-android but does NOT auto-add the kotlin/ test source dir for this
// module (it does for modules that ship at least one existing .kt test file
// — chicken/egg). Once any test lands, future tests are auto-detected.
//
// The Android library plugin creates the `test` source set lazily
// when a consumer asks for a unit-test task. We register the
// kotlin/ source dir only if the set already exists, so the build
// graph stays happy on a clean configuration-cache invalidation.
sourceSets.findByName("test")?.java?.srcDirs("src/test/kotlin")

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.slf4j.api)

    testImplementation(libs.junit)
}
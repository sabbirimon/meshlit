plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.meshlit"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.meshlit"
        // Bumped from 29 to 34 because D8 emits DEX 040 by default
        // from API 34 onwards. Ktor 3.x uses spaces in some field
        // SimpleNames (e.g. "use streaming syntax") which the
        // pre-DEX-040 dexer rejects. minSdk 34 covers Android 14+,
        // which is what Phase 1's physical-device test targets.
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // Required for `coreLibraryDesugaring` and also flips D8
        // into DEX 040 output, which Ktor 3.x needs.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Netty (pulled in by Ktor server) ships duplicate
            // META-INF descriptors across its many split JARs.
            // Exclude them so the merge step doesn't fail.
            excludes += listOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/native-image/**",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }
}

dependencies {
    // Project modules (app consumes the orchestration facade)
    implementation(project(":core-orchestration"))
    implementation(project(":core-common"))
    implementation(project(":core-trust"))
    implementation(project(":core-discovery"))
    implementation(project(":core-inference"))
    implementation(project(":core-mcp"))
    implementation(project(":core-training"))
    implementation(project(":core-files"))
    implementation(project(":core-ssh"))
    implementation(project(":core-firewall"))
    implementation(project(":core-guardrails"))
    implementation(project(":core-tunnel"))
    implementation(project(":core-users"))

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Logging
    implementation(libs.slf4j.api)

    // JSON (for SettingsRepository / DeviceProfileRepository override blobs)
    implementation(libs.kotlinx.serialization.json)

    // Ktor client — remote inference dispatch (HTTP + SSE).
    // Server-side Ktor lives in :core-inference; :app only needs the
    // client pieces. OkHttp engine = battle-tested, plays well with
    // Android's HTTP cache and proxy stacks.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    // Core-library desugaring: flips D8 into DEX 040 output (Ktor 3
    // uses spaces in some field SimpleNames that pre-DEX-040
    // dexers reject).
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
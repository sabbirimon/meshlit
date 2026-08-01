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
        // Floor = API 23 (Android 6.0). Ktor 3 was dropped for NanoHTTPD
        // + OkHttp specifically to keep this floor; androidx.core 1.19
        // and Compose BOM 2025.05 require it.
        minSdk = 23
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
        // Required for `java.time.LocalTime` (API 26) and
        // `ConcurrentHashMap.newKeySet` (API 24) on API 23-25.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Bundled GGUFs must NOT be compressed inside the APK so
        // llama.cpp can mmap the file directly via AAsset_openFileDescriptor
        // without paying the inflate cost on every random-access read.
        // See BundledModelInstaller for the extraction pathway.
        noCompress += "gguf"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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

    // OkHttp — the remote-inference client. OkHttp works on every
    // supported minSdk (pure-Java), unlike Ktor 3 client which needs
    // DEX 040 bytecode from API 33.
    implementation(libs.okhttp.core)

    // Core-library desugaring: required for java.time + ConcurrentHashMap
    // on Android 6/7.
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
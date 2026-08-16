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
        // Floor = API 24 (Android 7.0). The RunAnywhere SDK 0.20.12
        // ships `libllama.so` with API 24+ symbol requirements (and
        // uses java.time on cold paths); `:core-inference` already
        // declares 24 as its floor, and the manifest merger refuses
        // to lower this module below the library floor. Bumping from
        // the previous 23 is acceptable — every device that ran the
        // 23-floor build also runs 24.
        minSdk = 24
        targetSdk = 36
        // Hivemind-1 cluster + Stitch glass UI + RunAnywhere SDK
        // parity (Phase 4.x). Bumped from 0.1.0 → 0.2.3 so the
        // /v1/health `version` field distinguishes the cluster
        // build from the pre-cluster baseline, and the GitHub
        // dev release gets a fresh version tag.
        versionCode = 3
        versionName = "0.2.3"

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
    implementation(project(":core-cloud-mcp"))
    implementation(project(":core-training"))
    implementation(project(":core-files"))
    implementation(project(":core-ssh"))
    implementation(project(":core-firewall"))
    implementation(project(":core-guardrails"))
    implementation(project(":core-tunnel"))
    implementation(project(":core-users"))
    implementation(project(":core-terminal"))
    implementation(project(":core-advanced-engines"))
    implementation(project(":core-net"))
    implementation(project(":core-observability"))
    implementation(project(":feature-advanced"))
    implementation(project(":feature-ghosty"))

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

    // QR pairing code generation (we render our own Meshlit pairing
    // QR on the Devices screen) + Google Play Services Code Scanner
    // (we scan peers' QR codes via Play Services' bundled scanner
    // UI). The scanner ships as a small stub that downloads the
    // module from Play Services on first launch — no CAMERA
    // permission needed in the manifest, no CameraX dep.
    implementation(libs.zxing.core)
    implementation(libs.play.services.code.scanner)

    // CameraX — `agent_camera_capture` tool. Lifecycle-aware
    // camera surface, ~3 MB across the three artifacts.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)

    // Fused location provider — `agent_location_get` tool.
    implementation(libs.play.services.location)
    // Bridge `Task<Location>` into suspend functions via `.await()`.
    implementation(libs.kotlinx.coroutines.play.services)

    // Phase Observability 1 — OpenTelemetry SDK + OTLP exporter.
    // The TracingController in :core-observability owns the SDK
    // lifecycle (Off / Local / Otel). When Otel is on, the OTLP
    // exporter pushes spans to the endpoint the user pastes in
    // Settings → Tracing.
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.exporter.logging)

    // Phase 0.3 — Koin DI. Replaces the 50+ `by lazy { ... }`
    // singletons that lived on MeshlitApplication. The BOM pins
    // koin-android / koin-core / koin-androidx-compose to the same
    // transitively-resolved version (4.2.2).
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.core)

    // Core-library desugaring: required for java.time + ConcurrentHashMap
    // on Android 6/7.
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.koin.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
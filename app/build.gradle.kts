import java.util.Properties

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

        // Phase 1.0 — NDK ABI filter. The RunAnywhere SDK 0.20.12
        // ships `libllama.so`, `librunanywhere_jni.so`, and
        // `libonnxruntime.so` for all four ABIs (arm64-v8a,
        // armeabi-v7a, x86, x86_64). The Debug APK splits limit
        // ABI selection further (see `splits { ... }` below);
        // this `abiFilters` line is the *default* set the AAR
        // packaging pulls from — it ensures the universal release
        // APK still has all four ABIs available.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    // V1 vs V2 UI split. The new (Neural-Expressive) UI ships as
    // `meshlitV2` — separate applicationId so both flavors can be
    // installed side-by-side on the same device and the v1 UI is
    // always available for emergency revert. Build no. 1 of the v2
    // UI is `2.0.0-v2build1`. The shared `BuildConfig.USE_NEW_UI`
    // flag selects between `MeshlitApp()` (v1) and `V2Root()` (v2)
    // from `MainActivity`.
    flavorDimensions += "ui"
    productFlavors {
        create("meshlitV1") {
            dimension = "ui"
            // DEBUG builds already have applicationIdSuffix = ".debug";
            // the v1 flavor is the no-op suffix so it falls back to
            // the classic `com.meshlit(.debug)` package.
            applicationIdSuffix = ""
            versionNameSuffix = "-v1"
            buildConfigField("boolean", "USE_NEW_UI", "false")
            resValue("string", "app_name", "Meshlit")
        }
        create("meshlitV2") {
            dimension = "ui"
            applicationIdSuffix = ".v2"
            // Build no. 1 of the new UI. Bump versionCode by 1
            // so Play Store and F-Droid see a fresh artifact.
            versionName = "2.0.0-v2build1"
            versionCode = (defaultConfig.versionCode ?: 1) + 1
            buildConfigField("boolean", "USE_NEW_UI", "true")
            resValue("string", "app_name", "Meshlit v2")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Phase 7 — wire the release signing config. When
            // `~/.gradle/meshlit-release.properties` is present (operator
            // has populated the four keystore fields) this resolves to
            // a real production APK. Otherwise we fall back to debug
            // signing so the build still completes for local smoke
            // tests; the §1 release gate then flags the build as
            // "unsigned at tag time" — see `docs/release-checklist.md` §1.
            signingConfig = if (rootProject.file("~/.gradle/meshlit-release.properties").exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            // Debug builds skip R8. The minify-on-debug setup
            // from Phase 1.0 hit a Gradle 9.4.1 / AGP 9.2.14
            // regression where an empty merged consumer-rules
            // pipeline triggers
            //   javax.xml.stream.XMLStreamException: ParseError at
            //   [row,col]:[1,1] Message: Premature end of file.
            // inside `R8Task$R8Runnable` (b/396287783, similar).
            // Debug APKs aren't shipped to the Play Store, so the
            // 450 MB unminified cost is acceptable for
            // internal-test + two-phone validation runs. Release
            // still minifies (see below).
            //
            // When the AGP fix lands, restore:
            //   isMinifyEnabled = true
            //   isShrinkResources = true
            //   proguardFiles(
            //     getDefaultProguardFile("proguard-android-optimize.txt"),
            //     "proguard-rules.pro",
            //   )
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    // Phase 1.0 — Lean APK (debug only). The Debug variant ships
    // per-ABI APKs (arm64-v8a + x86_64) plus a universal variant
    // for the rare emulator that needs x86. The arm64-v8a-only
    // build comes in around 50 % of the universal size because
    // the RunAnywhere SDK bundles four ABI variants of
    // `libllama.so` (~34 MB × 4 = ~136 MB just for the LLM
    // runtime). Universal APK stays as a fallback for x86
    // emulator installs.
    //
    // The `release` build is NOT split — it ships all four ABIs
    // in a single distribution APK so the Play Store can hand the
    // right one to each device.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
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
        // V2 build packaging — each flavor ships its own `app_name`
        // string via `resValue` (see `productFlavors` above). The
        // `resValues` feature gates the `resValue` API on the
        // Android extension.
        resValues = true
    }

    androidResources {
        // Bundled GGUFs must NOT be compressed inside the APK so
        // llama.cpp can mmap the file directly via AAsset_openFileDescriptor
        // without paying the inflate cost on every random-access read.
        // See BundledModelInstaller for the extraction pathway.
        noCompress += "gguf"
    }

    // Phase 7 — lint config. Default Android lint runs without a config
    // block, which leaves every default rule on and emits hundreds of
    // MissingTranslation / HardcodedText / IconLocation warnings that
    // have always been benign. We keep lint non-fatal (the gate is
    // "no NEW warnings introduced", not "zero warnings") and disable
    // the two rules that are noisy by default. `abortOnError = true`
    // would block a release build on any pre-existing warning; flipping
    // it on only after the lint tree is clean is a follow-up.
    lint {
        abortOnError = false
        warningsAsErrors = false
        checkReleaseBuilds = true
        disable += setOf(
            "MissingTranslation",
            "ExtraTranslation",
            "HardcodedText",
            "IconMissingDensityFolder",
            "GoogleAppIndexingWarning",
        )
    }

    // Phase 7 — release signing. The Phase 8 hook PR shipped without
    // any `signingConfigs` block, so `:app:assembleRelease` could not
    // produce a signed APK and §1 of the release checklist stayed red.
    //
    // We do NOT generate a production keystore here (the release
    // captain owns that — see `keystore.properties.example` at the
    // repo root for the operator template). Instead the build reads
    // the four keystore fields from `~/.gradle/meshlit-release.properties`
    // if that file exists, otherwise falls back to debug signing so
    // `./gradlew :app:assembleRelease` still produces an installable
    // APK for local smoke tests. Tag-time build with a real keystore
    // is the release captain's responsibility (see §8 in
    // `docs/release-checklist.md`).
    signingConfigs {
        create("release") {
            val ksProps = rootProject.file("~/.gradle/meshlit-release.properties")
            if (ksProps.exists()) {
                val props = Properties().apply {
                    ksProps.inputStream().use { load(it) }
                }
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    // Phase 1.0 — Lean APK. The bundled `smollm2-360m-instruct-q8_0.gguf`
    // is ~369 MB and dominates the debug APK size (~450 MB before this
    // change). Debug installs skip it; the user downloads the model on
    // first launch via `BundledModelInstaller` -> Models screen's
    // "Download starter model" flow. The `release` build still ships
    // the GGUF for one-shot installs in environments without network
    // access — flip `bundledModel` to `true` to restore the bundle.
    //
    // The flag is read at config time so the asset is excluded from
    // `mergeAssets` (no APK file, no AAsset entry) rather than
    // stripped post-merge. The README.md next to the GGUF is kept in
    // both variants so anyone poking at `assets/models/` sees the
    // restore instructions.
    //
    // Implementation: with `productFlavors` enabled the
    // `sourceSets { getByName("main") { assets.excludes += ... } }`
    // shape used by the v1 build no longer resolves through the
    // Kotlin DSL (AGP 8.x deprecated `AndroidSourceSet`). The same
    // effect is achieved via `packagingOptions.resources.excludes`
    // below — Gradle skips the asset at packaging time, so the
    // APK never contains the GGUF file.
    val bundledModel = false
    packaging {
        if (!bundledModel) {
            resources {
                excludes += setOf(
                    "assets/models/smollm2-360m-instruct-q8_0.gguf",
                )
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // OkHttp 4.x ↔ 5.x is not binary compatible. OpenTelemetry's OTLP
    // sender pulls OkHttp 5.3.2 transitively, but the project pins
    // OkHttp to 4.12.0 (the last 4.x line — the only line
    // MockWebServer 4.12.0 understands). Force resolution to 4.12.0
    // so the unit test runtime classpath is consistent.
    configurations.all {
        resolutionStrategy {
            force("com.squareup.okhttp3:okhttp:4.12.0")
            force("com.squareup.okhttp3:okhttp-android:4.12.0")
            force("com.squareup.okhttp3:okhttp-jvm:4.12.0")
            force("com.squareup.okhttp3:mockwebserver:4.12.0")
            force("com.squareup.okhttp3:logging-interceptor:4.12.0")
        }
    }
}

dependencies {
    // Project modules (app consumes the orchestration facade)
    implementation(project(":core-orchestration"))
    implementation(project(":core-common"))
    implementation(project(":core-config"))
    implementation(project(":core-flags"))
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
    implementation(project(":core-bootstrap"))
    implementation(project(":core-registry"))
    implementation(project(":core-lifecycle"))
    implementation(project(":core-probe"))
    implementation(project(":core-role"))
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
    // V2 UI — `currentWindowAdaptiveInfo` lives in `material3-adaptive`,
    // `WindowWidthSizeClass` lives in `androidx.window.core`. Both
    // are required by `MeshlitAppV2.kt` to branch the drawer between
    // `PermanentNavigationDrawer` (tablets) and `ModalNavigationDrawer`
    // (phones) per the design brief.
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.window.core)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Logging — slf4j-api alone gives us NOP; logback-android binds it
    // to logcat with the configured pattern below. The binding is
    // required to diagnose Application.onCreate startup paths
    // (the Meshlit app has ~20 synchronous install steps before
    // MainActivity runs; without logcat visibility the source of
    // any >5s hang is invisible).
    implementation(libs.slf4j.api)
    implementation(libs.logback.android)

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
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    // Robolectric — in-VM Android runtime for TermuxRunCommandDispatcherTest
    // and other Context-dependent unit tests. The runner materialises a
    // ShadowApplication so the dispatcher can call `Context.filesDir`
    // without spinning up an emulator.
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
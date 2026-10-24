import java.util.Properties
import java.io.File
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Operator-owned signing properties are never generated or committed by this build.
val meshlitSigningFile = providers.gradleProperty("meshlit.signingProperties").orNull?.let { rootProject.file(it) }
    ?: File(System.getProperty("user.home"), ".gradle/meshlit-release.properties")

android {
    signingConfigs {
        getByName("debug") {
            providers.gradleProperty("meshlit.debugKeystore").orNull?.let { storeFile = rootProject.file(it) }
        }
        create("release") {
            if (meshlitSigningFile.exists()) {
                val props = Properties().apply { meshlitSigningFile.inputStream().use { load(it) } }
                storeFile = rootProject.file(requireNotNull(props.getProperty("storeFile")))
                storePassword = requireNotNull(props.getProperty("storePassword"))
                keyAlias = requireNotNull(props.getProperty("keyAlias"))
                keyPassword = requireNotNull(props.getProperty("keyPassword"))
            }
        }
    }
    packaging { jniLibs.useLegacyPackaging = true }
    namespace = "com.meshlit"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.meshlit"
        buildConfigField("boolean", "PLAY_REVIEW", "false")
        buildConfigField("boolean", "CORE_CANDIDATE", "false")
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
        versionCode = 42
        versionName = "0.2.8-cluster-studio"

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
            // Keep this preview version in step with the release evidence.
            // so Play Store and F-Droid see a fresh artifact.
            versionName = "2.0.0-v2build45"
            versionCode = 45
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
            // Production release is unsigned without explicit operator keys; never fall back to debug.
            signingConfig = if (meshlitSigningFile.exists()) signingConfigs.getByName("release") else null
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

    // Review-only distribution: narrower manifest, no cross-app autonomous service.
    // Debug-signed for installation; neither this APK nor its AAB is Play approved.
    buildTypes.create("playReview") {
        initWith(buildTypes.getByName("debug"))
        matchingFallbacks += listOf("debug")
        applicationIdSuffix = ".playreview"
        versionNameSuffix = "-play-review"
        isDebuggable = false
        signingConfig = signingConfigs.getByName("debug")
        buildConfigField("boolean", "PLAY_REVIEW", "true")
    }

    // Separate package and app data; not a claim of production qualification.
    buildTypes.create("productionCandidate") {
        initWith(buildTypes.getByName("playReview"))
        matchingFallbacks += listOf("debug")
        applicationIdSuffix = ".production"
        versionNameSuffix = "-core-candidate"
        buildConfigField("boolean", "CORE_CANDIDATE", "true")
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

    // Errors block builds; existing warning cleanup is tracked separately.
    lint {
        abortOnError = true
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
            // Preserve both SSH modules' metadata, alongside their offline licence assets.
            merges += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/NOTICE")
        }
    }


}

dependencies {
    implementation(libs.androidx.security.crypto)
    implementation(project(":shared-workspace"))
    // Project modules (app consumes the orchestration facade)
    implementation(project(":core-orchestration"))
    implementation(project(":core-common"))
    implementation(project(":core-config"))
    implementation(project(":core-flags"))
    implementation(project(":core-trust"))
    implementation(project(":core-discovery"))
    implementation(project(":core-inference"))
    implementation(project(":core-mcp"))
    implementation(project(":core-federation"))
    implementation(project(":core-gpu"))
    implementation(project(":core-hyperl"))
    implementation(project(":core-gibberlink"))
    implementation(project(":core-cloud-mcp"))
    implementation(project(":core-training"))
    implementation(project(":core-files"))
    implementation(project(":core-ssh"))
    implementation(project(":core-firewall"))
    implementation(project(":core-guardrails"))
    implementation(project(":core-tunnel"))
    implementation(project(":core-users"))
    implementation(project(":core-terminal"))
    implementation(project(":core-sandbox"))
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
    implementation(libs.androidx.lifecycle.runtime.compose)
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
    // Parse replies to native Compose blocks; never execute model HTML/scripts.
    implementation(libs.commonmark)
    implementation(libs.commonmark.tables)
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
    implementation(libs.nanohttpd.core)
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")

    // Render identity QRs locally and scan live camera frames with
    // EasyQRScan's in-app CameraX/ML Kit surface. The older Play
    // Services scanner remains for legacy worker/Devices surfaces.
    implementation(libs.zxing.core)
    implementation(libs.easyqrscan.scanner)
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
// Restricted build environments may choose a writable Robolectric dependency home.
val meshlitTestHome = providers.gradleProperty("meshlit.testHome")
tasks.withType<Test>().configureEach {
    if (meshlitTestHome.isPresent) systemProperty("user.home", meshlitTestHome.get())
}

val meshlitRobolectricDir=providers.gradleProperty("meshlit.robolectricDir")
tasks.withType<Test>().configureEach {
    if(meshlitRobolectricDir.isPresent){
        systemProperty("robolectric.offline","true")
        systemProperty("robolectric.dependency.dir",meshlitRobolectricDir.get())
    }
}

// A build must not silently omit its real starter model. Preparation is explicit
// and reproducible, rather than an unpinned download during Gradle configuration.
abstract class VerifyBundledModel:DefaultTask() {
    @get:InputFile abstract val manifestFile:RegularFileProperty
    @get:InputDirectory abstract val assetDirectory:DirectoryProperty
    @TaskAction fun verify() {
        val manifest=groovy.json.JsonSlurper().parse(manifestFile.get().asFile) as Map<*,*>
        val file=assetDirectory.file(manifest["filename"] as String).get().asFile
        check(file.isFile) { "Missing bundled model. Run python3 scripts/prepare-bundled-model.py from the repository root." }
        check(file.length()==(manifest["sizeBytes"] as Number).toLong()) { "Bundled model size mismatch. Prepare the model again." }
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer=ByteArray(1024*1024)
            while(true) { val n=input.read(buffer);if(n<0) break;digest.update(buffer,0,n) }
        }
        val hash=digest.digest().joinToString("") { "%02x".format(it) }
        check(hash==manifest["sha256"]) { "Bundled model checksum mismatch. Prepare the model again." }
    }
}
val verifyBundledModel by tasks.registering(VerifyBundledModel::class) {
    manifestFile.set(layout.projectDirectory.file("src/main/assets/models/bundled-model.json"))
    assetDirectory.set(layout.projectDirectory.dir("src/main/assets/models"))
}
// Consent must display the exact reviewed documents, including material data-flow changes.
abstract class VerifyOfflinePolicies:DefaultTask() {
    @get:InputFile abstract val termsDocument:RegularFileProperty
    @get:InputFile abstract val privacyDocument:RegularFileProperty
    @get:InputFile abstract val termsAsset:RegularFileProperty
    @get:InputFile abstract val privacyAsset:RegularFileProperty
    @get:InputFile abstract val agreementSource:RegularFileProperty
    @TaskAction fun verify() {
        val version=Regex("const val VERSION\\s*=\\s*\"([^\"]+)\"")
            .find(agreementSource.get().asFile.readText())?.groupValues?.get(1)
            ?: error("Review the legal agreement version before building.")
        listOf(termsDocument.get().asFile to termsAsset.get().asFile,
            privacyDocument.get().asFile to privacyAsset.get().asFile).forEach { (document,asset) ->
            check(document.readBytes().contentEquals(asset.readBytes())) {
                "Offline agreement differs from ${document.name}. Review and synchronize the legal assets before building."
            }
            check(document.readLines().getOrNull(1)?.startsWith("Version $version ·") == true) {
                "Agreement acceptance version differs from ${document.name}. Review consent renewal before building."
            }
        }
    }
}
val verifyOfflinePolicies by tasks.registering(VerifyOfflinePolicies::class) {
    termsDocument.set(rootProject.layout.projectDirectory.file("docs/TERMS_OF_USE.md"))
    privacyDocument.set(rootProject.layout.projectDirectory.file("docs/PRIVACY_POLICY.md"))
    termsAsset.set(layout.projectDirectory.file("src/main/assets/legal/terms.txt"))
    privacyAsset.set(layout.projectDirectory.file("src/main/assets/legal/privacy.txt"))
    agreementSource.set(layout.projectDirectory.file("src/main/kotlin/com/meshlit/legal/LegalAgreementStore.kt"))
}
tasks.named("preBuild") { dependsOn(verifyBundledModel,verifyOfflinePolicies) }


// Original lab companions shipped as data; installation requires an explicit human VM action.
abstract class GenerateLabAssets:DefaultTask() {
    @get:InputFile abstract val cyber:RegularFileProperty
    @get:InputFile abstract val packages:RegularFileProperty
    @get:OutputDirectory abstract val outputDirectory:DirectoryProperty
    @TaskAction fun generate() {
        val lab=outputDirectory.dir("lab").get().asFile
        check(lab.isDirectory || lab.mkdirs())
        listOf(cyber.get().asFile,packages.get().asFile).forEach { source ->
            check(source.length() in 1..131072) { "Invalid bundled lab companion size" }
            source.copyTo(File(lab,source.name),overwrite=true)
        }
    }
}
val generateLabAssets by tasks.registering(GenerateLabAssets::class) {
    cyber.set(rootProject.layout.projectDirectory.file("companions/cyber/meshlit_cyber.py"))
    packages.set(rootProject.layout.projectDirectory.file("companions/cyber/meshlit_packages.py"))
    outputDirectory.set(layout.buildDirectory.dir("generated/labAssets"))
}
androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(generateLabAssets,GenerateLabAssets::outputDirectory)
}
androidComponents.beforeVariants(androidComponents.selector().withBuildType("productionCandidate")) { variant ->
    checkNotNull(variant.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]).enable = true
}

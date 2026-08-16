plugins {
    id("com.android.library")
}

android {
    namespace = "com.meshlit.stable_diffusion"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        ndk {
            // Ship for every Android ABI the project supports:
            //   - arm64-v8a   : modern phones (95%+ of new devices)
            //   - armeabi-v7a : older 32-bit phones still in the wild
            //   - x86_64      : emulators on Apple Silicon / Linux CI
            //   - x86         : legacy emulators / x86 Chromebooks
            // The libmeshlit_sd.so is a JNI stub body in MVP1 — when
            // real stable-diffusion.cpp + ggml land in Phase 2, gate
            // these via a Gradle property so debug APKs stay small.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-fvisibility=hidden", "-fno-rtti", "-fno-exceptions", "-O2", "-DNDEBUG")
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // B-029: AGP's transform cache ships stale `libmeshlit_sd.so` in
    // the AAR. Editing any `.cpp` produces a fresh `.so` under
    // build/intermediates/cxx/ but the AAR still contains the previous
    // one. Force every CMake-related task to re-run so the freshly-
    // built `.so` is always picked up by the packaging pipeline.
    // Same fix the `:core-terminal` module uses for `vt_native.so`.
    tasks.matching {
        it.name.startsWith("buildCMake") ||
            it.name.startsWith("configureCMake") ||
            it.name.startsWith("externalNativeBuild")
    }.configureEach {
        outputs.upToDateWhen { false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // JVM unit tests can't reach the Android `Log` class. Returning
    // default values makes the JNI-stub `System.loadLibrary("meshlit_sd")`
    // a no-op (returns null) instead of throwing — the typed-failure
    // paths in the engine wrappers rely on this. Mirrors the setup
    // in `:core-inference`.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-inference"))
    implementation(libs.kotlinx.coroutines.core)
    implementation("androidx.annotation:annotation:1.9.1")

    // JVM unit tests — covered by the testOptions block above so the
    // engine's `System.loadLibrary` and `Log.*` calls don't throw on
    // the JVM. Mirrors the setup in `:core-inference`.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

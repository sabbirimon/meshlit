plugins {
    id("com.android.library")
}

android {
    namespace = "com.meshlit.terminal.nativ"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        ndk {
            // Ship for every Android ABI the project supports:
            //   - arm64-v8a   : modern phones (95%+ of new devices)
            //   - armeabi-v7a : older 32-bit phones still in the wild
            //   - x86_64      : emulators on Apple Silicon / Linux CI
            //   - x86         : legacy emulators / x86 Chromebooks
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-fvisibility=hidden", "-fno-rtti", "-fno-exceptions", "-O3", "-DNDEBUG")
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
}
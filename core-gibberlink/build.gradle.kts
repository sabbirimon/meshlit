plugins { alias(libs.plugins.android.library) }
android {
    namespace = "com.meshlit.core.gibberlink"
    compileSdk = 37
    ndkVersion = "28.2.13676358"
    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    testOptions { unitTests.all { test ->
        providers.gradleProperty("gibberlinkHostLibraryDir").orNull?.let {
            test.systemProperty("java.library.path", it); test.systemProperty("gibberlink.host.test", "true")
        }
    } }
}
dependencies { testImplementation(libs.junit) }

plugins {
    `kotlin-dsl`
}

group = "com.meshlit.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidLibraryConvention") {
            id = "meshlit.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplicationConvention") {
            id = "meshlit.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibraryComposeConvention") {
            id = "meshlit.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("jvmLibraryConvention") {
            id = "meshlit.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
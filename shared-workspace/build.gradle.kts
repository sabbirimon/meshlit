plugins {
    id("org.jetbrains.kotlin.multiplatform")
}
kotlin {
    jvmToolchain(21)
    jvm { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
        jvmMain.dependencies {
            implementation(libs.commonmark)
            implementation(libs.commonmark.tables)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

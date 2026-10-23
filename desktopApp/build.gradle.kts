import org.jetbrains.compose.desktop.application.dsl.TargetFormat
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.compose")
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":core-peer-security"))
    implementation(project(":shared-workspace"))
    implementation(project(":desktop-hyperl"))
    implementation(project(":desktop-ssh"))
    implementation(project(":desktop-engine"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.zxing.core)
    implementation("com.github.oshi:oshi-core:7.7.0")
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}
compose.desktop {
    application {
        mainClass = "com.meshlit.desktop.MainKt"
        providers.gradleProperty("meshlit.packagingJdk").orNull?.let { javaHome = it }
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "MeshlitPreview"
            packageVersion = "1.0.0"
            modules("java.instrument", "java.logging", "java.net.http", "java.prefs", "java.rmi", "java.security.jgss", "java.management", "jdk.management", "jdk.crypto.ec", "jdk.unsupported")
            description = "Experimental Meshlit desktop studio with bundled offline CPU inference and optional host connections"
            vendor = "Sabbir Hassan Imon"
        }
    }
}
tasks.register<JavaExec>("cli") {
    group = "application"
    description = "Authenticated, bounded Meshlit host client; use --args for CLI commands"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.meshlit.desktop.CliKt")
    standardInput = System.`in`
}
tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
    systemProperty("skiko.data.path", layout.buildDirectory.dir("skiko-test-cache").get().asFile.absolutePath)
    providers.gradleProperty("meshlit.previewDir").orNull?.let { systemProperty("meshlit.previewDir", it) }
}

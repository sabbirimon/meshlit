import java.nio.file.Files
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
            packageVersion = providers.gradleProperty("meshlit.packageVersion").getOrElse("2.0.45")
            modules("java.instrument", "java.logging", "java.net.http", "java.prefs", "java.rmi", "java.security.jgss", "java.management", "jdk.management", "jdk.crypto.ec", "jdk.unsupported")
            description = "Experimental Meshlit Studio; runtime availability depends on the installed package"
            vendor = "Sabbir Hassan Imon"
            macOS { iconFile.set(project.file("distribution/icons/meshlit.icns")) }
            windows { iconFile.set(project.file("distribution/icons/meshlit.ico")) }
            linux { iconFile.set(project.file("distribution/icons/meshlit.png")) }
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

// An open JVM must not read jars that a later compile/test replaces in place.
// Snapshot each launch separately; another run cannot mutate its classpath.
tasks.withType<JavaExec>().configureEach {
    if (name == "run") {
        notCompatibleWithConfigurationCache("Per-launch immutable development classpath")
        val isolatedClasspath = objects.fileCollection()
        doFirst {
            temporaryDir.mkdirs()
            val snapshot = Files.createTempDirectory(temporaryDir.toPath(), "runtime-").toFile()
            val isolated = classpath.files.filter { it.exists() }.mapIndexed { index, source ->
                val target = snapshot.resolve("$index-${source.name}")
                if (source.isDirectory) source.copyRecursively(target, overwrite = false)
                else source.copyTo(target, overwrite = false)
                target
            }
            setClasspath(isolatedClasspath.from(isolated))
        }
        doLast { classpath.files.firstOrNull()?.parentFile?.deleteRecursively() }
    }
}

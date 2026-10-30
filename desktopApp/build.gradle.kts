import java.util.Properties
import java.time.Instant
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
    val browserOs = when { System.getProperty("os.name").startsWith("Mac") -> "mac"; System.getProperty("os.name").startsWith("Windows") -> "win"; else -> "linux" }
    val browserPlatform = browserOs + if(System.getProperty("os.arch") in setOf("aarch64", "arm64")) "-aarch64" else ""
    listOf("base", "graphics", "controls", "media", "swing", "web").forEach {
        implementation("org.openjfx:javafx-$it:21.0.13:$browserPlatform")
    }
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
        providers.gradleProperty("meshlit.devResources").orNull?.let {
            require(project.file(it).isDirectory) { "Developer resources directory is missing" }
            jvmArgs("-Dmeshlit.dev.resources=" + project.file(it).absolutePath)
            jvmArgs("-Djava.library.path=" + project.file(it).resolve("local").absolutePath)
        }
        providers.gradleProperty("meshlit.packagingJdk").orNull?.let { javaHome = it }
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Meshlit"
            packageVersion = providers.gradleProperty("meshlit.packageVersion").getOrElse("2.0.45")
            modules("jdk.jfr", "jdk.xml.dom", "jdk.httpserver", "jdk.jsobject", "java.scripting", "java.xml", "jdk.unsupported.desktop", "java.instrument", "java.logging", "java.net.http", "java.prefs", "java.rmi", "java.security.jgss", "java.management", "jdk.management", "jdk.crypto.ec", "jdk.unsupported")
            description = "Experimental Meshlit Studio; runtime availability depends on the installed package"
            vendor = "Sabbir Hassan Imon"
            macOS { bundleID = "com.meshlit.desktop"; iconFile.set(project.file("distribution/icons/meshlit.icns")) }
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
        providers.gradleProperty("meshlit.workspace").orNull?.let { systemProperty("meshlit.workspace", it) }
        if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
            jvmArgs("-Xdock:name=Meshlit")
            systemProperty("apple.awt.application.name", "Meshlit")
        }
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

// Retain browser and other bundled component notices in every platform artifact.
tasks.named<org.gradle.language.jvm.tasks.ProcessResources>("processResources") {
    from("distribution/notices") { into("META-INF/meshlit/licenses") }
}

val studioBuildMetadata = layout.buildDirectory.dir("generated/studioIdentity")
val generateStudioIdentity by tasks.registering {
    notCompatibleWithConfigurationCache("Reads live Git identity for each desktop distribution")
    outputs.dir(studioBuildMetadata)
    outputs.upToDateWhen { false } // Every compiled distribution has a distinct build time.
    doLast {
        fun git(vararg args: String): String = providers.exec {
            workingDir(rootProject.projectDir)
            commandLine("git", *args)
        }.standardOutput.asText.get().trim()
        val file = studioBuildMetadata.get().file("studio-build.properties").asFile
        file.parentFile.mkdirs()
        val metadata = Properties().apply {
            setProperty("version", providers.gradleProperty("meshlit.packageVersion").getOrElse("2.0.45") + "-" + providers.gradleProperty("meshlit.channel").getOrElse("dev"))
            setProperty("revision", git("rev-parse", "HEAD"))
            setProperty("dirty", git("status", "--porcelain", "--untracked-files=normal").isNotEmpty().toString())
            setProperty("builtAt", Instant.now().toString())
        }
        file.outputStream().use { metadata.store(it, "Generated Meshlit build identity") }
    }
}
tasks.named<org.gradle.language.jvm.tasks.ProcessResources>("processResources") {
    dependsOn(generateStudioIdentity)
    from(studioBuildMetadata)
}

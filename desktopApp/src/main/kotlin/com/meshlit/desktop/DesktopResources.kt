package com.meshlit.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.serialization.json.jsonPrimitive

/** One resource directory for packaged and explicitly configured development runtimes. */
internal fun desktopResourceDirectory(property: (String) -> String? = { System.getProperty(it) }): Path = Path.of(
    property("meshlit.dev.resources") ?: property("compose.application.resources.dir") ?: "desktopApp/build/local-resources"
)

/** Check the same native file that the shared loadLibrary boundary will select. */
internal fun verifiedHyperLLibrary(resources: Path = desktopResourceDirectory(),
    searchPath: String = listOfNotNull(System.getProperty("sun.boot.library.path"), System.getProperty("java.library.path")).joinToString(File.pathSeparator)): Path {
    val library = resources.resolve("local/${System.mapLibraryName("meshlit_hyperl")}")
    check(Files.isRegularFile(library, LinkOption.NOFOLLOW_LINKS) && Files.size(library) <= 16 * 1024 * 1024) { "Bundled HyperL library unavailable or oversized" }
    val manifestBytes = Files.newInputStream(resources.resolve("local/hyperl-engine.json"), LinkOption.NOFOLLOW_LINKS).use { it.readNBytes(262145) }
    require(manifestBytes.size <= 262144) { "HyperL manifest exceeds its limit" }
    val manifest = boundedJson(manifestBytes.decodeToString(throwOnInvalidSequence = true))
    val bytes = Files.newInputStream(library, LinkOption.NOFOLLOW_LINKS).use { it.readNBytes(16 * 1024 * 1024 + 1) }.also { require(it.size <= 16 * 1024 * 1024) }
    val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    check(actual == manifest["sha256"]!!.jsonPrimitive.content) { "HyperL native checksum mismatch" }
    val selected = searchPath.split(File.pathSeparatorChar).map { Path.of(it.ifEmpty { "." }).resolve(library.fileName) }
        .firstOrNull { Files.exists(it) }
    check(selected != null && selected.toRealPath() == library.toRealPath()) {
        "HyperL native search path does not match verified resources. Restart with the packaged runtime or a matching java.library.path."
    }
    return library
}

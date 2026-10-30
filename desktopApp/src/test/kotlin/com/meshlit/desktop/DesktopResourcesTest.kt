package com.meshlit.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.*
import org.junit.Test

class DesktopResourcesTest {
    @Test fun desktopRuntimesShareExplicitResourceOverrideAndPackagedFallback() {
        val values = mutableMapOf("meshlit.dev.resources" to "/explicit/resources", "compose.application.resources.dir" to "/packaged/resources")
        assertEquals(Path.of("/explicit/resources"), desktopResourceDirectory { values[it] })
        values.remove("meshlit.dev.resources")
        assertEquals(Path.of("/packaged/resources"), desktopResourceDirectory { values[it] })
        values.clear()
        assertEquals(Path.of("desktopApp/build/local-resources"), desktopResourceDirectory { values[it] })
    }
    @Test fun hyperLRejectsDifferentNativeSearchCandidateAndTampering() {
        val root = Files.createTempDirectory("meshlit-hyperl-path-test")
        try {
            val resources = Files.createDirectories(root.resolve("resources/local")).parent
            val library = resources.resolve("local/${System.mapLibraryName("meshlit_hyperl")}")
            val bytes = "Integrity fixture; never executed".toByteArray()
            Files.write(library, bytes)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            Files.writeString(resources.resolve("local/hyperl-engine.json"), "{\"sha256\":\"$hash\"}")
            assertEquals(library, verifiedHyperLLibrary(resources, library.parent.toString()))
            val other = Files.createDirectories(root.resolve("other"))
            Files.write(other.resolve(library.fileName), bytes)
            assertFails { verifiedHyperLLibrary(resources, other.toString() + File.pathSeparator + library.parent) }
            assertFails { verifiedHyperLLibrary(resources, root.resolve("missing").toString()) }
            Files.writeString(library, "Changed library")
            assertFails { verifiedHyperLLibrary(resources, library.parent.toString()) }
        } finally { root.toFile().deleteRecursively() }
    }
    @Test fun workbenchUsesSavedOptionsOnlyForNewLoadsAndCapsSmallContextOutput() {
        val node = Preferences.userRoot().node("com/meshlit/test/ide-engine-" + UUID.randomUUID())
        try {
            val preferences = EnginePreferences(node)
            val first = EngineOptions(context = 512, threads = 1, batchThreads = 1)
            val second = first.copy(context = 2048)
            preferences.save(first)
            var running = false
            val loaded = mutableListOf<EngineOptions>()
            val owner = IdeEngineSession(preferences::load, { running }) { options ->
                loaded += options; running = true
                LocalSession(HostEndpoint.parse("http://127.0.0.1:12345/v1"), "fixture", options.context, EngineAdmission(1, 2, 1), "fixture")
            }
            val active = owner.load()
            assertEquals(first, loaded.single()); assertEquals(256, ideOutputBudget(active))
            preferences.save(second)
            assertSame(active, owner.load()); assertEquals(1, loaded.size)
            running = false
            val reloaded = owner.load()
            assertEquals(second, loaded.last()); assertEquals(2, loaded.size)
            assertEquals(1024, ideOutputBudget(reloaded)); assertEquals(1024, ideOutputBudget(null))
        } finally { node.removeNode() }
    }
}

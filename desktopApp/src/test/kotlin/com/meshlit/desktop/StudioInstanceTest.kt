package com.meshlit.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.*

class StudioInstanceTest {
    @Test fun onlyOneOwnerAndSecondLaunchRequestsActivation() {
        val root = Files.createTempDirectory("meshlit-instance-test")
        try {
            val first = assertNotNull(StudioInstance.acquire(root, "test build"))
            first.use {
                assertNull(StudioInstance.acquire(root, "second build"))
                assertTrue(first.consumeActivation())
                assertFalse(first.consumeActivation())
                assertTrue(Files.readString(root.resolve("instance.lock")).contains("test build"))
            }
            assertNotNull(StudioInstance.acquire(root, "next build")).close()
        } finally { root.toFile().deleteRecursively() }
    }
    @Test fun lockExcludesAnotherJvmAndRecoversAfterOwnerCloses() {
        val root = Files.createTempDirectory("meshlit-instance-process")
        fun probe(): String {
            val cp = listOf(StudioInstance::class.java, StudioInstanceProbe::class.java, Unit::class.java)
                .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }.distinct().joinToString(java.io.File.pathSeparator)
            val p = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", cp, StudioInstanceProbe::class.java.name, root.toString()).redirectErrorStream(true).start()
            assertTrue(p.waitFor(15, TimeUnit.SECONDS)); assertEquals(0, p.exitValue())
            return p.inputStream.bufferedReader().readText().trim()
        }
        try {
            assertNotNull(StudioInstance.acquire(root, "parent")).use { assertEquals("existing", probe()) }
            assertEquals("owner", probe())
        } finally { root.toFile().deleteRecursively() }
    }
}
object StudioInstanceProbe {
    @JvmStatic fun main(args: Array<String>) {
        val owner = StudioInstance.acquire(Path.of(args.single()), "child")
        println(if(owner == null) "existing" else "owner")
        owner?.close()
    }
}

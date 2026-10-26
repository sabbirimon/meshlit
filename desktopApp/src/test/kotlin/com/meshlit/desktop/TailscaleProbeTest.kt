package com.meshlit.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.prefs.Preferences
import kotlinx.coroutines.*
import kotlin.test.*
import org.junit.Assume.assumeFalse

/** Local executable protocol fixtures, not evidence of a running tailnet. */
class TailscaleProbeTest {
    private fun fixture(body: String, block: (String) -> Unit) {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        val dir = Files.createTempDirectory("vpn-cli-test")
        val file = dir.resolve("tailscale fixture")
        Files.writeString(file, "#!/bin/sh\n" + body)
        check(file.toFile().setExecutable(true))
        try { block(file.toString()) } finally { dir.toFile().deleteRecursively() }
    }
    @Test fun runsOnlyStatusArgumentsIncludingExecutablePathsWithSpaces() = fixture("""
        test "${'$'}#" = 2 || exit 8
        test "${'$'}1" = status || exit 9
        test "${'$'}2" = --json || exit 10
        printf '%s' '{"BackendState":"Stopped","TUN":true}'
    """.trimIndent()) { path -> runBlocking { assertEquals("Stopped", TailscaleProbe.status(path).backend) } }
    @Test fun timeoutAndCancellationTerminateOwnedProcess() = fixture("exec sleep 15") { path ->
        runBlocking {
            val started = System.nanoTime()
            assertFails { TailscaleProbe.status(path, 100) }
            assertTrue((System.nanoTime()-started)/1_000_000 < 4000)
            val job = launch { TailscaleProbe.status(path) }
            delay(100); withTimeout(4000) { job.cancelAndJoin() }
        }
    }
    @Test fun nonzeroAndMalformedOutputNeverExposeLoginTokens() {
        fixture("echo private-login-token >&2; exit 1") { path -> runBlocking {
            val error = assertFails { TailscaleProbe.status(path) }; assertFalse(error.message.orEmpty().contains("private-login-token"))
        } }
        fixture("printf '%s' '{broken}'") { path -> runBlocking { assertFails { TailscaleProbe.status(path) } } }
    }
    @Test fun oversizeOutputIsBounded() = fixture("head -c 2097153 /dev/zero") { path -> runBlocking { assertFails { TailscaleProbe.status(path) } } }
    @Test fun missingAndRelativeExecutablesAreRejected() = runBlocking<Unit> {
        assertFails { TailscaleProbe.status("tailscale") }
        assertFails { TailscaleProbe.status(Path.of(System.getProperty("java.io.tmpdir"), "missing-meshlit-cli").toString()) }
    }
    @Test fun optInAndPathPersistAndDefaultOff() {
        val node = Preferences.userRoot().node("com/meshlit/test-vpn-${System.nanoTime()}")
        try { val prefs = VpnPreferences(node); assertFalse(prefs.enabled()); prefs.save(true, "/trusted/tailscale"); assertTrue(VpnPreferences(node).enabled()); prefs.save(false, "/trusted/tailscale"); assertFalse(prefs.enabled()) }
        finally { node.removeNode(); node.flush() }
    }
}

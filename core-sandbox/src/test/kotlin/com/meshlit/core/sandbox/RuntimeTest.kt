package com.meshlit.core.sandbox

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class RuntimeTest {
    private fun temp(): File = Files.createTempDirectory("meshlit-runtime").toFile()
    private fun rootfs(root: File): File = File(root, "linux").apply {
        File(this, "bin").mkdirs(); File(this, "bin/sh").writeText("fixture")
    }
    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Test fun tokenizationPreservesArgumentsWithoutExpansion() {
        assertEquals(listOf("echo", "two words", "", "literal;cmd", "dollar\$value"),
            tokenizeCommand("echo 'two words' \"\" literal;cmd 'dollar\$value'"))
        assertTrue(runCatching { tokenizeCommand("echo 'unfinished") }.isFailure)
        assertTrue(runCatching { tokenizeCommand("echo\nnext") }.isFailure)
    }

    @Test fun rootRequiresConsentAndQuotesEveryArgument() {
        val dir = temp()
        try {
            val planner = RuntimePlanner(dir)
            val config = RuntimeConfig(RuntimeMode.ROOT, "/bin/sh")
            assertTrue(runCatching { planner.plan(config, listOf("echo", "hello")) }.isFailure)
            val plan = planner.plan(config, listOf("echo", "x'; touch /tmp/unwanted; '"), true)
            assertEquals("-c", plan.argv[1])
            assertEquals("exec " + listOf("echo", "x'; touch /tmp/unwanted; '").joinToString(" ", transform = ::shellQuote), plan.argv[2])
            val result = runBlocking { ProcessRunner().execute(plan) }
            assertEquals("x'; touch /tmp/unwanted; '", result.stdout.trim())
        } finally { dir.deleteRecursively() }
    }

    @Test fun prootAndNamespaceModesDoNotSilentlyFallBack() {
        val dir = temp()
        try {
            val rootfs = rootfs(dir)
            val planner = RuntimePlanner(dir)
            val proot = planner.plan(RuntimeConfig(RuntimeMode.PROOT, "/bin/sh", rootfs.path), listOf("/bin/sh"))
            assertEquals("-r", proot.argv[1])
            assertFalse(proot.argv.contains("-0"))
            val bwrap = planner.plan(RuntimeConfig(RuntimeMode.BUBBLEWRAP, "/bin/sh", rootfs.path), listOf("/bin/sh"))
            assertTrue(bwrap.argv.contains("--unshare-all"))
            assertFalse(bwrap.argv.contains("--share-net"))
            assertTrue(bwrap.argv.contains("--ro-bind"))
            assertTrue(runCatching { planner.plan(RuntimeConfig(RuntimeMode.PROOT, "/missing"), listOf("id")) }.isFailure)
        } finally { dir.deleteRecursively() }
    }

    @Test fun guestSshAlwaysUsesLoopbackAndVerifiedHostKey() {
        val dir = temp()
        try {
            val key = File(dir, "key").apply { writeText("fixture") }
            val known = File(dir, "known_hosts").apply { writeText("fixture") }
            val plan = RuntimePlanner(dir).plan(RuntimeConfig(RuntimeMode.VM_SSH, "/bin/sh",
                sshIdentity = key.path, sshKnownHosts = known.path), listOf("echo", "hello world"))
            assertTrue(plan.argv.contains("StrictHostKeyChecking=yes"))
            assertTrue(plan.argv.contains("ForwardAgent=no"))
            assertTrue(plan.argv.contains("meshlit@127.0.0.1"))
            assertEquals("exec 'echo' 'hello world'", plan.argv.last())
        } finally { dir.deleteRecursively() }
    }

    @Test fun processDrainsBothStreamsAndBoundsOutput() = runBlocking {
        val dir = temp()
        try {
            val plan = RuntimePlanner(dir).plan(RuntimeConfig(), listOf("/bin/sh", "-c",
                "i=0; while [ \"\$i\" -lt 3000 ]; do echo output-output-output; echo error-error-error >&2; i=\$((i+1)); done"))
            val result = ProcessRunner().execute(plan, maxBytes = 1024)
            assertEquals(0, result.exitCode)
            assertTrue(result.truncated)
            assertTrue(result.stdout.length <= 1024 && result.stderr.length <= 1024)
            assertFalse(result.timedOut)
        } finally { dir.deleteRecursively() }
    }

    @Test fun commandDeadlineDoesNotWaitForInheritedPipes() = runBlocking {
        val dir = temp()
        try {
            val plan = RuntimePlanner(dir).plan(RuntimeConfig(), listOf("/bin/sh", "-c", "sleep 2"))
            val start = System.nanoTime()
            val result = ProcessRunner().execute(plan, timeoutMs = 150)
            assertTrue(result.timedOut)
            assertTrue((System.nanoTime() - start) / 1_000_000 < 1500)
        } finally { dir.deleteRecursively() }
    }

    @Test fun checksumFailurePreservesExistingArtifact() = runBlocking {
        val dir = temp()
        try {
            val store = ArtifactStore(File(dir, "artifacts"))
            val source = File(dir, "source").apply { writeText("trusted") }
            val target = store.install(source, "disk.qcow2", sha(source.readBytes()))
            source.writeText("modified")
            assertTrue(runCatching { store.install(source, "disk.qcow2", "0".repeat(64), replace = true) }.isFailure)
            assertEquals("trusted", target.readText())
            assertTrue(store.verify(target, sha("trusted".toByteArray())))
            assertTrue(runCatching { store.install(source, "../escape", sha(source.readBytes())) }.isFailure)
        } finally { dir.deleteRecursively() }
    }

    @Test fun vmBindsControlPortsLocallyAndRestrictsNetworking() {
        val dir = temp()
        try {
            val disk = File(dir, "disk.qcow2").apply { writeText("fixture") }
            val args = VmConfig("/bin/sh", disk.path, GuestArchitecture.X86_64, enableDesktop = true).argv()
            assertTrue(args.contains("-snapshot"))
            assertTrue(args.contains("user,id=net0,restrict=on,hostfwd=tcp:127.0.0.1:2222-:22"))
            assertTrue(args.contains("127.0.0.1:1"))
            assertFalse(args.any { "0.0.0.0" in it })
            assertTrue(runCatching { VmConfig("/bin/sh", disk.path, GuestArchitecture.X86_64, memoryMb = 8192).argv() }.isFailure)
        } finally { dir.deleteRecursively() }
    }
}

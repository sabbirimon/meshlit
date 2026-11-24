package com.meshlit.desktop

import java.nio.file.Files
import kotlin.test.*

class DesktopCredentialsTest {
    private val host = HostEndpoint.parse("https://example.invalid/v1")
    private val password = "test-only credential vault password".toCharArray()
    @Test fun encryptedRestartAndExactBinding() {
        val dir = Files.createTempDirectory("credential-test")
        val vault = DesktopCredentials(dir)
        try {
            vault.unlock(password); vault.save(host, "test-bearer-secret")
            val lease = vault.lease(host)
            assertEquals("test-bearer-secret", vault.resolve(lease, host))
            assertFails { vault.resolve(lease, HostEndpoint.parse("https://other.invalid/v1")) }
            assertFails { vault.resolve(lease, HostEndpoint.parse("https://example.invalid/v1", HostProtocol.MESHLIT)) }
            assertFalse(Files.readAllBytes(dir.resolve("credentials.mcv")).toString(Charsets.UTF_8).contains("test-bearer-secret"))
            vault.lock(); assertFails { vault.resolve(lease, host) }
            assertFails { vault.unlock("wrong password long enough".toCharArray()) }
            vault.unlock(password)
            assertEquals("test-bearer-secret", vault.resolve(vault.lease(host), host))
            assertFails { vault.resolve(lease, host) }
        } finally { vault.lock(); dir.toFile().deleteRecursively() }
    }
    @Test fun replacementRemovalAndSingleOwner() {
        val dir = Files.createTempDirectory("credential-owner")
        val vault = DesktopCredentials(dir); val other = DesktopCredentials(dir)
        try {
            vault.unlock(password); vault.save(host, "first")
            assertFails { other.unlock(password) }
            val old = vault.lease(host)
            vault.save(host, "second"); assertFails { vault.resolve(old, host) }
            val current = vault.lease(host)
            vault.remove(host); assertFails { vault.resolve(current, host) }; assertFails { vault.lease(host) }
            vault.lock(); other.unlock(password); assertFails { other.lease(host) }
        } finally { vault.lock(); other.lock(); dir.toFile().deleteRecursively() }
    }
    @Test fun corruptionAndFailedWritesNeverExposePriorCredentials() {
        val dir = Files.createTempDirectory("credential-failure")
        val vault = DesktopCredentials(dir)
        try {
            vault.unlock(password); vault.save(host, "first")
            val lease = vault.lease(host)
            Files.delete(dir.resolve("credentials.mcv"))
            Files.createDirectory(dir.resolve("credentials.mcv"))
            assertFails { vault.save(host, "second") }
            assertFalse(vault.isUnlocked()); assertFails { vault.resolve(lease, host) }
            Files.delete(dir.resolve("credentials.mcv"))
            Files.write(dir.resolve("credentials.mcv"), byteArrayOf(1, 2, 3))
            repeat(2) { assertFails { vault.unlock(password) }; assertFalse(vault.isUnlocked()) }
            assertContentEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(dir.resolve("credentials.mcv")))
        } finally { vault.lock(); dir.toFile().deleteRecursively() }
    }
    @Test fun authenticatedTamperIsRejectedWithoutReplacingTheFile() {
        val dir = Files.createTempDirectory("credential-tamper")
        val vault = DesktopCredentials(dir)
        try {
            vault.unlock(password); vault.save(host, "test-only-secret"); vault.lock()
            val file = dir.resolve("credentials.mcv")
            val bytes = Files.readAllBytes(file)
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            Files.write(file, bytes)
            assertFails { vault.unlock(password) }
            assertFalse(vault.isUnlocked()); assertContentEquals(bytes, Files.readAllBytes(file))
        } finally { vault.lock(); dir.toFile().deleteRecursively() }
    }

}

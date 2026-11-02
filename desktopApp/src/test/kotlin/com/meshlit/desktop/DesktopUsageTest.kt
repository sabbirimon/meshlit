package com.meshlit.desktop

import com.meshlit.workspace.*
import java.nio.file.Files
import kotlin.test.*

class DesktopUsageTest {
    @Test fun connectionDocumentIsEncryptedPurposeBoundAndSessionBound() {
        val dir=Files.createTempDirectory("connection-vault");val vault=DesktopUsage(dir)
        val pass="connection test passphrase".toCharArray()
        try {
            vault.unlock(pass);val epoch=vault.teamEpoch()
            vault.saveDeviceConnectionDocument("private connection approvals",epoch)
            assertEquals("private connection approvals",vault.deviceConnectionDocument())
            assertFalse(Files.readAllBytes(dir.resolve("device-connections.mul")).toString(Charsets.UTF_8).contains("private connection approvals"))
            vault.lock();vault.unlock(pass)
            assertFails{vault.saveDeviceConnectionDocument("stale",epoch)}
            Files.copy(dir.resolve("device-connections.mul"),dir.resolve("agent-teams.mul"))
            assertFails{vault.teamDocument()}
        }finally{vault.lock();dir.toFile().deleteRecursively()}
    }
    @Test fun encryptedHistoryReopensAndRecoversWithoutPlaintextOrDoubleOwner() {
        val dir = Files.createTempDirectory("usage-test")
        val vault = DesktopUsage(dir); val other = DesktopUsage(dir)
        val password = "a test usage passphrase".toCharArray()
        try {
            vault.unlock(password)
            val id = vault.journal().begin(UsageLedger.route("https://private.invalid/v1", "private-model"), "private-model", UsagePrice(), 100, 100)
            assertFails { other.unlock(password) }
            assertFalse(Files.readAllBytes(dir.resolve("history.mul")).toString(Charsets.UTF_8).contains("private-model"))
            vault.lock(); assertFails { other.unlock("wrong password long enough".toCharArray()) }
            other.unlock(password)
            val attempt = other.journal().snapshot().attempts.single()
            assertEquals(id, attempt.id); assertEquals("cancelled", attempt.status); assertNull(attempt.cost)
        } finally { vault.lock(); other.lock(); dir.toFile().deleteRecursively() }
    }
    @Test fun damagedVaultDoesNotResetAndReleasesOwnership() {
        val dir = Files.createTempDirectory("usage-corrupt")
        val vault = DesktopUsage(dir)
        try {
            Files.write(dir.resolve("history.mul"), byteArrayOf(1, 2, 3))
            repeat(2) { assertFails { vault.unlock("a valid test passphrase".toCharArray()) } }
            assertContentEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(dir.resolve("history.mul")))
            assertFalse(vault.isUnlocked())
        } finally { vault.lock(); dir.toFile().deleteRecursively() }
    }
    @Test fun teamDocumentIsEncryptedPurposeBoundAndRejectsOldSessions() {
        val dir=Files.createTempDirectory("team-vault")
        val vault=DesktopUsage(dir);val pass="test team passphrase".toCharArray()
        try{
            vault.unlock(pass);val epoch=vault.teamEpoch()
            vault.saveTeamDocument("private owner task",epoch)
            assertEquals("private owner task",vault.teamDocument())
            assertFalse(Files.readAllBytes(dir.resolve("agent-teams.mul")).toString(Charsets.UTF_8).contains("private owner task"))
            vault.lock();assertFails{vault.teamDocument()};vault.unlock(pass)
            assertEquals("private owner task",vault.teamDocument())
            assertFails{vault.saveTeamDocument("old worker",epoch)}
            val bytes=Files.readAllBytes(dir.resolve("agent-teams.mul"));bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();Files.write(dir.resolve("agent-teams.mul"),bytes)
            assertFails{vault.teamDocument()}
        }finally{vault.lock();dir.toFile().deleteRecursively()}
    }
}

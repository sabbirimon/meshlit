package com.meshlit.desktop

import com.meshlit.workspace.*
import java.nio.file.Files
import kotlinx.serialization.json.*
import kotlin.test.*

class IdeUsageSessionTest {
    @Test fun localSessionUsesMemoryWithoutCreatingVaultOrReadingExistingHistory() {
        val root = Files.createTempDirectory("ide-session-usage")
        val directory = root.resolve("not-created")
        val vault = DesktopUsage(directory)
        val session = IdeUsageSession(vault)
        try {
            assertEquals("session", session.startSession())
            session.acquire(true).use { lease ->
                val id = lease.ledger.begin(UsageLedger.route("local:fixture", "local"), "local", UsagePrice(local = true), 10, 10)
                lease.ledger.finish(id, "complete", 8, 3)
            }
            assertFalse(Files.exists(directory))
            val view = session.view()
            assertEquals("session", view["usageMode"]!!.jsonPrimitive.content)
            assertFalse(view["persistent"]!!.jsonPrimitive.boolean)
            assertEquals(1, view["requests"]!!.jsonPrimitive.int)
            assertEquals("session", session.startSession())
            assertEquals(1, session.view()["requests"]!!.jsonPrimitive.int)
            assertFails { session.acquire(false) }
            assertFails { session.protectedLedger() }
            assertFails { vault.teamEpoch() }
            assertFails { vault.deviceConnectionDocument() }
            session.lock()
            assertFails { session.acquire(true) }
            session.startSession()
            assertEquals(0, session.view()["requests"]!!.jsonPrimitive.int)
            assertFalse(Files.exists(directory))
        } finally { session.lock(); root.toFile().deleteRecursively() }
    }
    @Test fun protectedLedgerIsUntouchedAndCanBeOwnedElsewhereDuringSessionChat() {
        val directory = Files.createTempDirectory("ide-existing-usage")
        val protected = DesktopUsage(directory)
        val local = IdeUsageSession(DesktopUsage(directory))
        val password = "test durable usage passphrase".toCharArray()
        try {
            protected.unlock(password)
            protected.journal().setPolicy(true, UsageBudget("USD", UsageMoney.parse("10"), UsageMoney.parse("1")), "USD")
            val before = Files.readAllBytes(directory.resolve("history.mul"))
            local.startSession()
            local.acquire(true).use { lease ->
                val id = lease.ledger.begin(UsageLedger.route("local:fixture", "local"), "local", UsagePrice(local = true), 10, 10)
                lease.ledger.finish(id, "complete", 5, 4)
            }
            assertContentEquals(before, Files.readAllBytes(directory.resolve("history.mul")))
            assertEquals(0, protected.journal().snapshot().attempts.size)
            assertFails { local.acquire(false) }
            assertFails { local.unlock(password) }
            assertEquals("locked", local.mode())
            assertContentEquals(before, Files.readAllBytes(directory.resolve("history.mul")))
            protected.lock()
            local.unlock(password)
            assertEquals("protected", local.mode())
            assertEquals("protected", local.startSession())
            assertEquals(1, local.protectedLedger().snapshot().budgets.size)
            assertTrue(local.protectedLedger().snapshot().freeOnly)
            local.acquire(false).use { lease ->
                assertFails { lease.ledger.begin(UsageLedger.route("https://fixture.invalid", "paid"), "paid", UsagePrice(), 10, 10) }
            }
        } finally { local.lock(); protected.lock(); password.fill('\u0000'); directory.toFile().deleteRecursively() }
    }
    @Test fun activeLeasePinsAccountingAndFailedUnlockDoesNotRestorePriorSession() {
        val directory = Files.createTempDirectory("ide-usage-switch")
        val session = IdeUsageSession(DesktopUsage(directory))
        try {
            session.startSession()
            val lease = session.acquire(true)
            assertFails { session.startSession() }
            assertFails { session.unlock("test durable passphrase".toCharArray()) }
            assertEquals("session", session.mode())
            val id = lease.ledger.begin(UsageLedger.route("local:fixture", "local"), "local", UsagePrice(local = true), 10, 10)
            lease.ledger.finish(id, "cancelled", null, null)
            lease.close(); lease.close()
            assertFails { session.unlock("short".toCharArray()) }
            assertEquals("locked", session.mode())
            assertFails { session.acquire(true) }
            session.startSession()
            assertFails { lease.ledger.begin(UsageLedger.route("local:fixture", "local"), "local", UsagePrice(local = true), 10, 10) }
            assertEquals(0, session.view()["requests"]!!.jsonPrimitive.int)
            assertFalse(Files.exists(directory.resolve("history.mul")))
        } finally { session.lock(); directory.toFile().deleteRecursively() }
    }
}

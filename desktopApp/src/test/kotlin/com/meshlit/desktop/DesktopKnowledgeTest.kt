package com.meshlit.desktop

import java.nio.file.Files
import kotlin.test.*
import org.junit.Test

class DesktopKnowledgeTest {
    @Test fun agentRecallRequiresAdditionalGrantAndHonorsEachNewLookup() {
        val folder = Files.createTempDirectory("meshlit-knowledge-agent-test")
        try {
            val store = DesktopKnowledge(folder)
            store.unlock("agent recall test password".toCharArray())
            val corpus = store.lockedCorpus()
            corpus.importText("decision", "local:decision", "The cobalt telescope uses an amber detector")
            corpus.setPolicy(com.meshlit.workspace.KnowledgePolicy(localChat = true, remoteChat = true))
            assertContains(store.context("telescope", false), "cobalt")
            assertEquals("", store.context("telescope", false, agent = true))
            assertEquals("", store.context("telescope", true, agent = true))
            corpus.setPolicy(corpus.snapshot().policy.copy(agents = true))
            assertContains(store.context("telescope", false, agent = true), "cobalt")
            corpus.setPolicy(corpus.snapshot().policy.copy(remoteChat = false))
            assertEquals("", store.context("telescope", true, agent = true))
            assertContains(store.context("telescope", false, agent = true), "cobalt")
            corpus.setPolicy(corpus.snapshot().policy.copy(agents = false))
            assertEquals("", store.context("telescope", false, agent = true))
            assertContains(store.context("telescope " + "x".repeat(20_000), false), "cobalt")
            store.lock()
            assertEquals("", store.context("telescope", false, agent = true))
        } finally { folder.toFile().deleteRecursively() }
    }
    @Test fun encryptedPersistenceLockAndWrongPassword() {
        val folder = Files.createTempDirectory("meshlit-knowledge-test")
        try {
            val password = "local test passphrase 123".toCharArray()
            val store = DesktopKnowledge(folder)
            store.unlock(password)
            store.lockedCorpus().importText("manual", "local:manual", "A cobalt telescope is on the moon")
            store.lockedCorpus().setPolicy(com.meshlit.workspace.KnowledgePolicy(localChat = true))
            assertContains(store.context("telescope", false), "cobalt")
            assertEquals("", store.context("telescope", true))
            assertFalse(Files.readAllBytes(folder.resolve("library.mkl")).toString(Charsets.ISO_8859_1).contains("cobalt"))
            val stale=store.lockedCorpus()
            store.lock(); assertEquals("", store.context("telescope", false))
            assertFails { store.unlock("wrong passphrase 123".toCharArray()) }
            assertFalse(store.isUnlocked())
            store.unlock(password); assertEquals(1, store.lockedCorpus().snapshot().documents.size)
            assertFails { stale.importText("stale", "local:stale", "This stale handle must not change the newly unlocked vault") }
            assertEquals(1,store.lockedCorpus().snapshot().documents.size)
            store.lock(); password.fill('\u0000')
        } finally { folder.toFile().deleteRecursively() }
    }
    @Test fun tamperingDoesNotOverwriteVault() {
        val folder = Files.createTempDirectory("meshlit-knowledge-test")
        try {
            val store = DesktopKnowledge(folder); store.unlock("local test passphrase".toCharArray()); store.lock()
            val file = folder.resolve("library.mkl"); val bytes = Files.readAllBytes(file)
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); Files.write(file, bytes)
            assertFails { store.unlock("local test passphrase".toCharArray()) }
            assertContentEquals(bytes, Files.readAllBytes(file))
        } finally { folder.toFile().deleteRecursively() }
    }
}

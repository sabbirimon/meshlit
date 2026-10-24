package com.meshlit.workspace

import kotlin.test.*

class KnowledgeCorpusTest {
    @Test fun durableImportAndSourceRetrieval() {
        var disk: String? = null
        val store = KnowledgeCorpus({ disk }, { disk = it })
        val first = store.importText("Manual", "local:manual.txt", "The telescope uses a cobalt battery.", 100)
        assertEquals(first, store.importText("Duplicate", "local:manual.txt", first.text, 200))
        val reopened = KnowledgeCorpus({ disk }, { disk = it })
        assertEquals(1, reopened.snapshot().documents.size)
        assertEquals("local:manual.txt", reopened.search("cobalt battery").single().source)
        assertTrue(reopened.search("elephant").isEmpty())
        reopened.delete(first.id)
        assertTrue(KnowledgeCorpus({ disk }, {}).snapshot().documents.isEmpty())
    }

    @Test fun grantsAreSeparateAndRevokedAtRetrieval() {
        val store = KnowledgeCorpus({ null }, {})
        store.importText("Private", "local:private.txt", "The telescope is private.")
        assertEquals("", store.context("telescope", false))
        store.setPolicy(KnowledgePolicy(localChat = true))
        assertContains(store.context("telescope", false), "[K1]")
        assertEquals("", store.context("telescope", true))
        assertEquals("", store.context("telescope", false, agent = true))
        store.setPolicy(KnowledgePolicy(remoteChat = true, agents = true))
        assertContains(store.context("telescope", true, agent = true), "untrusted document data")
        assertEquals("", store.context("telescope", false))
        store.setPolicy(KnowledgePolicy())
        assertEquals("", store.context("telescope", true, agent = true))
    }

    @Test fun failedCommitDoesNotPublishMutation() {
        var fail = false
        val store = KnowledgeCorpus({ null }, { if (fail) error("disk full") })
        val doc = store.importText("Manual", "local:a", "Original telescope")
        fail = true
        assertFails { store.delete(doc.id) }
        assertEquals(listOf(doc), store.snapshot().documents)
        assertFails { store.setPolicy(KnowledgePolicy(localChat = true)) }
        assertFalse(store.snapshot().policy.localChat)
    }

    @Test fun corruptionAndBoundsFailClosed() {
        var writes = 0
        val corrupt = KnowledgeCorpus({ "{broken" }, { writes++ })
        assertFails { corrupt.importText("new", "local:a", "text") }
        assertEquals(0, writes)
        val store = KnowledgeCorpus({ null }, {})
        assertFails { store.importText("big", "local:a", "x".repeat(KnowledgeCorpus.MAX_TEXT + 1)) }
        assertFails { store.importText("binary", "local:a", "a\u0000b") }
        assertFails { store.search("hello", 100) }
        repeat(KnowledgeCorpus.MAX_DOCUMENTS) { store.importText("$it", "local:$it", "document $it") }
        assertFails { store.importText("overflow", "local:extra", "extra") }
    }
}

package com.meshlit.chat

import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ChatDraftPersistenceTest {
    @Test fun typingDuringAnInFlightSaveCancelsQuietlyAndTheLatestDraftSaves()=runBlocking {
        val started=CompletableDeferred<Unit>()
        var warnings=0
        val old=launch {
            if(!saveChatDraft { started.complete(Unit);awaitCancellation() })warnings++
        }
        started.await()
        old.cancelAndJoin()
        assertTrue(old.isCancelled)
        assertEquals(0,warnings)
        var persisted=""
        assertTrue(saveChatDraft{persisted="the latest typed draft"})
        assertEquals("the latest typed draft",persisted)
    }

    @Test fun actualWriteFailureIsReportedAndARetryCanSucceed()=runBlocking {
        assertFalse(saveChatDraft{throw IOException("Storage unavailable")})
        assertTrue(saveChatDraft{})
    }
}

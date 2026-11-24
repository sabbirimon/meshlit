package com.meshlit.chat

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceTranscriptReviewTest {
    @Test fun onlyTheCurrentTranscriptCanBeEditedAndSubmittedOnce()=runTest {
        val review=VoiceTranscriptReview()
        var pending:PendingVoiceTranscript?=null
        val turn=async { review.await("recognized text"){pending=it} }
        runCurrent()
        val id=checkNotNull(pending).id
        assertFalse(review.answer("another-turn","wrong"))
        assertFalse(review.answer(id," "))
        assertTrue(review.answer(id," corrected text "))
        assertFalse(review.answer(id,"duplicate"))
        assertEquals("corrected text",turn.await())
        assertNull(pending)
        assertFalse(review.answer(id,"late"))
    }

    @Test fun discardedTimedOutAndStoppedTranscriptsCannotReachChat()=runTest {
        val review=VoiceTranscriptReview(100)
        var pending:PendingVoiceTranscript?=null
        fun start()=async { review.await("pending"){pending=it} }
        val discard=start();runCurrent()
        val discardedId=checkNotNull(pending).id
        assertTrue(review.answer(discardedId,null));assertNull(discard.await())
        val timeout=start();runCurrent()
        val expiredId=checkNotNull(pending).id
        advanceTimeBy(101);assertNull(timeout.await());assertNull(pending)
        val stopped=start();runCurrent()
        val stoppedId=checkNotNull(pending).id
        assertFalse(review.answer(expiredId,"stale"))
        stopped.cancelAndJoin()
        assertNull(pending)
        assertFalse(review.answer(stoppedId,"late"))
    }
}

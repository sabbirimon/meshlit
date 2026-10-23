package com.meshlit.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepeatTest {
    private val repeated="I am an emergency technician in Springfield. I work at the local center and help people recover from injuries. How was your journey?"

    @Test fun retriesCopiedAnswerForDifferentQuestion() {
        val history=listOf(ChatMessage(role="user",text="Who are you?"),ChatMessage(role="assistant",text=repeated))
        assertTrue(isUnrelatedRepeat("How do I set goals?",history,repeated))
        assertTrue(isUnrelatedRepeat("How do I set goals?",history,repeated+" Please tell me more."))
    }

    @Test fun preservesIntentionalRepeatAndShortGreeting() {
        val history=listOf(ChatMessage(role="user",text="Who are you?"),ChatMessage(role="assistant",text=repeated))
        assertFalse(isUnrelatedRepeat("Who are you?",history,repeated))
        assertFalse(isUnrelatedRepeat("How do I set goals?",history,"Hello again!"))
    }
}

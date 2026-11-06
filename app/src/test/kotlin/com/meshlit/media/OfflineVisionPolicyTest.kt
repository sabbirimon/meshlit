package com.meshlit.media

import com.meshlit.chat.ChatMessage
import com.meshlit.chat.chatPromptHistory
import org.junit.Assert.*
import org.junit.Test

class OfflineVisionPolicyTest {
    @Test fun photoQuestionAndAnswerDoNotEnterLaterPromptHistory() {
        val messages=listOf(
            ChatMessage(role="user",text="regular question"),
            ChatMessage(role="assistant",text="regular answer"),
            ChatMessage(role="user",text="private image question",isolatedPhoto=true),
            ChatMessage(role="assistant",text="private observation",isolatedPhoto=true),
        )
        assertEquals(listOf("regular question","regular answer"),chatPromptHistory(messages,2).map{it.text})
    }

    @Test fun largePairsDoNotStartWhenAvailableMemoryCannotFitTheirWeightsAndOverhead() {
        val mib=1024L*1024
        assertTrue(offlineVisionFits(256*mib,32*mib,2048*mib))
        assertFalse(offlineVisionFits(256*mib,32*mib,1024*mib))
        assertFalse(offlineVisionFits(256*mib,32*mib,0))
    }
}

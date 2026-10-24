package com.meshlit.chat
import org.junit.Assert.*
import org.junit.Test

class ChatRestorationTest {
    @Test fun photoHistoryKeepsSourceWithoutStoringPixelsOrBreakingOldMessages() {
        val json=kotlinx.serialization.json.Json
        val old=json.decodeFromString<ChatMessage>("""{"id":"old","role":"assistant","text":"hello"}""")
        assertNull(old.attachmentNote);assertNull(old.source);assertNull(old.status)
        val photo=ChatMessage(role="assistant",text="",source="Vision · model",status="Stopped · photo not answered")
        assertEquals(photo,json.decodeFromString<ChatMessage>(json.encodeToString(ChatMessage.serializer(),photo)))
    }
    @Test fun normalChatsKeepLongerRepliesAndHistoryWithoutNetworkTools() {
        val options=ChatOptions();assertEquals(1024,options.maxTokens);assertEquals(10,options.historyMessages)
        assertFalse(options.webTools);assertFalse(options.phoneTools);assertNull(options.onlineProfileId)
    }
    @Test fun rememberedOwnerSelectionWinsOverNewQualificationChat() {
        val owner=ChatConversation();val test=ChatConversation(options=ChatOptions(maxTokens=16,historyMessages=0))
        assertEquals(owner.id,restoredChatSelection(listOf(test,owner),owner.id))
        assertEquals(test.id,restoredChatSelection(listOf(test,owner),"missing"))
        assertNull(restoredChatSelection(emptyList(),owner.id))
    }
    @Test fun completedRuntimeTextIsDisplayedEvenWithoutCallbacksAndEmptyCompletionFails() {
        assertEquals("complete",completedChatText("partial","complete"))
        assertEquals("stream",completedChatText("stream",""))
        assertThrows(IllegalStateException::class.java){completedChatText("","")}
    }
}

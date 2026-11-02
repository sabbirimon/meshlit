package com.meshlit.ui.modern

import com.meshlit.chat.ChatMessage
import com.meshlit.models.LibraryModel
import org.junit.Assert.*
import org.junit.Test

class ChatModelPresentationTest {
    @Test fun fileSuffixesDoNotHideTheRealModelName() {
        assertEquals("Qwen3 0.6B",modelDisplayName("Qwen3-0.6B-Q4_K_M.gguf"))
        assertEquals("Qwen2.5 0.5B Instruct",modelDisplayName("/private/models/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf"))
        assertEquals("My বাংলা model",modelDisplayName("My বাংলা model"))
        assertEquals("Choose model",modelDisplayName(""))
    }
    @Test fun sdkLoadingPathsResolveToTheSavedLibraryName() {
        val entry=LibraryModel("url-id","Qwen3-0.6B-Q4_K_M",path="/saved/model.gguf")
        assertSame(entry,activeLibraryModel(listOf(entry),null,"runanywhere:url-id"))
        assertSame(entry,activeLibraryModel(listOf(entry),null,"/saved/model.gguf"))
        assertNull(activeLibraryModel(listOf(entry),null,"/unrelated/model.gguf"))
    }
    @Test fun replyQuotesAreEditableAndKeepAllExistingInputWithinTheDraftLimit() {
        val message=ChatMessage(role="assistant",text="An earlier answer\nwith a second line")
        val result=quotedReplyDraft(message,"My unfinished reply")!!
        assertTrue(result.startsWith("> Assistant:\n> An earlier answer\n> with a second line"))
        assertTrue(result.endsWith("My unfinished reply"))
        val longDraft="x".repeat(11900)
        val bounded=quotedReplyDraft(message.copy(text="q".repeat(5000)),longDraft)!!
        assertTrue(bounded.length<=12000);assertTrue(bounded.endsWith(longDraft))
        assertNull(quotedReplyDraft(message,"x".repeat(11999)))
    }
}

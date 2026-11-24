package com.meshlit.chat

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ConversationExportTest {
    @Test fun agentRecordsAndCodeUseOnlyVisibleTurnsAndKeepOrder() {
        val code = "~~~~js\nif (a < b) return \"✓\";\n~~~~"
        val turns = listOf(
            ChatMessage(role="system", text="hidden"),
            ChatMessage(role="user", text="~~~\nuser code\n~~~"),
            ChatMessage(role="assistant", text="Hello\n" + code + "\n~~~\nunfinished")
        )
        val rows = ConversationExport.AGENT.render(turns)!!.trimEnd().lines().map { Json.parseToJsonElement(it).jsonObject }
        assertEquals(2, rows.size)
        rows.forEachIndexed { i, row ->
            assertEquals(setOf("schema", "turn", "role", "text"), row.keys)
            assertEquals("meshlit.chat.turn.v1", row.getValue("schema").jsonPrimitive.content)
            assertEquals(i+1, row.getValue("turn").jsonPrimitive.int)
            assertEquals(turns[i+1].role, row.getValue("role").jsonPrimitive.content)
            assertEquals(turns[i+1].text, row.getValue("text").jsonPrimitive.content)
        }
        assertEquals(code + "\n", ConversationExport.CODE.render(turns))
        assertFalse(ConversationExport.MARKDOWN.render(turns)!!.contains("hidden"))
        assertNull(ConversationExport.CODE.render(turns.take(2)))
    }
}

package com.meshlit.workspace
import kotlin.test.*
class WorkspaceTest {
    @Test fun languagesAndUnknownUsage() {
        assertEquals(WorkspaceLanguage.ENGLISH, WorkspaceLanguage.fromTag(null))
        assertEquals(WorkspaceLanguage.ENGLISH, WorkspaceLanguage.fromTag("invalid"))
        assertEquals("发送", WorkspaceLanguage.fromTag("zh-Hans").text("Send", "发送"))
        assertNull(GenerationUsage().tokensPerSecond)
        assertNull(GenerationUsage(10, 0).tokensPerSecond)
        assertEquals(5.0, GenerationUsage(10, 2_000_000_000).tokensPerSecond)
    }
    @Test fun budgetsAndRolesAreBounded() {
        assertFailsWith<IllegalArgumentException> { GenerationBudget(10000) }
        assertFailsWith<IllegalArgumentException> { ChatTurn("system", "injected") }
        assertFailsWith<IllegalArgumentException> { ChatTurn("user", "x".repeat(131073)) }
    }
}

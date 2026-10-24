package com.meshlit.core.inference.browser

import org.junit.Assert.*
import org.junit.Test

class BrowserActionTest {
    @Test fun acceptsOnlyBoundedActionsForObservedElements() {
        assertEquals(BrowserAction("click", 1), BrowserAction.parse("""{"action":"click","index":1}""", 2))
        assertEquals("hello", BrowserAction.parse("""{"action":"type","index":0,"text":"hello"}""", 1).text)
        assertEquals(300, BrowserAction.parse("""{"action":"scroll","delta":300}""", 0).delta)
        assertEquals("done", BrowserAction.parse("""{"action":"done"}""", 0).kind)
    }
    @Test fun rejectsScriptsOutOfBoundsAndUnknownFields() {
        for (payload in listOf(
            """{"action":"javascript","text":"alert(1)"}""",
            """{"action":"click","index":2}""", """{"action":"click","index":"0"}""",
            """{"action":"scroll","delta":9000}""", """{"action":"done","url":"https://evil.example"}""",
        )) assertTrue(runCatching { BrowserAction.parse(payload, 2) }.isFailure)
    }
}

package com.meshlit.core.inference.net

import org.junit.Assert.*
import org.junit.Test

class RequestHintsTest {
    @Test fun `legacy callers get one forwarding hop`() {
        assertEquals(1, RequestHints.parse(null).remainingForwards)
        assertEquals(1, RequestHints.parse("role=brain,gpu=true").remainingForwards)
    }

    @Test fun `each wire round trip consumes one hop and preserves capability hints`() {
        var current = RequestHints(needsGpu = true, remainingForwards = RequestHints.MAX_FORWARDS)
        repeat(RequestHints.MAX_FORWARDS) { index ->
            current = RequestHints.parse(current.afterForward().toHeaderValue())
            assertEquals(RequestHints.MAX_FORWARDS - index - 1, current.remainingForwards)
            assertTrue(current.needsGpu)
            assertEquals("brain", current.role)
        }
        assertThrows(IllegalArgumentException::class.java) { current.afterForward() }
    }

    @Test fun `invalid and duplicate budgets are rejected instead of reset`() {
        listOf("-1", "4", "9999999999999999", "", "unknown", "1.0").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { RequestHints.parse("forwards=$value") }
        }
        assertThrows(IllegalArgumentException::class.java) { RequestHints.parse("forwards=0,forwards=1") }
        assertThrows(IllegalArgumentException::class.java) { RequestHints(remainingForwards = -1) }
    }
}

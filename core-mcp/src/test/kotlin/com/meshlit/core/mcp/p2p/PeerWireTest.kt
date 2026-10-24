package com.meshlit.core.mcp.p2p
import org.junit.Test
import org.junit.Assert.*
class PeerWireTest {
    @Test fun quotedBracesAreDataAndAdversarialNestingIsRejected() {
        assertEquals("{[untrusted]}", PeerWire.objectValue("{\"text\":\"{[untrusted]}\"}")["text"]!!.toString().trim('"'))
        for (invalid in listOf("[1]", "{}" + " ".repeat(65536), "{\"x\":" + "[".repeat(16) + "0" + "]".repeat(16) + "}", "{\"text\":\"unfinished")) {
            assertThrows(Exception::class.java) { PeerWire.objectValue(invalid) }
        }
    }
}

package com.meshlit.core.gibberlink

import org.junit.Assert.*
import org.junit.Test

class AcousticControlPacketTest {
    @Test fun discoveryRequiresMatchingFreshChallenge() {
        val hello=AcousticControlPacket.hello("A1B2C3D4E5F6")
        assertEquals("A1B2C3D4E5F6",AcousticControlPacket.helloNonce(hello))
        assertTrue(AcousticControlPacket.matchesReply(AcousticControlPacket.reply("A1B2C3D4E5F6"),"A1B2C3D4E5F6"))
        assertFalse(AcousticControlPacket.matchesReply(AcousticControlPacket.reply("000000000000"),"A1B2C3D4E5F6"))
        assertNull(AcousticControlPacket.helloNonce("ML2HELLO:invalid"))
    }
    @Test fun commandFramesAreBoundedAndNeverExecutableByParser() {
        val packet=AcousticControlPacket.commandRequest("STATUS")
        assertEquals("STATUS",AcousticControlPacket.receivedCommand(packet))
        assertNull(AcousticControlPacket.receivedCommand("hello"))
        assertNull(AcousticControlPacket.receivedCommand("ML2CMD:" + "A".repeat(100)))
        assertThrows(IllegalArgumentException::class.java) { AcousticControlPacket.commandRequest("a".repeat(100)) }
        assertThrows(IllegalArgumentException::class.java) { AcousticControlPacket.commandRequest("bad\ncommand") }
    }
}

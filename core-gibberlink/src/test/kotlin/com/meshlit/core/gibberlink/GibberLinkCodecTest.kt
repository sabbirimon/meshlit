package com.meshlit.core.gibberlink
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
class GibberLinkCodecTest {
    @Test fun actualPcmRoundTripAndInvalidHandles() {
        assumeTrue(System.getProperty("gibberlink.host.test") == "true")
        val codec = GibberLinkCodec(); val tx = codec.open(); val rx = codec.open()
        try {
            val text = "Hello agent. The English transcript is visible."
            val pcm = codec.encode(tx, text.toByteArray(Charsets.US_ASCII))
            assertTrue(pcm.any { it != 0.toShort() })
            var recovered: String? = null
            val audio = pcm + ShortArray(48000)
            for (offset in audio.indices step 1024) {
                val frame = audio.copyOfRange(offset, minOf(offset + 1024, audio.size))
                codec.decode(rx, frame, frame.size)?.let { recovered = it.toString(Charsets.US_ASCII) }
            }
            assertEquals(text, recovered)
            assertThrows(IllegalArgumentException::class.java) { codec.encode(tx, ByteArray(97) { 65 }) }
            assertThrows(IllegalArgumentException::class.java) { codec.decode(rx, ShortArray(1), 4097) }
            assertThrows(IllegalArgumentException::class.java) { codec.encode(-1, byteArrayOf(65)) }
        } finally { codec.close(tx); codec.close(rx) }
        assertThrows(IllegalArgumentException::class.java) { codec.decode(rx, ShortArray(1024), 1024) }
    }
    @Test fun silenceCannotInventTranscript() {
        assumeTrue(System.getProperty("gibberlink.host.test") == "true")
        val c = GibberLinkCodec(); val rx = c.open()
        try { repeat(50) { assertNull(c.decode(rx, ShortArray(1024), 1024)) } } finally { c.close(rx) }
    }
}

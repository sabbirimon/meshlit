package com.meshlit.core.net.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [MdnsPcapRecorder]. Verifies the .pcap it writes
 * is round-trippable through [PcapParser] with the same linktype
 * + record bytes.
 */
class MdnsPcapRecorderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `record appends packets in order`() {
        val file = tmp.newFile("capture.pcap")
        MdnsPcapRecorder(file).use { rec ->
            rec.start()
            rec.record(1_700_000_000_000L, byteArrayOf(0x45, 0x00, 0x00, 0x14)) // 4-byte IPv4 stub
            rec.record(1_700_000_000_500L, byteArrayOf(0x45, 0x00, 0x00, 0x14, 0x00))
            rec.record(1_700_000_001_000L, byteArrayOf(0x60, 0x00, 0x00, 0x00, 0x00, 0x14)) // IPv6 stub
            assertEquals(3, rec.packetCount())
        }
        val parsed = PcapParser().parse(file)
        assertTrue("expected a valid parse, got $parsed", parsed is PcapParser.Result.Ok)
        val ok = parsed as PcapParser.Result.Ok
        assertEquals(PcapWriter.LINKTYPE_RAW, ok.linktype)
        assertEquals(3, ok.records.size)
        // First record: IPv4 stub of length 4
        assertEquals(1_700_000_000_000L, ok.records[0].timestampMs)
        assertEquals(4, ok.records[0].data.size)
        assertEquals(0x45.toByte(), ok.records[0].data[0])
        // Third record: IPv6 stub — different content from the first
        assertNotEquals(ok.records[0].data[0], ok.records[2].data[0])
    }

    @Test
    fun `close writes a valid pcap with raw linktype`() {
        val file = tmp.newFile("capture.pcap")
        MdnsPcapRecorder(file).use { rec ->
            rec.start()
            rec.record(0L, byteArrayOf(0x45))
        }
        // Re-parse and confirm we get back exactly the LINKTYPE_RAW
        // the recorder was constructed with.
        val parsed = PcapParser().parse(file)
        assertTrue(parsed is PcapParser.Result.Ok)
        assertEquals(PcapWriter.LINKTYPE_RAW, (parsed as PcapParser.Result.Ok).linktype)
    }

    @Test
    fun `record before start is a no-op`() {
        val file = tmp.newFile("noop.pcap")
        MdnsPcapRecorder(file).use { rec ->
            // No start() call.
            rec.record(0L, byteArrayOf(0x45))
            assertEquals("count only advances when a writer is open", 0, rec.packetCount())
        }
    }
}
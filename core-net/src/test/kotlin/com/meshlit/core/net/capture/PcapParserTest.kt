package com.meshlit.core.net.capture

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class PcapParserTest {
    private fun fixture(order: ByteOrder, nanos: Boolean = false, fraction: Int = 123_456): ByteArray {
        return ByteBuffer.allocate(43).order(order).apply {
            putInt(if (nanos) 0xa1b23c4d.toInt() else 0xa1b2c3d4.toInt())
            putShort(2); putShort(4); putInt(0); putInt(0); putInt(65535); putInt(101)
            putInt(1_700_000_000); putInt(fraction); putInt(3); putInt(5)
            put(byteArrayOf(1, 2, 3))
        }.array()
    }
    private fun parse(bytes: ByteArray): PcapParser.Result {
        val f = File.createTempFile("capture-test", ".pcap")
        return try { f.writeBytes(bytes); PcapParser().parse(f) } finally { f.delete() }
    }
    @Test fun bothByteOrdersPreserveTimestampsAndTruncationMetadata() {
        for (order in listOf(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN)) {
            val r = parse(fixture(order)) as PcapParser.Result.Ok
            assertEquals(101, r.linktype)
            assertEquals(1_700_000_000_123L, r.records.single().timestampMs)
            assertEquals(5, r.records.single().originalLength)
            assertArrayEquals(byteArrayOf(1, 2, 3), r.records.single().data)
        }
    }
    @Test fun nanosecondVariantsConvertToMilliseconds() {
        for (order in listOf(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN)) {
            val r = parse(fixture(order, nanos = true, fraction = 123_456_789)) as PcapParser.Result.Ok
            assertEquals(1_700_000_000_123L, r.records.single().timestampMs)
        }
    }
    @Test fun unsignedTimestampDoesNotWrapNegativeIn2038() {
        val b = fixture(ByteOrder.BIG_ENDIAN)
        ByteBuffer.wrap(b).putInt(24, -1)
        assertEquals(4_294_967_295_123L, (parse(b) as PcapParser.Result.Ok).records.single().timestampMs)
    }
    @Test fun partialRecordHeaderAndPayloadFailRatherThanHideTruncation() {
        val b = fixture(ByteOrder.BIG_ENDIAN)
        assertTrue(parse(b.copyOf(26)) is PcapParser.Result.Invalid)
        assertTrue(parse(b.copyOf(42)) is PcapParser.Result.Invalid)
    }
    @Test fun invalidLengthsFractionsAndPcapngAreRejected() {
        for ((offset, value) in listOf(32 to -1, 36 to 2, 28 to 1_000_000, 16 to 0, 0 to 0x0a0d0d0a)) {
            val b = fixture(ByteOrder.BIG_ENDIAN); ByteBuffer.wrap(b).putInt(offset, value)
            assertTrue("offset=$offset", parse(b) is PcapParser.Result.Invalid)
        }
    }
    @Test fun oversizedFileAndTooManyRecordsFailWithExplicitBudgets() {
        val f = File.createTempFile("capture-budget", ".pcap")
        try {
            RandomAccessFile(f, "rw").use { it.setLength(PcapParser.MAX_FILE_BYTES + 1) }
            assertTrue(PcapParser().parse(f) is PcapParser.Result.Invalid)
        } finally { f.delete() }
        val source = fixture(ByteOrder.BIG_ENDIAN)
        val header = source.copyOfRange(0,24)
        val packet = source.copyOfRange(24,source.size)
        val b = header + ByteArray(packet.size * (PcapParser.MAX_RECORDS + 1)).apply {
            for (i in 0..PcapParser.MAX_RECORDS) packet.copyInto(this, i * packet.size)
        }
        assertTrue(parse(b) is PcapParser.Result.Invalid)
    }
}

package com.meshlit.core.net.capture

import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded classic-PCAP reader. Payloads stay local; PCAPNG needs a separate reader. */
class PcapParser {
    data class Record(val timestampMs: Long, val data: ByteArray, val originalLength: Int) {
        override fun equals(other: Any?): Boolean = other is Record && timestampMs == other.timestampMs &&
            originalLength == other.originalLength && data.contentEquals(other.data)
        override fun hashCode(): Int = timestampMs.hashCode() xor data.contentHashCode() xor originalLength
    }
    sealed class Result {
        data class Ok(val linktype: Int, val records: List<Record>) : Result()
        data class Invalid(val reason: String) : Result()
    }
    companion object {
        const val MAX_FILE_BYTES = 16L * 1024 * 1024
        const val MAX_RECORDS = 10_000
        const val MAX_PACKET_BYTES = 1_000_000
    }
    fun parse(file: File): Result {
        if (!file.isFile || file.length() < 24) return Result.Invalid("File is too small for a PCAP header")
        if (file.length() > MAX_FILE_BYTES) return Result.Invalid("Capture exceeds the 16 MiB mobile preview limit; use desktop Wireshark")
        return try {
            DataInputStream(FileInputStream(file)).use { input ->
                val magic = input.readInt()
                val order = when (magic) {
                    0xa1b2c3d4.toInt(), 0xa1b23c4d.toInt() -> ByteOrder.BIG_ENDIAN
                    0xd4c3b2a1.toInt(), 0x4d3cb2a1 -> ByteOrder.LITTLE_ENDIAN
                    else -> return Result.Invalid("Unsupported capture format; select classic PCAP, not PCAPNG")
                }
                val nanos = magic == 0xa1b23c4d.toInt() || magic == 0x4d3cb2a1
                val header = ByteArray(20).also { input.readFully(it) }
                val h = ByteBuffer.wrap(header).order(order)
                val major = h.short.toInt() and 0xffff
                val minor = h.short.toInt() and 0xffff
                h.int; h.int
                val snaplen = h.int.toLong() and 0xffffffffL
                val linktype = h.int
                if (major != 2 || minor != 4 || snaplen !in 1L..MAX_PACKET_BYTES.toLong())
                    return Result.Invalid("Unsupported PCAP version or snapshot length")
                val records = ArrayList<Record>()
                var consumed = 24L
                val recordHeader = ByteArray(16)
                while (true) {
                    val first = input.read()
                    if (first < 0) break
                    if (records.size >= MAX_RECORDS) return Result.Invalid("Capture exceeds the 10,000-packet preview limit")
                    recordHeader[0] = first.toByte()
                    input.readFully(recordHeader, 1, 15)
                    consumed += 16
                    val rh = ByteBuffer.wrap(recordHeader).order(order)
                    val seconds = rh.int.toLong() and 0xffffffffL
                    val fraction = rh.int.toLong() and 0xffffffffL
                    val captured = rh.int.toLong() and 0xffffffffL
                    val original = rh.int.toLong() and 0xffffffffL
                    if (fraction >= (if (nanos) 1_000_000_000L else 1_000_000L))
                        return Result.Invalid("Invalid PCAP timestamp fraction")
                    if (captured > snaplen || captured > MAX_PACKET_BYTES || original < captured || original > Int.MAX_VALUE)
                        return Result.Invalid("Invalid PCAP packet length")
                    consumed += captured
                    if (consumed > MAX_FILE_BYTES) return Result.Invalid("Capture exceeds the mobile preview limit")
                    val data = ByteArray(captured.toInt()).also { input.readFully(it) }
                    records.add(Record(seconds * 1000 + fraction / (if (nanos) 1_000_000L else 1000L), data, original.toInt()))
                }
                Result.Ok(linktype, records)
            }
        } catch (_: Exception) { Result.Invalid("Capture is truncated or unreadable") }
    }
}

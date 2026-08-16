package com.meshlit.core.net.capture

import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream

/**
 * Reads a libpcap (.pcap) file back into memory so the in-app
 * Network Monitor screen can render a packet list for files
 * produced by:
 *
 *   - [MeshlitCaptureVpnService]
 *   - PCAPdroid (file export)
 *   - Termux `tcpdump -w`
 *   - `tshark -w`
 *
 * Only files with the classic magic `0xa1b2c3d4` are supported —
 * the big-endian nanosecond variant (`0xa1b23c4d`) is rare on
 * Android captures and we don't ship a translator.
 */
class PcapParser {

    data class Record(
        val timestampMs: Long,
        val data: ByteArray,
        val originalLength: Int,
    ) {
        override fun equals(other: Any?): Boolean = other is Record &&
            timestampMs == other.timestampMs &&
            originalLength == other.originalLength &&
            data.contentEquals(other.data)
        override fun hashCode(): Int = timestampMs.hashCode() xor data.contentHashCode() xor originalLength
    }

    sealed class Result {
        data class Ok(val linktype: Int, val records: List<Record>) : Result()
        data class Invalid(val reason: String) : Result()
    }

    fun parse(file: File): Result {
        if (!file.isFile || file.length() < 24) return Result.Invalid("file too small")
        return runCatching {
            DataInputStream(FileInputStream(file)).use { input ->
                val magic = input.readInt()
                if (magic != PcapWriter.PCAP_MAGIC) {
                    return Result.Invalid("not a classic pcap (magic=0x${"%08x".format(magic)})")
                }
                input.readShort() // version major
                input.readShort() // version minor
                input.readInt() // thiszone
                input.readInt() // sigfigs
                input.readInt() // snaplen
                val linktype = input.readInt()

                val records = ArrayList<Record>(64)
                while (true) {
                    val tsSec = runCatching { input.readInt() }.getOrNull() ?: break
                    val tsUsec = input.readInt()
                    val captured = input.readInt()
                    val original = input.readInt()
                    if (captured < 0 || captured > 1_000_000) {
                        return Result.Invalid("bad record length $captured")
                    }
                    val data = ByteArray(captured)
                    input.readFully(data)
                    records.add(
                        Record(
                            timestampMs = tsSec.toLong() * 1000L + (tsUsec / 1000L),
                            data = data,
                            originalLength = original,
                        )
                    )
                }
                Result.Ok(linktype = linktype, records = records)
            }
        }.getOrElse { Result.Invalid(it.message ?: "read failed") }
    }
}

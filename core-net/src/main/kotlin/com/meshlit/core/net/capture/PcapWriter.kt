package com.meshlit.core.net.capture

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Minimal libpcap (.pcap) writer. Encapsulates the file header
 * (magic `0xa1b2c3d4` + version + linktype) and the per-record
 * record header (timestamp + captured length + original length).
 *
 * Output is readable by:
 *   - Wireshark (desktop, via file open)
 *   - `tcpdump -r <file>`
 *   - PCAPdroid (file import)
 *   - Termux `tshark -r <file>` if the user has it installed
 *
 * Why we hand-roll instead of pulling in libpcap4j:
 *   1. The Meshlit capture path is one-way (write) and reads
 *      through PCAPdroid / Wireshark / Termux — never through the
 *      app itself.
 *   2. Avoiding a native dependency keeps the APK smaller and the
 *      build graph simpler.
 *
 * All multi-byte integers are little-endian on disk because the
 * magic is `0xa1b2c3d4` (not `0xd4c3b2a1`).
 */
class PcapWriter(
    private val file: File,
    private val linktype: Int = LINKTYPE_RAW,
) : AutoCloseable {

    private val stream = FileOutputStream(file, false)
    private val out = DataOutputStream(stream)
    private var written: Boolean = false

    init {
        writeFileHeader()
    }

    /** Write the global header (24 bytes). */
    private fun writeFileHeader() {
        if (written) return
        out.writeInt(PCAP_MAGIC)
        out.writeShort(PCAP_VERSION_MAJOR)
        out.writeShort(PCAP_VERSION_MINOR)
        out.writeInt(0) // thiszone
        out.writeInt(0) // sigfigs
        out.writeInt(PCAP_SNAPLEN)
        out.writeInt(linktype)
        out.flush()
        written = true
    }

    /**
     * Append a packet record. [timestampMs] is the wall-clock
     * capture time. [data] is the link-layer frame as captured
     * (for LINKTYPE_RAW this is the IP packet starting at the
     * version byte).
     */
    fun writePacket(timestampMs: Long, data: ByteArray, originalLength: Int = data.size) {
        val tsSec = timestampMs / 1000L
        val tsUsec = ((timestampMs % 1000L) * 1000L).toInt()
        out.writeInt(tsSec.toInt())
        out.writeInt(tsUsec)
        out.writeInt(data.size)
        out.writeInt(originalLength)
        out.write(data)
    }

    override fun close() {
        out.flush()
        out.close()
    }

    companion object {
        const val PCAP_MAGIC: Int = 0xa1b2c3d4.toInt()
        const val PCAP_VERSION_MAJOR: Int = 2
        const val PCAP_VERSION_MINOR: Int = 4
        const val PCAP_SNAPLEN: Int = 65_535

        /** Raw IP packets — link layer is IP, not Ethernet. */
        const val LINKTYPE_RAW: Int = 101

        /** Linux cooked v1 capture — used by some Android VPN devices. */
        const val LINKTYPE_LINUX_SLL: Int = 113

        /**
         * Build a single-shot packet payload as bytes (for tests).
         * Not used at runtime — included so unit tests can construct
         * a known record without a real network interface.
         */
        fun buildRecord(timestampMs: Long, data: ByteArray): ByteArray {
            val baos = ByteArrayOutputStream(16 + data.size)
            val dos = DataOutputStream(baos)
            dos.writeInt((timestampMs / 1000L).toInt())
            dos.writeInt(((timestampMs % 1000L) * 1000L).toInt())
            dos.writeInt(data.size)
            dos.writeInt(data.size)
            dos.write(data)
            return baos.toByteArray()
        }
    }
}

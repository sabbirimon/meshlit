package com.meshlit.core.net.capture

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Writes Meshlit mDNS IP frames to a libpcap (.pcap) file readable
 * by Wireshark / `tcpdump` / PCAPdroid / Termux `tshark`.
 *
 * The recorder is deliberately mDNS-only — the caller filters on
 * `dstPort == 5353` (see [MdnsCaptureListener.handleFrame]) before
 * invoking [record]. The output file uses `LINKTYPE_RAW = 101`
 * because the captured bytes are the raw IP frame (no Ethernet
 * header), matching what `MulticastSocket.receive()` returns.
 *
 * Lifecycle:
 *  - [start] opens the file + writes the global header. Idempotent.
 *  - [record] appends one record. Cheap — single `DataOutputStream.writeInt` +
 *    byte copy.
 *  - [stop] closes the file. After stop, [file] is ready to share
 *    via `FileProvider`.
 *
 * Thread safety: [record] is safe to call from the listener worker
 * thread. The `DataOutputStream` is not itself thread-safe; if
 * production ever needs concurrent writers, wrap with a mutex.
 */
class MdnsPcapRecorder(
    private val file: File,
    private val linktype: Int = PcapWriter.LINKTYPE_RAW,
) : AutoCloseable {

    private var writer: PcapWriter? = null
    private val count = AtomicInteger(0)

    /** Open the file and write the pcap global header. */
    @Synchronized
    fun start() {
        if (writer != null) return
        file.parentFile?.mkdirs()
        writer = PcapWriter(file, linktype)
    }

    /** Append one mDNS IP frame. Safe to call before start() — the
     *  call is a no-op (and does not increment [packetCount])
     *  until [start] is invoked. */
    @Synchronized
    fun record(timestampMs: Long, ipFrame: ByteArray) {
        val w = writer ?: return
        w.writePacket(timestampMs, ipFrame)
        count.incrementAndGet()
    }

    /** Total records written since [start]. */
    fun packetCount(): Int = count.get()

    /** Close the underlying [PcapWriter]. After this call, the file
     *  on disk is a valid pcap. */
    @Synchronized
    override fun close() {
        runCatching { writer?.close() }
        writer = null
    }
}

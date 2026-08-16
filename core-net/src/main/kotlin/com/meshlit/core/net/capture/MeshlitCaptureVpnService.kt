package com.meshlit.core.net.capture

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.meshlit.core.observability.LogSource
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Opt-in device-wide capture service. Android routes packets to
 * the TUN file descriptor; Meshlit records metadata + a PCAP copy
 * but does not decrypt TLS or upload any bytes.
 *
 * Important platform limitation: a VPN app that reads a TUN packet
 * must also forward it to a real upstream tunnel to preserve the
 * device's network connection. This first implementation is a
 * metadata capture surface and deliberately does not claim to be a
 * transparent MITM. The UI explains this and recommends PCAPdroid
 * for full device routing when needed.
 */
class MeshlitCaptureVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private var writer: PcapWriter? = null
    private val running = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running.get()) startCapture()
        return START_STICKY
    }

    private fun startCapture() {
        val vpn = Builder()
            .setSession("Meshlit network monitor")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .setBlocking(false)
        tun = runCatching { vpn.establish() }.getOrNull()
        if (tun == null) {
            stopSelf()
            return
        }
        val captures = File(filesDir, "exports/captures").apply { mkdirs() }
        writer = runCatching {
            PcapWriter(File(captures, "meshlit-${System.currentTimeMillis()}.pcap"))
        }.getOrNull()
        running.set(true)
        worker = thread(name = "meshlit-vpn-capture", isDaemon = true) {
            readPackets()
        }
    }

    private fun readPackets() {
        val fd = tun ?: return
        try {
            FileInputStream(fd.fileDescriptor).use { input ->
                val buffer = ByteArray(65_535)
                while (running.get()) {
                    val read = input.read(buffer)
                    if (read <= 0) continue
                    val packet = buffer.copyOf(read)
                    writer?.writePacket(System.currentTimeMillis(), packet)
                    onPacket(packet)
                }
            }
        } catch (_: IOException) {
            // Closing the descriptor interrupts the blocking read on stop.
        } finally {
            writer?.close()
            writer = null
        }
    }

    /** Override point for app integration / tests. */
    protected open fun onPacket(packet: ByteArray) {
        val parsed = PacketParser.parseIp(packet) ?: return
        PacketCaptureRegistry.publish(
            PacketCaptureRegistry.Entry(
                timestampMs = System.currentTimeMillis(),
                source = LogSource.NETWORK,
                src = parsed.src,
                dst = parsed.dst,
                transport = parsed.transport.name,
                srcPort = parsed.srcPort,
                dstPort = parsed.dstPort,
                payloadLength = parsed.payloadLength,
            )
        )
    }

    private fun stopCapture() {
        if (!running.getAndSet(false)) return
        runCatching { tun?.close() }
        tun = null
        worker?.interrupt()
        worker = null
        writer?.close()
        writer = null
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.meshlit.core.net.capture.STOP"
    }
}

/**
 * Process-local packet metadata ring buffer. Kept in :core-net so
 * the app's NetworkMonitorScreen can read device packets without
 * coupling the service to Compose or the app module.
 */
object PacketCaptureRegistry {
    data class Entry(
        val timestampMs: Long,
        val source: LogSource,
        val src: String,
        val dst: String,
        val transport: String,
        val srcPort: Int,
        val dstPort: Int,
        val payloadLength: Int,
    )

    private const val MAX = 2_000
    private val lock = Any()
    private val list = ArrayDeque<Entry>(MAX)

    fun publish(entry: Entry) {
        synchronized(lock) {
            if (list.size >= MAX) list.removeFirst()
            list.addLast(entry)
        }
    }

    fun snapshot(): List<Entry> = synchronized(lock) { list.toList() }
    fun clear() = synchronized(lock) { list.clear() }
}

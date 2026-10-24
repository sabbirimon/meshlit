package com.meshlit.core.net.capture

import android.content.Intent
import android.net.VpnService
import com.meshlit.core.observability.LogSource

/**
 * Disabled legacy service identity. The former TUN reader did not forward packets
 * and could interrupt connectivity. Never establish a TUN until a supervised,
 * traffic-preserving backend is implemented and tested. Use the external capture
 * companion instead; the app manifest also disables this service.
 */
class MeshlitCaptureVpnService : VpnService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }
    companion object { const val ACTION_STOP = "com.meshlit.core.net.capture.STOP" }
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

package com.meshlit.core.net.capture

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

/**
 * Listens on `224.0.0.251:5353` for mDNS traffic and forwards every
 * decoded Meshlit advertisement to a [SharedFlow] for the UI's
 * live packet stream + the .pcap recorder.
 *
 * Design notes:
 *  - **mDNS-only** — Android doesn't require [android.net.VpnService]
 *    to read multicast UDP, only `CHANGE_WIFI_MULTICAST_STATE` +
 *    a `WifiManager.MulticastLock`. We acquire the lock so the
 *    listener receives packets even while the screen is off.
 *  - **One packet at a time** — `socket.receive()` blocks. The
 *    decode happens on the same worker thread to keep the packet
 *    flow synchronous; the SharedFlow has `buffer = 64` so the
 *    consumer (Compose) is never blocked.
 *  - **No hard dep on core-discovery** — the JSON shape is
 *    matched here directly so the listener can live in `core-net`.
 *    Drift is caught by the unit test that pins the JSON contract.
 *
 * The class is deliberately narrow: it owns a single
 * [MulticastSocket], a single worker thread, and a single
 * [SharedFlow]. The application context is injected as a
 * [SocketFactory] + [MulticastLockFactory] so tests can swap them.
 */
class MdnsCaptureListener(
    private val socketFactory: MulticastSocketFactory = RealMulticastSocketFactory,
    private val multicastLockFactory: MulticastLockFactory = NoOpMulticastLockFactory,
    private val scope: kotlinx.coroutines.CoroutineScope =
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        ),
) {
    private val _records = MutableSharedFlow<MdnsPacketRecord>(
        replay = 0,
        extraBufferCapacity = 64,
    )

    /** Live stream of decoded mDNS packets. Bounded at 64 — drops on overflow. */
    val records: SharedFlow<MdnsPacketRecord> = _records.asSharedFlow()

    private val _running = java.util.concurrent.atomic.AtomicBoolean(false)
    private val _worker = java.util.concurrent.atomic.AtomicReference<Thread?>(null)
    private var socket: MulticastSocket? = null
    private var lock: AutoCloseable? = null

    /** True if the worker thread is alive. */
    val isRunning: Boolean get() = _running.get()

    /**
     * Bind to the mDNS group and start the receive loop. Returns
     * the worker thread; returns `null` if another start() is in
     * progress or the bind failed.
     */
    @Synchronized
    fun start(): Thread? {
        if (_running.get()) return _worker.get()
        val sock = runCatching {
            // Bind explicitly to 5353 so we receive mDNS even when the
            // OS mdnsd daemon filters multicast on user-space sockets.
            // We rely on `reuseAddress=true` to coexist with the daemon.
            val s = socketFactory.createMulticastSocket(MDNS_PORT)
            s.reuseAddress = true
            s.broadcast = true
            s.joinGroup(InetAddress.getByName(MDNS_GROUP))
            s.soTimeout = 1_000 // allow periodic unblocking so stop() can interrupt
            Log.i(TAG, "started: localPort=${s.localPort} bound=${s.localSocketAddress}")
            s
        }.getOrElse {
            Log.w(TAG, "start failed: ${it.message}")
            return null
        }
        socket = sock
        lock = multicastLockFactory.acquire()
        val worker = Thread({ readLoop(sock) }, "meshlit-mdns-listener").apply {
            isDaemon = true
            start()
        }
        _worker.set(worker)
        _running.set(true)
        return worker
    }

    /**
     * Stop the receive loop. Closes the socket (interrupting the
     * blocking `receive()`), releases the multicast lock, and joins
     * the worker thread with a short timeout. Idempotent.
     */
    @Synchronized
    fun stop() {
        if (!_running.getAndSet(false)) return
        runCatching { socket?.close() }
        runCatching { lock?.close() }
        socket = null
        lock = null
        _worker.getAndSet(null)?.let {
            runCatching { it.join(500) }
        }
    }

    private fun readLoop(sock: MulticastSocket) {
        val buffer = ByteArray(65_535)
        var recvCount = 0
        var timeoutCount = 0
        var lastLogMs = 0L
        while (_running.get()) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                sock.receive(packet)
            } catch (e: IOException) {
                // socket closed or timed out
                if (!_running.get()) return
                timeoutCount++
                if (timeoutCount % 30 == 1) {
                    Log.d(TAG, "loop alive, timeouts so far=$timeoutCount")
                }
                continue
            }
            recvCount++
            val frame = packet.data.copyOf(packet.length)
            val srcIp = packet.address.hostAddress ?: "0.0.0.0"
            val srcPort = packet.port
            val record = handleDnsPayload(frame, srcIp, srcPort)
            if (record != null) _records.tryEmit(record)
            val now = System.currentTimeMillis()
            if (now - lastLogMs > 5000 || recvCount % 10 == 0) {
                Log.d(TAG, "recv=$recvCount lastSize=${frame.size} from=$srcIp:$srcPort decoded=${record?.decoded != null}")
                lastLogMs = now
            }
        }
    }

    /**
     * Pure decode entry point — takes the raw DNS payload (the
     * UDP datagram body that [MulticastSocket.receive] returns),
     * plus the source IP:port. Returns the [MdnsPacketRecord]
     * (or `null` if the payload doesn't decode to a Meshlit
     * advertisement), AND publishes it on the [records] flow.
     * Exposed publicly so tests can drive it with synthetic DNS
     * frames without a real socket.
     */
    fun handleDnsPayload(
        dns: ByteArray,
        sourceIp: String,
        sourcePort: Int,
    ): MdnsPacketRecord? {
        val decoded = decodeMeshlit(dns)
        // For the pcap we want to surface raw mDNS even when not
        // decoded — make sure every inbound packet shows up in the
        // live stream so users see the network activity.
        val record = MdnsPacketRecord(
            timestampMs = System.currentTimeMillis(),
            sourceIp = sourceIp,
            sourcePort = sourcePort,
            length = dns.size,
            decoded = decoded,
            rawDnsPayload = dns,
        )
        return record
    }

    /**
     * Backwards-compatible IP-frame entry point used by unit tests.
     * Parses a real IPv4/IPv6 + UDP frame and extracts the DNS
     * payload before delegating to [handleDnsPayload]. Used in
     * tests; production uses [handleDnsPayload] directly because
     * Android's `MulticastSocket.receive()` returns the DNS bytes
     * without any IP/UDP envelope.
     */
    fun handleFrame(ipFrame: ByteArray): MdnsPacketRecord? {
        val parsed = PacketParser.parseIp(ipFrame) ?: return null
        // We want only packets addressed *to* the mDNS group (or from
        // any source port) — the standard mDNS response model.
        if (parsed.dstPort != MDNS_PORT && parsed.srcPort != MDNS_PORT) return null
        // Payload lives after IP+UDP headers.
        val ihl = if (parsed.version == 4) (ipFrame[0].toInt() and 0x0F) * 4 else 40
        val udpHeaderLen = 8
        val payloadStart = ihl + udpHeaderLen
        if (ipFrame.size < payloadStart) return null
        val dnsPayload = ipFrame.copyOfRange(payloadStart, ipFrame.size)
        val record = handleDnsPayload(dnsPayload, parsed.src, parsed.srcPort)
        if (record != null) _records.tryEmit(record)
        return record
    }

    /**
     * DNS message parser focused on the records we care about: PTR
     * for `_meshlit._tcp.local.` + TXT (with `meshlit=<json>`).
     *
     * We deliberately keep this small. Anything outside that
     * surface returns `null` and the UI shows the raw packet.
     */
    private fun decodeMeshlit(dns: ByteArray): MdnsPacketRecord.DecodedMeshlitAdv? {
        if (dns.size < 12) return null
        val qd = readUInt16BE(dns, 4)
        val an = readUInt16BE(dns, 6)
        if (an == 0 && qd > 0) {
            // Query-only packet — no Meshlit info to extract.
            return null
        }
        var offset = 12
        // Skip questions.
        repeat(qd) {
            val skip = skipName(dns, offset) ?: return null
            offset = skip + 4 // QTYPE + QCLASS
        }
        // Walk answers looking for our service.
        repeat(an) {
            val nameEnd = skipName(dns, offset) ?: return null
            if (nameEnd + 10 > dns.size) return null
            val qtype = readUInt16BE(dns, nameEnd)
            val rdLen = readUInt16BE(dns, nameEnd + 8)
            val dataStart = nameEnd + 10
            val dataEnd = dataStart + rdLen
            if (dataEnd > dns.size) return null
            val name = readName(dns, offset) ?: return null
            if (qtype == QT_TXT && name.contains(MESHLIT_SERVICE)) {
                val txt = parseTxtAttributes(dns, dataStart, rdLen)
                val json = txt["meshlit"] ?: return null
                return decodeJson(json)
            }
            offset = dataEnd
        }
        return null
    }

    private fun decodeJson(json: String): MdnsPacketRecord.DecodedMeshlitAdv? {
        return runCatching {
            val element = Json.parseToJsonElement(json).jsonObject
            val portStr = element["port"]?.jsonPrimitive?.content ?: return null
            MdnsPacketRecord.DecodedMeshlitAdv(
                nodeId = element["nodeId"]?.jsonPrimitive?.content ?: return null,
                host = element["host"]?.jsonPrimitive?.content ?: return null,
                port = portStr.toIntOrNull() ?: return null,
                fingerprint = element["fingerprint"]?.jsonPrimitive?.content ?: return null,
            )
        }.getOrNull()
    }

    companion object {
        const val MDNS_GROUP: String = "224.0.0.251"
        const val MDNS_PORT: Int = 5353
        const val MESHLIT_SERVICE: String = "_meshlit._tcp.local"
        private const val QT_TXT: Int = 16
        private const val TAG = "MdnsCapture"

        private fun readUInt16BE(b: ByteArray, off: Int): Int =
            ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

        /** Read a DNS name, returning the index *past* the name. */
        private fun skipName(b: ByteArray, off: Int): Int? {
            var i = off
            var jumps = 0
            while (i < b.size) {
                val len = b[i].toInt() and 0xFF
                when {
                    len == 0 -> return i + 1
                    (len and 0xC0) == 0xC0 -> {
                        // Pointer: 2 bytes total. We don't dereference
                        // (don't need the value, only the bounds).
                        return i + 2
                    }
                    (len and 0xC0) == 0 -> {
                        if (i + 1 + len > b.size) return null
                        i += 1 + len
                        jumps++
                        if (jumps > 32) return null
                    }
                    else -> return null
                }
            }
            return null
        }

        /**
         * Materialise a DNS name (including pointer decompression).
         * Returns the assembled dotted name. Handles up to 16
         * pointer hops.
         */
        private fun readName(b: ByteArray, off: Int): String? {
            val labels = mutableListOf<String>()
            var i = off
            var hops = 0
            var advanced = false
            while (i < b.size) {
                val len = b[i].toInt() and 0xFF
                when {
                    len == 0 -> {
                        if (!advanced) i++
                        return labels.joinToString(".")
                    }
                    (len and 0xC0) == 0xC0 -> {
                        if (i + 1 >= b.size) return null
                        val ptr = ((len and 0x3F) shl 8) or (b[i + 1].toInt() and 0xFF)
                        i = ptr
                        advanced = true
                        if (++hops > 16) return null
                    }
                    (len and 0xC0) == 0 -> {
                        if (i + 1 + len > b.size) return null
                        labels.add(String(b, i + 1, len, Charsets.UTF_8))
                        i += 1 + len
                    }
                    else -> return null
                }
            }
            return null
        }

        /** Read TXT attributes into a map of name → value. */
        private fun parseTxtAttributes(b: ByteArray, off: Int, rdLen: Int): Map<String, String> {
            val out = mutableMapOf<String, String>()
            var i = off
            val end = off + rdLen
            while (i < end) {
                val totalLen = b[i].toInt() and 0xFF
                i++
                if (i + totalLen > end) break
                val attr = String(b, i, totalLen, Charsets.UTF_8)
                val eq = attr.indexOf('=')
                if (eq > 0) {
                    out[attr.substring(0, eq)] = attr.substring(eq + 1)
                }
                i += totalLen
            }
            return out
        }
    }
}

/** Minimal multicast-socket factory so tests can substitute a fake. */
fun interface MulticastSocketFactory {
    fun createMulticastSocket(port: Int): MulticastSocket
}

object RealMulticastSocketFactory : MulticastSocketFactory {
    override fun createMulticastSocket(port: Int): MulticastSocket =
        if (port <= 0) MulticastSocket() else MulticastSocket(port)
}

/**
 * Indirection for the `WifiManager.MulticastLock` so production
 * code can acquire + release the lock and tests can substitute a
 * no-op. The `AutoCloseable` shape lets us call `use {}` uniformly.
 */
fun interface MulticastLockFactory {
    fun acquire(): AutoCloseable?
}

object NoOpMulticastLockFactory : MulticastLockFactory {
    override fun acquire(): AutoCloseable? = null
}
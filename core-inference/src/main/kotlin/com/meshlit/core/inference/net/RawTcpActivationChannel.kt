package com.meshlit.core.inference.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Raw-TCP activation channel. One socket per remote peer; length-
 * prefixed JSON frames carrying [ActivationPacket]. The default
 * transport used by v1; the WebRTC sibling will be feature-flagged
 * in a follow-up.
 *
 * Why raw TCP and not NanoHTTPD/SSE: a pipeline-parallel inference
 * loop needs **bidirectional** flow on a long-lived socket. NanoHTTPD
 * is request/response; SSE is one-way server→client. A direct TCP
 * socket with a tiny framing protocol is the smallest correct
 * shape.
 *
 * Threading:
 *  - The reader and writer run on dedicated coroutines under a
 *    single `SupervisorJob`. Cancelling the scope closes the
 *    socket and stops both.
 *  - [send] is non-blocking — `tryEmit` into the buffer. Drop
 *    policy: SUSPEND is too aggressive for a realtime tokenizer
 *    loop; we use `BufferOverflow.DROP_OLDEST` so the engine
 *    never blocks on a slow peer.
 *
 * Wire format (matches `RawTcpActivationServer`):
 *  - u32 BE length prefix
 *  - JSON-encoded [ActivationPacket] body
 */
class RawTcpActivationChannel(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ActivationTransport {

    private val opened = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    /**
     * The attached socket. Null until either [connect] dials a peer
     * or [tryAttachSocket] wires a pre-accepted socket (server
     * side). The atomic makes the bind idempotent across both paths.
     */
    private val socketRef = AtomicReference<Socket?>(null)

    /** Job that owns the read/write coroutines. Cancelled on close. */
    private val loopJob = AtomicReference<Job?>(null)

    /** Outbound packets awaiting serialization. */
    private val outgoing = MutableSharedFlow<ActivationPacket>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Inbound packets emitted to subscribers. */
    private val _incoming = MutableSharedFlow<ActivationPacket>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun connect(peerHost: String, peerPort: Int) {
        if (!opened.compareAndSet(false, true)) return
        scope.launch {
            try {
                val sock = Socket().apply {
                    soTimeout = 0 // never time out — the SSE loop is long-lived
                    tcpNoDelay = true
                    connect(InetSocketAddress(peerHost, peerPort), CONNECT_TIMEOUT_MS)
                }
                if (!tryAttachSocket(sock)) {
                    sock.close()
                    return@launch
                }
                // Reader + writer coroutines launched inside tryAttachSocket.
                // Park here until close() cancels the loop job.
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                    cont.invokeOnCancellation { /* close() handles teardown */ }
                }
            } catch (t: Throwable) {
                if (closed.get()) return@launch // ignore on teardown
                // Surface the failure to subscribers by closing; the
                // caller observes incoming() end and reacts.
                close()
            }
        }
    }

    /**
     * Bind a pre-accepted [Socket] into this channel. Used by
     * [RawTcpActivationServer] so the server side can hook directly
     * into a freshly accepted peer socket without dialing back.
     * Returns false if the channel was already attached (idempotent).
     */
    fun tryAttachSocket(sock: Socket): Boolean {
        if (!opened.compareAndSet(false, true)) {
            // Already attached via connect() — caller should drop
            // the duplicate socket and reuse the existing channel.
            return false
        }
        socketRef.set(sock)
        val child = CoroutineScope(scope.coroutineContext + SupervisorJob())
        val out = DataOutputStream(sock.getOutputStream())
        val ins = DataInputStream(sock.getInputStream())
        val job = child.launch { readLoop(ins) }
        child.launch { writeLoop(out) }
        loopJob.set(job)
        return true
    }

    override fun send(packet: ActivationPacket) {
        if (closed.get()) return
        outgoing.tryEmit(packet)
    }

    override fun incoming(): Flow<ActivationPacket> = _incoming.asSharedFlow()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val sock = socketRef.getAndSet(null)
        // Cancel the reader/writer job — they will unwind via their
        // finally blocks which call back into close() (idempotent).
        loopJob.getAndSet(null)?.cancel()
        if (sock != null) {
            // Socket.close() is cheap and blocking-on-close isn't an
            // issue for a single FD; we deliberately don't switch
            // dispatchers here because close() is non-suspend.
            try { sock.close() } catch (_: IOException) { /* swallow */ }
        }
    }

    /**
     * Spawn the read and write coroutines against the already-attached
     * socket. Both run on [scope]; either ending tears the channel
     * down via its `finally { close() }` block.
     */
    private suspend fun readLoop(ins: DataInputStream) {
        try {
            while (!closed.get()) {
                val len = ins.readInt()
                if (len <= 0 || len > MAX_FRAME_BYTES) {
                    // Reset on illegal frame size — protocol error.
                    break
                }
                val body = ByteArray(len)
                ins.readFully(body)
                val packet = json.decodeFromString(ActivationPacket.serializer(), String(body, Charsets.UTF_8))
                _incoming.tryEmit(packet)
            }
        } catch (_: Throwable) {
            // Connection dropped. End the flow.
        } finally {
            close()
        }
    }

    private suspend fun writeLoop(out: DataOutputStream) {
        try {
            outgoing.collect { packet ->
                val body = json.encodeToString(ActivationPacket.serializer(), packet)
                    .toByteArray(Charsets.UTF_8)
                out.writeInt(body.size)
                out.write(body)
                out.flush()
            }
        } catch (_: Throwable) {
            // Peer dropped — end the channel.
        } finally {
            close()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        /** Hard cap on a single frame so a malicious peer can't
         *  exhaust our heap with a single `readInt`. */
        private const val MAX_FRAME_BYTES = 4 * 1024 * 1024  // 4 MB — KV slice upper bound for Qwen 1.5B @ 4K ctx
    }
}

/**
 * The matching server half — a [RawTcpActivationServer] listens on
 * a port and creates one [RawTcpActivationChannel] per accepted
 * socket. Exposed as a class so it can be wired from the FGS
 * alongside [com.meshlit.core.inference.net.InferenceHttpServer].
 */
class RawTcpActivationServer(
    private val port: Int,
    private val onChannel: (RawTcpActivationChannel) -> Unit,
) : AutoCloseable {

    private val running = AtomicBoolean(false)
    private val serverRef = AtomicReference<ServerSocket?>(null)
    private var acceptThread: Thread? = null

    /**
     * The live port the server is bound to. Returns the requested
     * port after [start], or 0 before [start] / after [close]. The
     * FGS surfaces this on `/v1/health` so peers can dial the
     * activation channel without hard-coding a port.
     */
    val boundPort: Int
        get() = serverRef.get()?.localPort ?: 0

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val srv = try {
            ServerSocket(port)
        } catch (t: Throwable) {
            // Port already bound or permission denied — surface to
            // caller via the missing channel callback (silent skip).
            running.set(false)
            return
        }
        serverRef.set(srv)
        acceptThread = Thread({
            while (running.get()) {
                val sock = try { srv.accept() } catch (_: Throwable) {
                    if (!running.get()) return@Thread
                    continue
                }
                val ch = RawTcpActivationChannel()
                // Wire the freshly accepted socket into the channel —
                // no dial-back required since the peer already
                // connected to us.
                ch.tryAttachSocket(sock)
                onChannel(ch)
            }
        }, "meshlit-activation-tcp").apply {
            isDaemon = true
            start()
        }
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        val srv = serverRef.getAndSet(null)
        if (srv != null) {
            try { srv.close() } catch (_: Throwable) { /* swallow */ }
        }
        acceptThread = null
    }
}

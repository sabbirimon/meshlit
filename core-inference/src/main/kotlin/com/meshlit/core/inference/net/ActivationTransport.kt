package com.meshlit.core.inference.net

/**
 * Transport for [ActivationPacket]s flowing between pipeline stages.
 *
 * Two implementations live in this package:
 *  - [RawTcpActivationChannel] — the production transport today.
 *    One socket per upstream/downstream peer, length-prefixed
 *    frames. Pure JDK + okio; zero APK overhead.
 *  - [WebRtcActivationChannel]   — optional, dependency-gated. Will
 *    exist when `io.github.webrtc-sdk:android` is on the
 *    compile classpath. Skipped for v1 because the AAR is ~50 MB
 *    and our `minSdk = 23` floor doesn't allow graceful
 *    fallback inside the APK.
 *
 * Selection: [create] prefers raw TCP unless `meshlit.transport=webrtc`
 * is on the command line (or System property). LITE phones that
 * can't run WebRTC reliably stay on raw TCP.
 *
 * Both implementations must be `CoroutineScope`-bound and cancellable
 * via [close]. The whole transport reuses one channel per remote
 * peer — not one channel per packet.
 */
interface ActivationTransport : AutoCloseable {
    /**
     * Open a connection to [peerHost]:[peerPort] and start the
     * send/receive loop. The implementation is responsible for
     * lifecycle: it must close on [close] and tear down the
     * underlying socket.
     */
    fun connect(peerHost: String, peerPort: Int)

    /** Queue an [ActivationPacket] for transmission. */
    fun send(packet: ActivationPacket)

    /**
     * Subscribe to incoming packets. The transport owns the buffer
     * and lifecycle; consumers should treat this as a hot flow that
     * ends when the connection drops.
     */
    fun incoming(): kotlinx.coroutines.flow.Flow<ActivationPacket>

    /** Idempotent. */
    override fun close()

    companion object {
        /**
         * Pick an implementation. Today this always returns a
         * [RawTcpActivationChannel] — the WebRTC path is wired
         * behind a feature flag (added in a follow-up) so the
         * activation transport has one stable interface while
         * we evaluate the AAR's minSdk compatibility.
         */
        fun create(): ActivationTransport = RawTcpActivationChannel()
    }
}
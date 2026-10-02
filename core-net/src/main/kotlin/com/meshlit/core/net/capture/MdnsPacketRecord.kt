package com.meshlit.core.net.capture

import kotlinx.serialization.Serializable

/**
 * One decoded mDNS packet surfaced by [MdnsCaptureListener] to
 * the UI's live packet stream. Carries enough metadata for the
 * screen to render a single row without re-parsing the raw
 * datagram.
 *
 * `decoded` is non-null when the packet contained a `_meshlit._tcp.local.`
 * pointer record with a `meshlit=` TXT attribute whose JSON we
 * recognised — the same [PeerAdvertisement] payload emitted by
 * `NsdDiscoveryTransport`. It's `null` for any other mDNS traffic
 * (Bonjour browse, queries from other apps, responses to other
 * services) so the UI can show "raw mDNS · N bytes" instead.
 */
data class MdnsPacketRecord(
    val timestampMs: Long,
    val sourceIp: String,
    val sourcePort: Int,
    val length: Int,
    val decoded: DecodedMeshlitAdv? = null,
    /** Raw DNS payload (the UDP datagram body). The PCAP recorder
     *  wraps this in a tiny IPv4+UDP affordance so Wireshark can
     *  decode it as mDNS over UDP/5353. Kept off the hot path so
     *  the live stream stays slim — exposed here only because the
     *  listener can't afford to keep a separate buffer per
     *  subscriber. */
    val rawDnsPayload: ByteArray = byteArrayOf(0x00),
) {
    override fun equals(other: Any?): Boolean = other is MdnsPacketRecord &&
        timestampMs == other.timestampMs &&
        sourceIp == other.sourceIp &&
        sourcePort == other.sourcePort &&
        length == other.length &&
        decoded == other.decoded &&
        rawDnsPayload.contentEquals(other.rawDnsPayload)

    override fun hashCode(): Int = timestampMs.hashCode() xor
        sourceIp.hashCode() xor sourcePort xor length xor
        (decoded?.hashCode() ?: 0) xor rawDnsPayload.contentHashCode()
    /**
     * Mirror of [com.meshlit.core.discovery.PeerAdvertisement]
     * intended for the UI. Kept in `core-net` so the listener
     * does not have a hard dependency on `core-discovery`.
     */
    @Serializable
    data class DecodedMeshlitAdv(
        val nodeId: String,
        val host: String,
        val port: Int,
        val fingerprint: String,
    )
}

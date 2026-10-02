package com.meshlit.core.net.capture

import java.net.InetAddress

/**
 * Tiny helper that wraps a raw mDNS payload (the UDP datagram body
 * that `MulticastSocket.receive()` returns) inside a minimal
 * IPv4+UDP header so the result is a valid IP frame suitable for
 * `LINKTYPE_RAW` (a.k.a. `DLT_RAW`) PCAP output.
 *
 * Why this is necessary:
 *  - On Android, `MulticastSocket` returns the DNS payload **without**
 *    any IP/UDP envelope. The `MulticastSocket` API doesn't expose
 *    the IP source/destination either.
 *  - Wireshark reads `LINKTYPE_RAW` files as raw IP frames, so we
 *    must invent a header before the bytes go into the pcap.
 *  - We use `127.0.0.1` as the destination (224.0.0.251 isn't
 *    unicast — the wire-level fields don't matter for Wireshark
 *    dissection; only the length/header validity does).
 *
 * This is intentionally lightweight — the goal is "Wireshark
 * recognises it as mDNS over UDP" rather than a faithful
 * reproduction of the on-wire frame.
 */
object MdnsCaptureUtils {
    /** Construct a fake IPv4+UDP/5353 envelope around the DNS payload. */
    fun wrapAsIpv4Udp5353(dnsPayload: ByteArray, srcIp: String): ByteArray {
        val src = runCatching { InetAddress.getByName(srcIp) }.getOrNull()
        val srcAddr = src?.address ?: byteArrayOf(127.toByte(), 0.toByte(), 0.toByte(), 1.toByte())
        val dstAddr = byteArrayOf(224.toByte(), 0.toByte(), 0.toByte(), 251.toByte())
        val totalLen = 20 + 8 + dnsPayload.size
        val ip = ByteArray(20)
        ip[0] = 0x45.toByte() // version 4, IHL 5 (20 bytes)
        ip[1] = 0x00.toByte() // DSCP / ECN
        ip[2] = ((totalLen ushr 8) and 0xFF).toByte()
        ip[3] = (totalLen and 0xFF).toByte()
        ip[4] = 0x00.toByte(); ip[5] = 0x00.toByte() // identification
        ip[6] = 0x00.toByte(); ip[7] = 0x00.toByte() // flags + fragment offset
        ip[8] = 64.toByte() // TTL
        ip[9] = 17.toByte() // protocol: UDP
        ip[10] = 0x00.toByte(); ip[11] = 0x00.toByte() // header checksum (left zero — Wireshark doesn't care)
        System.arraycopy(srcAddr, 0, ip, 12, 4)
        System.arraycopy(dstAddr, 0, ip, 16, 4)
        val udp = ByteArray(8)
        // We don't know the real source port per packet, so we use
        // 5353 — mDNS responses always come from 5353 anyway.
        val srcPort = 5353
        udp[0] = ((srcPort ushr 8) and 0xFF).toByte()
        udp[1] = (srcPort and 0xFF).toByte()
        udp[2] = ((5353 ushr 8) and 0xFF).toByte()
        udp[3] = (5353 and 0xFF).toByte()
        val udpLen = 8 + dnsPayload.size
        udp[4] = ((udpLen ushr 8) and 0xFF).toByte()
        udp[5] = (udpLen and 0xFF).toByte()
        udp[6] = 0x00.toByte(); udp[7] = 0x00.toByte() // UDP checksum (zero/disabled)
        return ip + udp + dnsPayload
    }
}

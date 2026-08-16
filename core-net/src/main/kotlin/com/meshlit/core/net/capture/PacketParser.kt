package com.meshlit.core.net.capture

/**
 * Best-effort parser for IPv4 / IPv6 / TCP / UDP headers exposed
 * by the [MeshlitCaptureVpnService]. We never need the full
 * payload — the VpnService writes a 96-byte preview per packet
 * to the ring buffer and the entire IP frame to the .pcap file.
 *
 * The parser is intentionally tolerant of malformed input. A
 * truncated or junk packet returns `null` so the caller can drop
 * it without crashing the capture thread.
 */
object PacketParser {

    /** Lightweight summary of a parsed packet. */
    data class Parsed(
        val version: Int,
        val src: String,
        val dst: String,
        val transport: Transport,
        val srcPort: Int,
        val dstPort: Int,
        val payloadLength: Int,
        val preview: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean = other is Parsed &&
            version == other.version && src == other.src && dst == other.dst &&
            transport == other.transport && srcPort == other.srcPort &&
            dstPort == other.dstPort && payloadLength == other.payloadLength &&
            preview.contentEquals(other.preview)
        override fun hashCode(): Int = version * 31 + src.hashCode() + dst.hashCode() +
            transport.hashCode() * 7 + srcPort * 11 + dstPort * 13 +
            payloadLength + preview.contentHashCode()
    }

    enum class Transport { TCP, UDP, OTHER }

    /**
     * Try to parse [data] as an IP packet. Returns `null` for
     * unrecognized link-layer / protocol combinations.
     */
    fun parseIp(data: ByteArray): Parsed? {
        if (data.isEmpty()) return null
        return when ((data[0].toInt() ushr 4) and 0x0F) {
            4 -> parseIpv4(data)
            6 -> parseIpv6(data)
            else -> null
        }
    }

    private fun parseIpv4(data: ByteArray): Parsed? {
        if (data.size < 20) return null
        val ihl = (data[0].toInt() and 0x0F) * 4
        if (ihl < 20 || data.size < ihl) return null
        val totalLen = readUInt16BE(data, 2)
        val protocol = data[9].toInt() and 0xFF
        val src = ipv4ToString(data, 12)
        val dst = ipv4ToString(data, 16)
        return buildParsed(4, src, dst, protocol, data, ihl, totalLen)
    }

    private fun parseIpv6(data: ByteArray): Parsed? {
        if (data.size < 40) return null
        val payloadLen = readUInt16BE(data, 4)
        val nextHeader = data[6].toInt() and 0xFF
        val src = ipv6ToString(data, 8)
        val dst = ipv6ToString(data, 24)
        return buildParsed(6, src, dst, nextHeader, data, 40, payloadLen + 40)
    }

    private fun buildParsed(
        version: Int,
        src: String,
        dst: String,
        protocol: Int,
        data: ByteArray,
        headerLen: Int,
        totalLen: Int,
    ): Parsed? {
        return when (protocol) {
            6 -> parseTcp(version, src, dst, data, headerLen, totalLen)
            17 -> parseUdp(version, src, dst, data, headerLen, totalLen)
            else -> {
                val preview = preview(data, headerLen)
                Parsed(
                    version = version,
                    src = src,
                    dst = dst,
                    transport = Transport.OTHER,
                    srcPort = 0,
                    dstPort = 0,
                    payloadLength = (totalLen - headerLen).coerceAtLeast(0),
                    preview = preview,
                )
            }
        }
    }

    private fun parseTcp(version: Int, src: String, dst: String, data: ByteArray, headerLen: Int, totalLen: Int): Parsed? {
        if (data.size < headerLen + 20) return null
        val srcPort = readUInt16BE(data, headerLen)
        val dstPort = readUInt16BE(data, headerLen + 2)
        val dataOffset = ((data[headerLen + 12].toInt() ushr 4) and 0x0F) * 4
        val payloadStart = headerLen + dataOffset
        return Parsed(
            version = version,
            src = src,
            dst = dst,
            transport = Transport.TCP,
            srcPort = srcPort,
            dstPort = dstPort,
            payloadLength = (totalLen - payloadStart).coerceAtLeast(0),
            preview = preview(data, payloadStart),
        )
    }

    private fun parseUdp(version: Int, src: String, dst: String, data: ByteArray, headerLen: Int, totalLen: Int): Parsed? {
        if (data.size < headerLen + 8) return null
        val srcPort = readUInt16BE(data, headerLen)
        val dstPort = readUInt16BE(data, headerLen + 2)
        val payloadStart = headerLen + 8
        return Parsed(
            version = version,
            src = src,
            dst = dst,
            transport = Transport.UDP,
            srcPort = srcPort,
            dstPort = dstPort,
            payloadLength = (totalLen - payloadStart).coerceAtLeast(0),
            preview = preview(data, payloadStart),
        )
    }

    private fun ipv4ToString(data: ByteArray, offset: Int): String = buildString {
        for (i in 0 until 4) {
            if (i > 0) append('.')
            append((data[offset + i].toInt() and 0xFF).toString())
        }
    }

    private fun ipv6ToString(data: ByteArray, offset: Int): String = buildString {
        for (i in 0 until 8) {
            if (i > 0) append(':')
            val word = readUInt16BE(data, offset + i * 2)
            append("%04x".format(word))
        }
    }

    private fun readUInt16BE(data: ByteArray, offset: Int): Int {
        if (offset + 1 >= data.size) return 0
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }

    private fun preview(data: ByteArray, offset: Int): ByteArray {
        val len = minOf(PREVIEW_BYTES, (data.size - offset).coerceAtLeast(0))
        return if (len <= 0) ByteArray(0) else data.copyOfRange(offset, offset + len)
    }

    const val PREVIEW_BYTES = 96
}

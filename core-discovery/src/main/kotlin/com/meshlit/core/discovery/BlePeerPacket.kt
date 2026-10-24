package com.meshlit.core.discovery

import java.nio.ByteBuffer
import java.util.UUID

/** 24 bytes + manufacturer-data overhead + flags fit the legacy 31-byte advertisement limit. */
object BlePeerPacket {
    const val MANUFACTURER_ID = 0x4D53
    fun encode(self: LocalPeerDescriptor): ByteArray {
        val address = self.host.split('.')
        require(address.size == 4 && address.all { it.toIntOrNull() in 0..255 }) { "BLE discovery needs a local IPv4 address" }
        require(self.port in 1..65535)
        val id = runCatching { UUID.fromString(self.nodeId) }.getOrElse { UUID.nameUUIDFromBytes(self.nodeId.toByteArray()) }
        return ByteBuffer.allocate(24).put(0x4D.toByte()).put(1.toByte())
            .putLong(id.mostSignificantBits).putLong(id.leastSignificantBits)
            .put(address.map { it.toInt().toByte() }.toByteArray()).putShort(self.port.toShort()).array()
    }
    fun decode(bytes: ByteArray): PeerAdvertisement? = runCatching {
        require(bytes.size == 24 && bytes[0] == 0x4D.toByte() && bytes[1] == 1.toByte())
        val buffer = ByteBuffer.wrap(bytes); buffer.position(2)
        val id = UUID(buffer.long, buffer.long).toString()
        val host = (0 until 4).joinToString(".") { (buffer.get().toInt() and 255).toString() }
        val port = buffer.short.toInt() and 65535; require(port > 0)
        PeerAdvertisement(id, host, port, "local_sandboxed", "", ttlSec = 60, transport = "ble")
    }.getOrNull()
}

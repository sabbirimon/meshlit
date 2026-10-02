package com.meshlit.core.net.capture

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for [MdnsCaptureListener]. Drives the listener
 * directly through `handleFrame()` so we never need a real socket
 * or a Robolectric runtime. The DNS frames are hand-built below —
 * small enough to read in a screenshot.
 */
class MdnsCaptureListenerTest {

    /**
     * Build a minimal mDNS response: answer section contains one
     * TXT record for `_meshlit._tcp.local.` with the given
     * `meshlit=` JSON attribute.
     */
    private fun buildMdnsResponseWithMeshlitTxt(
        jsonAttr: String,
        withMeshlitName: Boolean = true,
    ): ByteArray {
        val name = if (withMeshlitName) "_meshlit._tcp.local" else "_other._tcp.local"
        val qname = encodeDnsName(name)
        val txtAttr = "meshlit=$jsonAttr"
        // TXT record body: <len-prefixed string>
        val txtRd = ByteArray(1 + txtAttr.length)
        txtRd[0] = txtAttr.length.toByte()
        for (i in txtAttr.indices) txtRd[1 + i] = txtAttr[i].code.toByte()

        // Header: ID, flags(0x8400 = response + authoritative), QD=0,
        // AN=1, NS=0, AR=0
        val header = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), // ID
            0x84.toByte(), 0x00.toByte(), // flags
            0x00.toByte(), 0x00.toByte(), // QDCOUNT
            0x00.toByte(), 0x01.toByte(), // ANCOUNT
            0x00.toByte(), 0x00.toByte(), // NSCOUNT
            0x00.toByte(), 0x00.toByte(), // ARCOUNT
        )
        val answerHeader = byteArrayOf(
            0x00.toByte(), 0x10.toByte(), // TYPE = TXT (16)
            0x00.toByte(), 0x01.toByte(), // CLASS = IN (1)
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3c.toByte(), // TTL = 60
            (txtRd.size ushr 8).toByte(), (txtRd.size and 0xFF).toByte(), // RDLENGTH
        )
        return header + qname + answerHeader + txtRd
    }

    /** Wrap a UDP payload in an IPv4 + UDP datagram addressed to 5353. */
    private fun wrapInUdp(payload: ByteArray, srcPort: Int = 5353): ByteArray {
        val ip = ByteArray(20 + 8 + payload.size)
        // IPv4 header
        ip[0] = 0x45.toByte() // version=4, IHL=5
        ip[1] = 0x00.toByte() // DSCP
        val total = ip.size
        ip[2] = (total ushr 8).toByte()
        ip[3] = (total and 0xFF).toByte()
        ip[8] = 64.toByte() // TTL
        ip[9] = 17.toByte() // protocol = UDP
        // src = 192.168.1.42, dst = 224.0.0.251
        ip[12] = 192.toByte(); ip[13] = 168.toByte(); ip[14] = 1.toByte(); ip[15] = 42.toByte()
        ip[16] = 224.toByte(); ip[17] = 0.toByte(); ip[18] = 0.toByte(); ip[19] = 251.toByte()
        // UDP header
        val udpOff = 20
        ip[udpOff] = (srcPort ushr 8).toByte()
        ip[udpOff + 1] = (srcPort and 0xFF).toByte()
        ip[udpOff + 2] = (5353 ushr 8).toByte()
        ip[udpOff + 3] = (5353 and 0xFF).toByte()
        val udpLen = 8 + payload.size
        ip[udpOff + 4] = (udpLen ushr 8).toByte()
        ip[udpOff + 5] = (udpLen and 0xFF).toByte()
        // payload
        System.arraycopy(payload, 0, ip, udpOff + 8, payload.size)
        return ip
    }

    /** Encode a dotted DNS name into wire form. */
    private fun encodeDnsName(name: String): ByteArray {
        val labels = name.split('.')
        val out = ByteArray(name.length + 2)
        var i = 0
        for (label in labels) {
            out[i++] = label.length.toByte()
            for (c in label) out[i++] = c.code.toByte()
        }
        out[i] = 0
        return out
    }

    private fun listener(): MdnsCaptureListener =
        MdnsCaptureListener(scope = TestScope(UnconfinedTestDispatcher()))

    @Test
    fun `emits decoded record for meshlit ptr`() {
        val l = listener()
        val dns = buildMdnsResponseWithMeshlitTxt(
            """{"nodeId":"node-A","host":"192.168.1.42","port":8080,"fingerprint":"fp:abc"}"""
        )
        val record = l.handleFrame(wrapInUdp(dns))
        assertNotNull("a record should be returned", record)
        assertEquals("192.168.1.42", record!!.sourceIp)
        assertEquals(5353, record.sourcePort)
        val decoded = record.decoded
        assertNotNull("decoded meshlit adv expected", decoded)
        assertEquals("node-A", decoded!!.nodeId)
        assertEquals("192.168.1.42", decoded.host)
        assertEquals(8080, decoded.port)
        assertEquals("fp:abc", decoded.fingerprint)
    }

    @Test
    fun `ignores non meshlit ptr responses`() {
        val l = listener()
        val dns = buildMdnsResponseWithMeshlitTxt(
            """{"nodeId":"other","host":"10.0.0.1","port":8080,"fingerprint":"fp:x"}""",
            withMeshlitName = false,
        )
        val record = l.handleFrame(wrapInUdp(dns))
        assertNotNull("raw mDNS packet still emitted", record)
        assertNull("decoded should be null for non-meshlit", record!!.decoded)
    }

    @Test
    fun `multicast lock factory contract`() {
        val acquireCount = AtomicInteger(0)
        val releaseCount = AtomicInteger(0)
        val factory = MulticastLockFactory {
            acquireCount.incrementAndGet()
            AutoCloseable { releaseCount.incrementAndGet() }
        }
        val lock = factory.acquire()
        assertEquals(1, acquireCount.get())
        assertEquals(0, releaseCount.get())
        lock?.close()
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun `stop is idempotent and does not throw when never started`() {
        val l = listener()
        // Must not throw.
        l.stop()
        l.stop()
        assertTrue("records flow exists", l.records != null)
    }
}

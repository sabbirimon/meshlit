package com.meshlit.core.inference.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Tests for [RawTcpActivationServer]. The server binds a
 * `ServerSocket` on the requested port and exposes [boundPort]
 * so the FGS can include the live endpoint on `/v1/health`.
 *
 * Coverage:
 *  - `boundPort` returns 0 before `start()`.
 *  - After `start()` the server listens on the requested port.
 *  - `close()` makes the port reusable for the next test.
 *  - A peer can dial the bound port and the server accepts the
 *    socket (the channel callback fires).
 */
class RawTcpActivationServerTest {

    @Test
    fun `boundPort is 0 before start`() {
        val server = RawTcpActivationServer(0) { /* no-op */ }
        assertEquals(0, server.boundPort)
    }

    @Test
    fun `boundPort reflects the requested port after start`() {
        val port = pickFreePort()
        val server = RawTcpActivationServer(port) { /* no-op */ }
        server.start()
        try {
            assertEquals(port, server.boundPort)
        } finally {
            server.close()
        }
    }

    @Test
    fun `server accepts a peer dial and fires the channel callback`() {
        val receivedChannel=java.util.concurrent.atomic.AtomicReference<RawTcpActivationChannel?>()
        val accepted=java.util.concurrent.CountDownLatch(1)
        val server = RawTcpActivationServer(0) { ch ->
            receivedChannel.set(ch);accepted.countDown()
        }
        server.start()
        try {
            assertTrue("server failed to bind", server.boundPort > 0)
            // Dial the server from a JVM client. The server's
            // accept thread should fire the onChannel callback.
            val sock = Socket()
            sock.connect(InetSocketAddress("127.0.0.1", server.boundPort), 5_000)
            assertTrue("client failed to connect to ${server.boundPort}", sock.isConnected)
            assertTrue("channel callback did not fire within 5s",accepted.await(5,java.util.concurrent.TimeUnit.SECONDS))
            assertNotNull(receivedChannel.get())
            sock.close()
        } finally {
            server.close()
        }
    }

    @Test
    fun `close releases the port for reuse`() {
        val port = pickFreePort()
        val server = RawTcpActivationServer(port) { /* no-op */ }
        server.start()
        val firstBound = server.boundPort
        assertEquals(port, firstBound)
        server.close()
        // After close, boundPort is 0 again.
        assertEquals(0, server.boundPort)
        // Closing a ServerSocket wakes accept asynchronously. Linux may keep
        // its listener alive until that thread exits; require bounded reuse
        // rather than relying on the immediate scheduling seen on macOS.
        val server2 = RawTcpActivationServer(port) { /* no-op */ }
        try {
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2)
            do {
                server2.start()
                if (server2.boundPort == port) break
                Thread.sleep(10)
            } while (System.nanoTime() < deadline)
            assertEquals("closed listener did not release its port within 2s", port, server2.boundPort)
        } finally {
            server2.close()
        }
    }

    /**
     * Pick a port the OS will assign ephemerally. We bind a
     * throwaway ServerSocket on port 0, read the assigned port,
     * and close the socket so the next bind can reuse it. Not
     * race-free — the OS may grab the port between calls in
     * extreme cases — but it's enough for unit tests.
     */
    private fun pickFreePort(): Int {
        val s = java.net.ServerSocket(0)
        val p = s.localPort
        s.close()
        return p
    }
}

package com.meshlit.devices.studio

import com.meshlit.core.trust.PeerCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import kotlin.concurrent.thread

class StudioRelayTransportTest {
    @Test fun relayRejectsUnsecuredRemoteAndUnpinnedReconnectBeforeDial() {
        Socket().use { client ->
            for (origin in listOf("http://example.org", "https://user@example.org", "https://example.org/path", "https://example.org/?token=bad")) {
                assertThrows(IllegalArgumentException::class.java) { StudioWebSocket(client).open(origin) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                studioRequest(client, PeerCrypto.generate("test"), "Test", "http://127.0.0.1", "t", "0".repeat(20), null, false)
            }
        }
    }

    private fun frames(write: (DataOutputStream) -> Unit, read: (StudioWebSocket) -> Unit) {
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            val host = thread(isDaemon = true) {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    val headers = generateSequence { reader.readLine()?.takeIf(String::isNotEmpty) }.toList()
                    val key = headers.first { it.startsWith("Sec-WebSocket-Key: ") }.substringAfter(": ")
                    val digest = MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray())
                    val accept = Base64.getEncoder().encodeToString(digest)
                    val output = DataOutputStream(socket.getOutputStream())
                    output.writeBytes("HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: websocket\r\nSec-WebSocket-Accept: $accept\r\n\r\n")
                    write(output)
                    output.flush()
                }
            }
            Socket().use { socket ->
                val wire = StudioWebSocket(socket)
                wire.open("http://127.0.0.1:" + server.localPort)
                read(wire)
            }
            host.join(2000)
        }
    }

    @Test fun reconstructsFragmentedTlsMessages() {
        frames({ it.write(byteArrayOf(2, 2, 1, 2, 0x80.toByte(), 1, 3)) }) {
            assertArrayEquals(byteArrayOf(1, 2, 3), it.read(StudioWebSocket.BINARY))
        }
    }

    @Test fun rejectsOversizedTlsRecordBeforeReadingThePayload() {
        frames({
            it.writeByte(0x82)
            it.writeByte(127)
            it.writeLong(StudioWebSocket.LIMIT + 1L)
        }) { assertThrows(IllegalStateException::class.java) { it.read(StudioWebSocket.BINARY) } }
    }
}

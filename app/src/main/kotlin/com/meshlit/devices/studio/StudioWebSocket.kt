package com.meshlit.devices.studio

import okio.ByteString.Companion.toByteString
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** A private Studio relay connection. Bound frames before reading their payloads. */
internal class StudioWebSocket(private val connection: Socket) {
    private lateinit var input: DataInputStream
    private lateinit var output: DataOutputStream
    private val random = SecureRandom()

    fun open(origin: String) {
        val uri = URI(origin)
        require(uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath in listOf("", "/", "/v1/devices")) { "Enter the relay HTTPS origin." }
        val host = uri.host.removeSurrounding("[", "]")
        val secure = uri.scheme == "https"
        require(secure || (uri.scheme == "http" && host in listOf("127.0.0.1", "::1"))) { "Use HTTPS for a remote relay." }
        val port = if (uri.port == -1) (if (secure) 443 else 80) else uri.port
        require(port in 1..65535)
        connection.soTimeout = 12000
        connection.connect(InetSocketAddress(host, port), 10000)
        val socket = if (secure) {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            (factory.createSocket(connection, host, port, true) as SSLSocket).apply {
                sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                startHandshake()
            }
        } else connection
        input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        output = DataOutputStream(socket.getOutputStream())
        val key = ByteArray(16).also(random::nextBytes).toByteString().base64()
        val headers = "GET /v1/devices HTTP/1.1\r\nHost: " + uri.rawAuthority +
            "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: " + key +
            "\r\nSec-WebSocket-Version: 13\r\n\r\n"
        output.write(headers.toByteArray(Charsets.US_ASCII))
        output.flush()
        val response = ByteArrayOutputStream()
        var suffix = 0
        while (suffix != 0x0d0a0d0a) {
            check(response.size() < 8192) { "Relay response headers too large." }
            val byte = input.readUnsignedByte()
            response.write(byte)
            suffix = (suffix shl 8) or byte
        }
        val lines = response.toString("US-ASCII").split("\r\n")
        check(lines.first().matches(Regex("HTTP/1\\.1 101(?: .*)?"))) { "Relay declined the WebSocket upgrade." }
        val fields = lines.drop(1).filter { ':' in it }.associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
        val accept = MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()).toByteString().base64()
        check(fields["upgrade"].equals("websocket", true) &&
            fields["connection"].orEmpty().split(',').any { it.trim().equals("upgrade", true) } &&
            fields["sec-websocket-accept"] == accept && fields["sec-websocket-extensions"] == null &&
            fields["sec-websocket-protocol"] == null) { "Unexpected relay handshake." }
    }

    fun send(op: Int, bytes: ByteArray) {
        require(bytes.size <= LIMIT)
        val mask = ByteArray(4).also(random::nextBytes)
        output.writeByte(0x80 or op)
        when (bytes.size) {
            in 0..125 -> output.writeByte(0x80 or bytes.size)
            in 126..65535 -> { output.writeByte(0xfe); output.writeShort(bytes.size) }
            else -> { output.writeByte(0xff); output.writeLong(bytes.size.toLong()) }
        }
        output.write(mask)
        output.write(ByteArray(bytes.size) { (bytes[it].toInt() xor mask[it % 4].toInt()).toByte() })
        output.flush()
    }

    fun read(expected: Int): ByteArray {
        val contents = ByteArrayOutputStream()
        var started = false
        while (true) {
            val head = input.readUnsignedByte()
            val size = input.readUnsignedByte()
            val final = head and 0x80 != 0
            val op = head and 15
            check(head and 0x70 == 0 && size and 0x80 == 0) { "Invalid relay frame." }
            val length = when (size) {
                126 -> input.readUnsignedShort().toLong()
                127 -> input.readLong()
                else -> size.toLong()
            }
            val limit = if (op >= 8) 125 else if (expected == TEXT) 2048 else LIMIT
            check(length in 0L..limit.toLong() && (op >= 8 || contents.size() + length <= limit)) { "Relay frame too large." }
            if (op >= 8) check(final)
            val bytes = ByteArray(length.toInt()).also(input::readFully)
            when (op) {
                8 -> throw IOException("Relay disconnected. Check the token, device ID and host availability.")
                9 -> send(10, bytes)
                10 -> Unit
                else -> {
                    check(op == (if (started) 0 else expected)) { "Unexpected relay message." }
                    contents.write(bytes)
                    if (final) return contents.toByteArray()
                    started = true
                }
            }
        }
    }

    fun waitForApproval() { connection.soTimeout = 60000 }

    companion object {
        const val TEXT = 1
        const val BINARY = 2
        const val LIMIT = 128 * 1024
    }
}

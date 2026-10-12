package com.meshlit.devices

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.meshlit.core.common.EndpointProtocol
import com.meshlit.core.common.RemoteEndpoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Data embedded in a Meshlit pairing QR code.
 *
 * Wire format is a compact URI so generic QR scanners can display / copy
 * it even without Meshlit installed:
 *
 *     meshlit://pair?name=<urlencoded>&url=<urlencoded>&node=<hex>&tier=MID
 *
 * The same string can be pasted into the Add-device sheet. We deliberately
 * don't put API keys or private credentials in the QR — trust is established
 * by the receiving phone after it displays the decoded endpoint to the user.
 */
@Serializable
data class PairingPayload(
    val nodeName: String,
    val baseUrl: String,
    val nodeId: String,
    val capabilityTier: String,
    val publicKey: String = "",
) {
    fun encode(): String = buildString {
        append("meshlit://pair")
        append("?name=").append(enc(nodeName))
        append("&url=").append(enc(baseUrl))
        append("&node=").append(enc(nodeId))
        append("&tier=").append(enc(capabilityTier))
        if (publicKey.isNotBlank()) append("&key=").append(enc(publicKey))
    }

    fun toRemoteEndpoint(): RemoteEndpoint = validate().let { RemoteEndpoint(
        id = if (nodeId.isNotBlank()) "node-$nodeId" else UUID.randomUUID().toString(),
        name = nodeName.ifBlank { baseUrl },
        baseUrl = baseUrl.trimEnd('/'),
        protocol = EndpointProtocol.MESHLIT_SSE,
        allowInsecure = baseUrl.startsWith("http://"),
        trusted = false,
        addedAtMs = System.currentTimeMillis(),
        notes = if (publicKey.isBlank()) "" else "identity.key=$publicKey",
    ) }

    fun validate(): PairingPayload {
        require(nodeName.length <= 128 && nodeId.length <= 128 && capabilityTier.length <= 32) { "Pairing fields too long" }
        require(baseUrl.isNotBlank()) { "missing endpoint URL" }
        val uri = runCatching { java.net.URI(baseUrl.trim()) }.getOrElse { throw IllegalArgumentException("endpoint URL is not a valid URI", it) }
        require(uri.scheme?.lowercase() in setOf("http", "https")) { "endpoint URL scheme must be http or https" }
        require(!uri.host.isNullOrBlank()) { "endpoint URL is missing a host" }
        require(nodeId.isBlank() || NODE_ID_REGEX.matches(nodeId)) { "nodeId must be a UUID or public fingerprint; recovery phrases are private" }
        require(uri.userInfo == null && uri.fragment == null && uri.rawQuery == null) { "Endpoint must not contain credentials, query or fragment" }
        require(uri.port == -1 || uri.port in 1..65535) { "Invalid endpoint port" }
        require(uri.host !in setOf("0.0.0.0", "::", "[::]")) { "Use the device network address" }
        require(publicKey.length <= 1024) { "Identity key too long" }
        if (publicKey.isNotBlank()) {
            require(nodeId.isNotBlank()) { "Identity key needs a device ID" }
            com.meshlit.core.trust.PeerCrypto.fingerprint(publicKey)
        }
        return copy(baseUrl = baseUrl.trim().trimEnd('/'))
    }

    companion object {
        private val NODE_ID_REGEX = Regex("^(?:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|[0-9a-f]{64})$", RegexOption.IGNORE_CASE)
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(raw: String): PairingPayload {
            require(raw.length <= 8192) { "Pairing payload too large" }
            val trimmed = raw.trim()
            if (trimmed.startsWith("{")) {
                return json.decodeFromString(serializer(), trimmed).validate()
            }
            val uri = runCatching { java.net.URI(trimmed) }.getOrElse { throw IllegalArgumentException("URI is malformed", it) }
            if (uri.scheme.equals("meshlit", true) && uri.host.equals("join", true)) throw IllegalArgumentException("Use the Join Cluster screen for meshlit://join links")
            require(uri.scheme.equals("meshlit", true) && uri.host.equals("pair", true)) {
                "not a Meshlit pairing payload"
            }
            val q = parseQuery(uri.rawQuery ?: "")
            return PairingPayload(
                nodeName = q["name"].orEmpty(),
                baseUrl = q["url"].orEmpty(),
                nodeId = q["node"].orEmpty(),
                capabilityTier = q["tier"].orEmpty(),
                publicKey = q["key"].orEmpty(),
            ).validate()
        }

        private fun parseQuery(raw: String): Map<String, String> {
            val pairs = raw.split('&').filter { it.isNotBlank() }.map { piece ->
                val parts = piece.split('=', limit = 2)
                require(parts.size == 2) { "Invalid pairing query" }
                dec(parts[0]) to dec(parts[1])
            }
            require(pairs.map { it.first }.distinct().size == pairs.size) { "Duplicate pairing fields" }
            return pairs.toMap()
        }

        private fun enc(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())

        private fun dec(value: String): String =
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }
}

/** Pure-Java QR encoder backed by ZXing core. */
object QrCodec {
    fun encode(text: String, size: Int = 512): Bitmap {
        require(text.isNotBlank()) { "QR payload cannot be blank" }
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val offset = y * size
            for (x in 0 until size) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}

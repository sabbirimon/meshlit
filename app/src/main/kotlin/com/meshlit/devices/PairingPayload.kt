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
) {
    fun encode(): String = buildString {
        append("meshlit://pair")
        append("?name=").append(enc(nodeName))
        append("&url=").append(enc(baseUrl))
        append("&node=").append(enc(nodeId))
        append("&tier=").append(enc(capabilityTier))
    }

    fun toRemoteEndpoint(): RemoteEndpoint = RemoteEndpoint(
        id = if (nodeId.isNotBlank()) "node-$nodeId" else UUID.randomUUID().toString(),
        name = nodeName.ifBlank { baseUrl },
        baseUrl = baseUrl.trimEnd('/'),
        protocol = EndpointProtocol.MESHLIT_SSE,
        allowInsecure = baseUrl.startsWith("http://"),
        trusted = false,
        addedAtMs = System.currentTimeMillis(),
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(raw: String): PairingPayload {
            val trimmed = raw.trim()
            if (trimmed.startsWith("{")) {
                return json.decodeFromString(serializer(), trimmed)
            }
            val uri = java.net.URI(trimmed)
            require(uri.scheme == "meshlit" && uri.host == "pair") {
                "not a Meshlit pairing payload"
            }
            val q = parseQuery(uri.rawQuery ?: "")
            return PairingPayload(
                nodeName = q["name"].orEmpty(),
                baseUrl = q["url"].orEmpty(),
                nodeId = q["node"].orEmpty(),
                capabilityTier = q["tier"].orEmpty(),
            ).also { require(it.baseUrl.isNotBlank()) { "missing endpoint URL" } }
        }

        private fun parseQuery(raw: String): Map<String, String> = raw
            .split('&')
            .filter { it.contains('=') }
            .associate { piece ->
                val (key, value) = piece.split('=', limit = 2)
                dec(key) to dec(value)
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

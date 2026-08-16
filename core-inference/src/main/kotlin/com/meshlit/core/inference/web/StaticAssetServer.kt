package com.meshlit.core.inference.web

import android.content.Context
import fi.iki.elonen.NanoHTTPD

/**
 * Phase Hivemind-1 — minimal static-file server for the browser
 * chat UI. The assets live in `assets/web/` inside the APK and
 * are read on every request (cheap; files are < 50 KB total).
 *
 * Route map:
 *  - `GET /`             → `index.html`
 *  - `GET /chat.js`      → `chat.js`
 *  - `GET /style.css`    → `style.css`
 *  - `GET /favicon.svg`  → `favicon.svg`
 *  - `GET /qr.svg`       → generated SVG QR for the current URL
 *  - everything else     → 404 (so a typo doesn't accidentally
 *                          match an inference route)
 *
 * The MIME type is inferred from the file extension. The list is
 * deliberately tiny — we only ship five files. Adding a new asset
 * means adding one line to [mimeOf].
 *
 * No caching layer; the browser handles it via `Cache-Control:
 * max-age=300` set by the browser based on the 200 response. We
 * don't set `ETag`; if the user edits the assets, they ship a
 * new APK so cache invalidation is moot.
 */
class StaticAssetServer(
    private val context: Context,
    private val root: String = "web",
    private val publicBaseUrl: () -> String? = { null },
) {

    fun route(session: NanoHTTPD.IHTTPSession, uri: String): NanoHTTPD.Response? {
        if (session.method != NanoHTTPD.Method.GET) return null
        val path = uri.trimEnd('/').ifBlank { "/" }
        val asset = mapPath(path) ?: return null
        return try {
            // QR is generated on the fly — it doesn't live in assets/.
            if (asset == "qr.svg") {
                val target = publicBaseUrl() ?: "http://meshlit-master.local:8080/"
                val svg = QrEncoder.encodeSvg(target, sizePx = 256)
                NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    mimeOf(asset),
                    svg,
                )
            } else {
                val bytes = context.assets.open("$root/$asset").use { it.readBytes() }
                NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    mimeOf(asset),
                    java.io.ByteArrayInputStream(bytes),
                    bytes.size.toLong(),
                )
            }
        } catch (e: Throwable) {
            // Asset I/O, encoder, and other runtime failures all
            // surface here so a malformed request can't crash the
            // NanoHTTPD session.
            notFound(path)
        }
    }

    private fun mapPath(uri: String): String? = when (uri) {
        "/" -> "index.html"
        "/index.html" -> "index.html"
        "/chat.js" -> "chat.js"
        "/style.css" -> "style.css"
        "/favicon.svg" -> "favicon.svg"
        "/qr.svg" -> "qr.svg"
        else -> null
    }

    private fun mimeOf(asset: String): String = when {
        asset.endsWith(".html") -> "text/html; charset=utf-8"
        asset.endsWith(".js") -> "application/javascript; charset=utf-8"
        asset.endsWith(".css") -> "text/css; charset=utf-8"
        asset.endsWith(".svg") -> "image/svg+xml"
        else -> "application/octet-stream"
    }

    private fun notFound(path: String): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.NOT_FOUND,
            "text/plain; charset=utf-8",
            "asset not found: $path",
        )
    }
}
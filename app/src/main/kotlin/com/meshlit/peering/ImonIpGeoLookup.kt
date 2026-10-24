package com.meshlit.peering

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/** Optional client for the owner's IMON WordPress API. No lookup runs without a tap. */
data class IpGeoEstimate(
    val ip: String,
    val latitude: Double,
    val longitude: Double,
    val place: String,
    val source: String,
)

object ImonIpGeoLookup {
    suspend fun lookup(server: String, ip: String): IpGeoEstimate = withContext(Dispatchers.IO) {
        val literal = ip.trim()
        require(isPublicLiteral(literal)) { "Enter a public IPv4 or IPv6 address. Private LAN addresses cannot be geolocated by public IP." }
        val base = URL(server.trim().trimEnd('/'))
        require(base.protocol == "https" && base.host.isNotBlank() && base.userInfo == null && base.query == null && base.ref == null) {
            "Enter the HTTPS base URL of your IMON WordPress server."
        }
        val endpoint = URL(base.toString().trimEnd('/') + "/wp-json/privacy-checker/v1/lookup/ip?ip=" +
            java.net.URLEncoder.encode(literal, "UTF-8"))
        val connection = endpoint.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "MeshlitAndroid/2.0 (+https://github.com/sabbirimon/meshlit-v2)")
            require(connection.responseCode == 200) { "IMON lookup returned HTTP ${connection.responseCode}." }
            val body = StringBuilder()
            connection.inputStream.bufferedReader().use { reader ->
                val chunk = CharArray(1024)
                while (true) {
                    val count = reader.read(chunk)
                    if (count < 0) break
                    require(body.length + count <= 16_384) { "IMON response is too large." }
                    body.append(chunk, 0, count)
                }
            }
            val root = JSONObject(body.toString())
            val intel = root.optJSONObject("intel") ?: error("IMON did not return IP intelligence.")
            require(intel.optString("status") == "ok") { intel.optString("error", "IMON has no estimate for this IP.") }
            val source = intel.optString("source")
            require(source.isNotBlank() && source != "mock") { "IMON did not provide a real geo source." }
            val lat = intel.optDouble("latitude", Double.NaN)
            val lon = intel.optDouble("longitude", Double.NaN)
            require(lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0) {
                "IMON has no valid coordinates for this IP."
            }
            val place = listOf(intel.optString("city"), intel.optString("region"), intel.optString("country_name"))
                .filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { "Approximate region" }
            IpGeoEstimate(literal, lat, lon, place, source)
        } finally { connection.disconnect() }
    }

    internal fun isPublicLiteral(ip: String): Boolean {
        if (!ip.matches(Regex("[0-9A-Fa-f:.]{3,45}"))) return false
        if (':' !in ip && ip.count { it == '.' } != 3) return false
        return runCatching {
            val address = InetAddress.getByName(ip)
            val bytes = address.address
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress) return@runCatching false
            if (bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc) return@runCatching false
            if (bytes.size == 4) {
                val a = bytes[0].toInt() and 255
                val b = bytes[1].toInt() and 255
                if (a == 0 || a >= 224 || (a == 100 && b in 64..127) || (a == 192 && b == 0)) return@runCatching false
            }
            true
        }.getOrDefault(false)
    }
}

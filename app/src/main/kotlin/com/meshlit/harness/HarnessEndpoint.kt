package com.meshlit.harness

import java.net.URI

/** The launch URL is a credential: never persist, log, or include it in UI errors. */
internal class HarnessEndpoint private constructor(val endpoint: String, val origin: String, val launchUrl: String) {
    fun allowsNavigation(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.rawUserInfo == null && originOf(uri) == origin && uri.scheme in setOf("http", "https")
    }.getOrDefault(false)

    companion object {
        fun parse(value: String): HarnessEndpoint {
            require(value.length in 8..2048 && value.none { it.isWhitespace() || it == '\\' })
            val uri = URI(value)
            require(!uri.host.isNullOrEmpty() && uri.rawUserInfo == null && uri.rawFragment == null)
            require(uri.port == -1 || uri.port in 1..65535)
            require(uri.scheme == "https" || uri.scheme == "http" && uri.host in setOf("127.0.0.1", "[::1]"))
            val path = uri.rawPath.ifEmpty { "/" }
            require(Regex("/(?:[A-Za-z0-9_-]+/)*").matches(path))
            val query = uri.rawQuery
            require(query == null || Regex("token=[A-Za-z0-9_-]{16,512}").matches(query))
            val origin = originOf(uri)
            val endpoint = origin + path
            return HarnessEndpoint(endpoint, origin, endpoint + if (query == null) "" else "?$query")
        }

        private fun originOf(uri: URI): String {
            val port = if (uri.port == -1 || uri.scheme == "https" && uri.port == 443 || uri.scheme == "http" && uri.port == 80) "" else ":${uri.port}"
            return "${uri.scheme}://${uri.host.lowercase()}$port"
        }
    }
}

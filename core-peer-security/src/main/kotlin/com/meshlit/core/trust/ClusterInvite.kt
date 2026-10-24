package com.meshlit.core.trust
import java.net.URI
import java.net.URLEncoder
import java.net.URLDecoder

/** Out-of-band invite pins the relay identity; its short-lived code authorizes enrollment only. */
data class ClusterInvite(val url: String, val code: String, val nodeId: String, val publicKey: String, val expiresAtMs: Long) {
    fun encode(): String {
        fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
        return "meshlit://join?url=${enc(url)}&code=$code&node=${enc(nodeId)}&key=$publicKey&expires=$expiresAtMs"
    }
    companion object {
        fun decode(raw: String, now: Long = System.currentTimeMillis()): ClusterInvite {
            require(raw.length <= 4096)
            val uri = URI(raw.trim()); require(uri.scheme == "meshlit" && uri.host == "join")
            val pairs = uri.rawQuery.orEmpty().split('&').map { p -> val kv = p.split('=', limit = 2); require(kv.size == 2); kv[0] to URLDecoder.decode(kv[1], "UTF-8") }
            require(pairs.map { it.first }.distinct().size == pairs.size)
            val query = pairs.toMap()
            val invite = ClusterInvite(query["url"].orEmpty(), query["code"].orEmpty(), query["node"].orEmpty(), query["key"].orEmpty(), query["expires"]?.toLongOrNull() ?: 0)
            val address = URI(invite.url)
            require(address.scheme in setOf("http", "https") && address.host != null && address.userInfo == null && address.rawQuery == null && address.fragment == null)
            require(invite.nodeId.length in 1..128 && invite.code.matches(Regex("[0-9]{6}")) && invite.publicKey.length <= 1024)
            PeerCrypto.fingerprint(invite.publicKey)
            require(invite.expiresAtMs > now && invite.expiresAtMs <= now + 630_000) { "Invite expired or invalid" }
            return invite
        }
    }
}

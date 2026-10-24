package com.meshlit.peering
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.meshlit.core.trust.HandshakeResponse
import java.net.URI
class PeerSessionStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(context, "mesh_peer_sessions",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    private fun origin(url: String): String { val uri = URI(url); return "${uri.scheme}://${uri.rawAuthority}${uri.path.orEmpty().trimEnd('/')}" }
    fun save(url: String, response: HandshakeResponse) {
        val key = origin(url)
        check(prefs.edit().putString("$key.token", response.tokenHex).putLong("$key.expiry", response.expiresAtMs).commit())
    }
    fun tokenFor(url: String): String? {
        val key = origin(url)
        return if (prefs.getLong("$key.expiry", 0) > System.currentTimeMillis()) prefs.getString("$key.token", null) else null
    }
    fun remove(url: String) { val key = origin(url); prefs.edit().remove("$key.token").remove("$key.expiry").apply() }
}

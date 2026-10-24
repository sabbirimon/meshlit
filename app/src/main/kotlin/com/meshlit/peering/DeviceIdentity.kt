package com.meshlit.peering

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.meshlit.core.trust.*

/** Exportable key material is encrypted at rest with an Android Keystore master key. */
class DeviceIdentity(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(context, "mesh_identity",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    @Synchronized fun get(nodeId: String): RecoverableIdentity {
        require(nodeId.isNotBlank()) { "Device identity is starting" }
        val raw = prefs.getString("identity", null)
        if (raw != null) return PeerCrypto.json.decodeFromString(RecoverableIdentity.serializer(), raw)
        return PeerCrypto.generate(nodeId).also(::save)
    }
    @Synchronized fun restoredNodeId(): String? = prefs.getString("identity", null)?.let {
        PeerCrypto.json.decodeFromString(RecoverableIdentity.serializer(), it).nodeId
    }
    @Synchronized fun save(identity: RecoverableIdentity) {
        check(prefs.edit().putString("identity", PeerCrypto.json.encodeToString(RecoverableIdentity.serializer(), identity)).commit()) {
            "Could not persist device identity"
        }
    }
}

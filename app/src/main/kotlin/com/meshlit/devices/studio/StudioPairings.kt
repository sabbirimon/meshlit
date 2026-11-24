package com.meshlit.devices.studio

import android.content.Context
import com.meshlit.core.trust.EncryptedCredentialStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val STUDIO_RELAY_ORIGIN = "https://meshlit-relay.imon.com.bd"

@Serializable
internal data class SavedStudioPeer(val id: String, val key: String, val node: String, val name: String)

internal class StudioPairings(context: Context) {
    private val store = EncryptedCredentialStore(context.applicationContext, "studio-relay-pairings")

    fun token(): String? = store.get("access")
    fun peers(): List<SavedStudioPeer> = store.list().filter { it.startsWith("peer:") }.mapNotNull {
        store.get(it)?.let { value -> Json.decodeFromString(SavedStudioPeer.serializer(), value) }
    }.sortedBy { it.name.lowercase() }

    fun save(peer: SavedStudioPeer, token: String) = synchronized(lock) {
        val previous = peers()
        val existing = previous.find { it.id == peer.id }
        require(existing == null || (existing.key == peer.key && existing.node == peer.node)) {
            "The saved desktop identity changed."
        }
        require(existing != null || previous.size < 64) { "Forget an unused desktop before adding another." }
        store.updateCommitted(mapOf("peer:${peer.id}" to Json.encodeToString(SavedStudioPeer.serializer(), peer), "access" to token))
    }

    fun forget(id: String) = synchronized(lock) { store.updateCommitted(mapOf("peer:$id" to null)) }
    fun forgetToken() = store.updateCommitted(mapOf("access" to null))

    private companion object { val lock = Any() }
}

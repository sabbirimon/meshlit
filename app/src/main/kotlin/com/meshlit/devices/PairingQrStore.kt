package com.meshlit.devices

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** Local copies of public pairing details. Saving a QR never approves its device. */
class PairingQrStore(context: Context) {
    @Serializable
    data class SavedQr(
        val id: String,
        val payload: PairingPayload,
        val savedAtMs: Long,
    ) {
        val temporary: Boolean get() = payload.expiresAtEpochMs != null
        val expired: Boolean get() = payload.expiresAtEpochMs?.let { it <= System.currentTimeMillis() } ?: false
    }

    private val preferences = context.applicationContext.getSharedPreferences("saved-pairing-qrs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(read())
    val entries: StateFlow<List<SavedQr>> = state

    @Synchronized fun save(payload: PairingPayload): SavedQr {
        val validated = payload.validate()
        val current = state.value
        current.firstOrNull { it.payload == validated }?.let { return it }
        require(current.size < 32) { "Up to 32 pairing QRs can be saved" }
        val item = SavedQr(UUID.randomUUID().toString(), validated, System.currentTimeMillis())
        persist(current + item)
        return item
    }

    @Synchronized fun delete(id: String) {
        val next = state.value.filterNot { it.id == id }
        if (next.size != state.value.size) persist(next)
    }

    private fun persist(items: List<SavedQr>) {
        val encoded = json.encodeToString(items)
        require(encoded.length <= 256_000) { "Saved pairing QRs are too large" }
        check(preferences.edit().putString("entries", encoded).commit()) { "Pairing QRs could not be saved" }
        state.value = items
    }

    private fun read(): List<SavedQr> = runCatching {
        val raw = preferences.getString("entries", "[]") ?: "[]"
        require(raw.length <= 256_000) { "Saved QR data is too large" }
        json.decodeFromString<List<SavedQr>>(raw).take(32)
    }.getOrDefault(emptyList())
}

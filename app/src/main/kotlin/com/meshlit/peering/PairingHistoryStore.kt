package com.meshlit.peering

import android.content.Context
import com.meshlit.core.trust.PairingHistoryEvent
import com.meshlit.notifications.NotificationCategory
import com.meshlit.notifications.NotificationCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Bounded local audit of pairing requests. Records identities, never keys or session tokens. */
class PairingHistoryStore(context: Context, private val notifications: NotificationCenter,
    private val scope: CoroutineScope) {
    @Serializable data class Entry(val nodeId: String, val fingerprint: String, val tier: String,
        val status: String, val atMs: Long)

    private val prefs = context.applicationContext.getSharedPreferences("pairing-history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(read())
    val entries: StateFlow<List<Entry>> = state
    private val lastReadState = MutableStateFlow(prefs.getLong("last_read_at", 0L))
    val lastReadAt: StateFlow<Long> = lastReadState
    private val alertsState = MutableStateFlow(prefs.getBoolean("alerts", true))
    val alertsEnabled: StateFlow<Boolean> = alertsState
    private var lastAlertMs = 0L
    private var lastConnectedAlertMs = 0L

    @Synchronized fun setAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("alerts", enabled).apply()
        alertsState.value = enabled
    }

    @Synchronized fun markRead() {
        val latest = state.value.maxOfOrNull { it.atMs } ?: return
        prefs.edit().putLong("last_read_at", latest).apply()
        lastReadState.value = latest
    }

    @Synchronized fun clear() {
        prefs.edit().remove("entries").apply()
        state.value = emptyList()
    }

    @Synchronized fun record(event: PairingHistoryEvent) {
        if (event.nodeId.length !in 1..128 || event.fingerprint.length > 128 || event.requestedTier.length > 32) return
        val entry = Entry(event.nodeId, event.fingerprint, event.requestedTier, event.status.take(32), event.atMs)
        val updated = (listOf(entry) + state.value).take(100)
        val encoded = json.encodeToString(updated)
        if (encoded.length > 100_000) return
        prefs.edit().putString("entries", encoded).apply()
        state.value = updated
        if (entry.status == "waiting" && alertsState.value && event.atMs - lastAlertMs >= 60_000) {
            lastAlertMs = event.atMs
            scope.launch {
                notifications.post(NotificationCategory.SECURITY_ALERT, "Device wants to pair",
                    "${entry.nodeId.take(12)} is waiting. Open Devices → Notifications to review.")
            }
        }
        if (entry.status == "connected" && alertsState.value && event.atMs - lastConnectedAlertMs >= 60_000) {
            lastConnectedAlertMs = event.atMs
            scope.launch {
                notifications.post(NotificationCategory.SECURITY_ALERT, "Device connected",
                    "${entry.nodeId.take(12)} completed a verified pairing session. Review it in Devices.")
            }
        }
    }

    private fun read(): List<Entry> = runCatching {
        val raw = prefs.getString("entries", "[]") ?: "[]"
        require(raw.length <= 100_000)
        json.decodeFromString<List<Entry>>(raw).take(100)
    }.getOrDefault(emptyList())
}

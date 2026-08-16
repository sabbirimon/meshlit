package com.meshlit.notifications

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Ring buffer of the last N notification posts the user
 * actually received. Backs the new "History" section in
 * `NotificationsSettingsScreen` so the user can see what got
 * posted (and what got silently dropped) over the past few
 * minutes.
 *
 * Design notes:
 *  - **In-memory primary**, persisted via JSON to a single
 *    DataStore key (`notif.history`). The buffer is small
 *    (cap 20) so the JSON is tiny and a single `edit` is
 *    cheap.
 *  - **FIFO eviction** — the oldest entry is dropped on the
 *    21st push. Order is preserved (oldest first).
 *  - **Pure** — no Android imports. The screen calls
 *    [record] / [recent] / [clear] and writes the resulting
 *    JSON back to the repo via `setNotifHistory(json)`.
 *
 * Why not LiveData or a database: the Settings screen needs
 * reactive reads but writes are infrequent (≤ 1/sec). A JSON
 * string in DataStore round-trips fast enough and survives
 * process death without a Room migration.
 */
class NotificationHistoryLog(
    private val cap: Int = DEFAULT_CAP,
    private val json: Json = DEFAULT_JSON,
) {
    private val listSerializer = ListSerializer(Entry.serializer())

    /** One recorded post or drop. */
    @Serializable
    data class Entry(
        /** Epoch millis when the post was attempted. */
        val atMs: Long,
        val categoryId: String,
        val title: String,
        val outcome: String,
    )

    /**
     * Returns the most recent [limit] entries, oldest first.
     * `limit == 0` returns the whole buffer.
     */
    fun recent(rawJson: String, limit: Int = cap): List<Entry> {
        val list = decode(rawJson)
        val slice = if (limit <= 0 || limit >= list.size) list
                    else list.takeLast(limit)
        return slice
    }

    /**
     * Append an entry, drop oldest if the buffer exceeds [cap].
     * Returns the JSON to write back to DataStore.
     */
    fun append(rawJson: String, entry: Entry): String {
        val list = decode(rawJson).toMutableList()
        list.add(entry)
        while (list.size > cap) list.removeAt(0)
        return encode(list)
    }

    /** Wipe the history. Returns "[]". */
    fun clear(): String = encode(emptyList())

    private fun decode(rawJson: String): List<Entry> {
        if (rawJson.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(listSerializer, rawJson)
        }.getOrDefault(emptyList())
    }

    private fun encode(list: List<Entry>): String =
        json.encodeToString(listSerializer, list)

    companion object {
        const val DEFAULT_CAP = 20

        /**
         * Project-wide JSON config. Matches the one used by
         * `SettingsRepository` so history entries survive a
         * round-trip through DataStore.
         */
        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
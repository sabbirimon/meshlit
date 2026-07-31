package com.meshlit.notifications

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Per-category notification preferences, persisted in DataStore.
 *
 * The OS channel importance (set in [NotificationCenter.applyChannel])
 * is the source of truth for "do we even post a notification?"
 * — the system handles muting. These prefs are *additional* filters
 * on top, so a user who disables the OS channel can still flip this
 * back on and have it work, and vice versa.
 *
 * Why two layers? Because:
 *  - The OS channel importance determines whether a notification
 *    appears at all on a freshly-installed app (channels are pre-set).
 *  - The user might want "this category muted in-app but the OS still
 *    tracks it in history for support cases".
 */
class NotificationPreferences(private val context: Context) {

    private val dataStore = context.notificationPrefsDataStore

    /**
     * Per-category preference. Each one holds:
     *  - enabled: do we post a notification, or silently drop?
     *  - importanceOverride: -1 means "use the OS channel default";
     *    otherwise an int from NotificationManager.IMPORTANCE_*.
     */
    data class CategoryPrefs(
        val enabled: Boolean = true,
        val importanceOverride: Int = -1,
        val allowSound: Boolean = true,
        val allowVibration: Boolean = true,
        val showBadge: Boolean = true,
    )

    val flow: Flow<Map<NotificationCategory, CategoryPrefs>> =
        dataStore.data.map { prefs ->
            NotificationCategory.entries.associateWith { cat ->
                CategoryPrefs(
                    enabled = prefs[Keys.enabled(cat)] ?: true,
                    importanceOverride = prefs[Keys.importance(cat)] ?: -1,
                    allowSound = prefs[Keys.sound(cat)] ?: true,
                    allowVibration = prefs[Keys.vibration(cat)] ?: true,
                    showBadge = prefs[Keys.badge(cat)] ?: true,
                )
            }
        }

    suspend fun setEnabled(category: NotificationCategory, enabled: Boolean) {
        dataStore.edit { it[Keys.enabled(category)] = enabled }
    }

    suspend fun setImportanceOverride(category: NotificationCategory, importance: Int) {
        dataStore.edit { it[Keys.importance(category)] = importance }
    }

    suspend fun setSound(category: NotificationCategory, allow: Boolean) {
        dataStore.edit { it[Keys.sound(category)] = allow }
    }

    suspend fun setVibration(category: NotificationCategory, allow: Boolean) {
        dataStore.edit { it[Keys.vibration(category)] = allow }
    }

    suspend fun setBadge(category: NotificationCategory, allow: Boolean) {
        dataStore.edit { it[Keys.badge(category)] = allow }
    }

    suspend fun reset(category: NotificationCategory) {
        dataStore.edit { prefs ->
            prefs.remove(Keys.enabled(category))
            prefs.remove(Keys.importance(category))
            prefs.remove(Keys.sound(category))
            prefs.remove(Keys.vibration(category))
            prefs.remove(Keys.badge(category))
        }
    }

    private object Keys {
        fun enabled(c: NotificationCategory) =
            booleanPreferencesKey("notif_${c.channelId}_enabled")
        fun importance(c: NotificationCategory) =
            intPreferencesKey("notif_${c.channelId}_importance")
        fun sound(c: NotificationCategory) =
            booleanPreferencesKey("notif_${c.channelId}_sound")
        fun vibration(c: NotificationCategory) =
            booleanPreferencesKey("notif_${c.channelId}_vibration")
        fun badge(c: NotificationCategory) =
            booleanPreferencesKey("notif_${c.channelId}_badge")
    }
}

private val Context.notificationPrefsDataStore: androidx.datastore.core.DataStore<Preferences>
        by preferencesDataStore(name = "meshlit_notification_prefs")
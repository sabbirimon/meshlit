package com.meshlit.mcp

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meshlit.core.mcp.UserMcpServerStore
import kotlinx.coroutines.flow.first

/**
 * Production [UserMcpServerStore.Persistence] backed by a single
 * DataStore Preferences key holding a JSON-encoded list of
 * [com.meshlit.core.mcp.UserMcpServer].
 *
 * The whole catalog lives under `stringPreferencesKey("user_servers_json")`
 * — atomic, one DataStore write per upsert/remove, the pool reads the
 * full set on construction. For a typical user with ≤ 10 entries the
 * payload is well under a kilobyte.
 */
class DataStoreUserMcpServerPersistence(
    private val context: Context,
) : UserMcpServerStore.Persistence {

    private val Context.userStore by preferencesDataStore(name = "meshlit_user_mcp_servers")

    override suspend fun read(): String? {
        val prefs = context.userStore.data.first()
        return prefs[KEY]
    }

    override suspend fun write(value: String) {
        context.userStore.edit { it[KEY] = value }
    }

    private companion object {
        val KEY = stringPreferencesKey("user_servers_json")
    }
}

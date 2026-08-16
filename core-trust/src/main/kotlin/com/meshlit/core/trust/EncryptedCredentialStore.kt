package com.meshlit.core.trust

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Android Keystore-backed credential store. Wraps
 * [EncryptedSharedPreferences] with a 256-bit AES/GCM master key.
 *
 * The master key is hardware-backed on devices with a TEE or
 * StrongBox. On older devices the key lives in software but is
 * still bound to the app's signing certificate, so credential
 * bytes cannot be read by another app or by `adb backup`.
 *
 * Usage:
 * ```
 * val store = EncryptedCredentialStore(context, "my_prefs")
 * store.put("api_token", "nara-…")
 * val token = store.get("api_token")          // "nara-…"
 * store.list().forEach { key -> … }          // returns all keys
 * store.remove("api_token")                   // wipes the entry
 * ```
 *
 * The store is process-safe — concurrent reads / writes from
 * different coroutine scopes see consistent values because the
 * underlying [SharedPreferences] handles synchronization.
 */
open class EncryptedCredentialStore(
    context: Context,
    prefsName: String = "encrypted_credentials",
) {
    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        prefsName,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun get(key: String): String? = prefs.getString(key, null)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    /** All keys currently in the store. */
    fun list(): Set<String> = prefs.all.keys

    /** Wipe every entry. Use sparingly — primarily for sign-out. */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
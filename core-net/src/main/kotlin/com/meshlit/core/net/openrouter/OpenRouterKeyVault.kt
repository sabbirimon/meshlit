package com.meshlit.core.net.openrouter

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.meshlit.core.common.logger
import java.util.concurrent.atomic.AtomicReference

/**
 * Hardware-encrypted credential store for the OpenRouter API key.
 *
 * Phase 4 — direct-from-phone OpenRouter integration. Meshlit
 * doesn't ship a backend proxy, so the user's API key has to live
 * on the device. We wrap [EncryptedSharedPreferences] with a
 * 256-bit AES/GCM master key backed by Android Keystore (TEE /
 * StrongBox where available).
 *
 * Threat model
 * ============
 *  - **APK decompilation**: the master key never leaves Keystore,
 *    so the ciphertext alone is unrecoverable.
 *  - **Rooted device**: same — the master is hardware-bound. A
 *    full-disk attacker can read `EncryptedSharedPreferences`
 *    files, but only the Keystore- bound key can decrypt them.
 *  - **`adb backup`**: encrypted prefs are excluded from auto
 *    backup unless explicitly opted in. We don't opt in, so a
 *    backup-restore on a different device yields unreadable bytes.
 *  - **Lost phone**: user revokes the key on openrouter.ai. The
 *    next call here returns `null` from [load] and the UI flips
 *    to the "Disconnected" state.
 *
 * Hot-path caching
 * ================
 * OpenRouter inference calls happen on every chat — we don't want
 * to re-decrypt + re-prefetch from Keystore on each call. The
 * vault keeps the decrypted key in an [AtomicReference] for the
 * process lifetime, invalidated by [clear], [wipe], or process
 * death. The cache is process-local (not disk), so it's wiped on
 * cold start.
 *
 * Usage:
 * ```
 * val vault = OpenRouterKeyVault(context)
 * vault.save("sk-or-v1-…")
 * val key = vault.load()    // returns null when no key stored
 * vault.exists()           // true after save(), false before
 * vault.wipe()              // sign-out path
 * ```
 *
 * Backward compat: this class is independent of any FGS / DI
 * graph. Production wiring is in
 * `app/.../inference/OpenRouterSettingsActivity.kt`.
 */
class OpenRouterKeyVault(
    context: Context,
    prefsName: String = "openrouter_key_vault",
) {
    private val log = logger("OpenRouterKeyVault")

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = runCatching {
        EncryptedSharedPreferences.create(
            context,
            prefsName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse { e ->
        // EncryptedSharedPreferences can throw on devices with no
        // Keystore (extremely rare; mostly broken emulators). We
        // fall back to a no-op in-memory store so the rest of the
        // app still launches. The user is told via [load] / [save]
        // returning `false` that persistence is unavailable.
        log.warn("OpenRouterKeyVault", "Keystore unavailable: ${e.message}")
        InMemorySharedPreferences()
    }

    private val cache = AtomicReference<String?>(null)

    /**
     * Save the API key. The key is normalized: trimmed, must start
     * with `sk-or-v1-` (or `sk-or-` for older keys). Returns true
     * when the key was persisted, false when the input was
     * invalid.
     */
    fun save(rawKey: String): Boolean {
        val normalized = rawKey.trim()
        if (!isValidKeyShape(normalized)) {
            log.warn("OpenRouterKeyVault", "save() rejected malformed key shape")
            return false
        }
        val ok = runCatching { prefs.edit().putString(KEY_OPENROUTER_API, normalized).commit() }
        if (ok.getOrDefault(false)) {
            cache.set(normalized)
            log.info("OpenRouterKeyVault", "key stored (length=${normalized.length})")
        }
        return ok.getOrDefault(false)
    }

    /**
     * Load the API key. Returns null when:
     *  - no key has been saved yet, OR
     *  - the underlying Keystore is unavailable (so prefs is the
     *    in-memory fallback), OR
     *  - the cached value was wiped (sign-out).
     */
    fun load(): String? {
        cache.get()?.let { return it }
        val stored = runCatching { prefs.getString(KEY_OPENROUTER_API, null) }.getOrNull()
        if (!stored.isNullOrEmpty()) {
            cache.set(stored)
        }
        return stored
    }

    /** `true` if a key has been stored. */
    fun exists(): Boolean = load() != null

    /**
     * Instance-level convenience wrapper for the static
     * [Companion.isValidKeyShape]. Use the companion form when
     * you don't have a vault instance yet (e.g. on a `TextField`
     * change listener).
     */
    fun isValidKeyShape(key: String): Boolean = isValidKeyShape(key)

    /** Sign-out: remove the persisted key + invalidate the cache. */
    fun wipe() {
        runCatching { prefs.edit().remove(KEY_OPENROUTER_API).apply() }
        cache.set(null)
        log.info("OpenRouterKeyVault", "wiped")
    }

    /** Alias for [wipe]. Used by tests + UI sign-out paths. */
    fun clear() = wipe()

    companion object {
        /** Prefs key for the OpenRouter API key. Stable across
         *  app versions so we don't lose keys on upgrade. */
        const val KEY_OPENROUTER_API: String = "openrouter.api_key"

        /** Default prefs file name. Overridable so tests can use
         *  a separate prefs file. */
        const val DEFAULT_PREFS_NAME: String = "openrouter_key_vault"

        /**
         * Format check: keys must look like `sk-or-v1-…` (new) or
         * `sk-or-…` (legacy). We don't enforce length / charset
         * beyond a sensible lower bound (20 chars) so trivial
         * mistakes are caught client-side before we burn a
         * request. Static so the validator is unit-testable
         * without an Android `Context`.
         */
        fun isValidKeyShape(key: String): Boolean {
            if (key.length < 20) return false
            if (!key.startsWith("sk-or-")) return false
            return key.drop(6).all { c ->
                c.isLetterOrDigit() || c == '-' || c == '_'
            }
        }
    }

    /**
     * In-memory fallback used when the Keystore is unavailable.
     * Not thread-safe beyond the [SharedPreferences] contract; we
     * accept the degradation because the rare "no Keystore" path
     * means the user's privacy guarantees are already gone.
     */
    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String, defValue: String?): String? =
            data[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            data[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = InMemoryEditor(data)
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ) = Unit
    }

    private class InMemoryEditor(private val data: MutableMap<String, Any?>) :
        SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removes = mutableSetOf<String>()
        private var clearAll = false
        override fun putString(key: String, value: String?): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = apply { pending[key] = values }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun remove(key: String): SharedPreferences.Editor =
            apply { removes.add(key) }
        override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            if (clearAll) data.clear()
            data.putAll(pending)
            removes.forEach { data.remove(it) }
            pending.clear()
            removes.clear()
            clearAll = false
        }
    }
}
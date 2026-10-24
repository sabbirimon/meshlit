package com.meshlit.legal

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first

/** Installation-local acceptance, excluded from device backup; never implies optional consent. */
class LegalAgreementStore(context: Context, name: String = "legal-agreement") {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    fun accepted(): Boolean = prefs.getString("terms", null) == VERSION &&
        prefs.getString("privacy", null) == VERSION && prefs.getLong("accepted-at", 0) > 0
    fun acceptedAt(): Long? = prefs.getLong("accepted-at", 0).takeIf { accepted() && it > 0 }
    fun accept(terms: Boolean, privacy: Boolean) {
        require(terms && privacy) { "Both agreements must be accepted explicitly" }
        check(prefs.edit().putString("terms", VERSION).putString("privacy", VERSION)
            .putLong("accepted-at", System.currentTimeMillis()).commit()) { "Agreement could not be saved. Retry." }
    }
    suspend fun awaitAccepted() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(accepted()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(accepted())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.first { it }
    companion object { const val VERSION = "2026-10-09.2" }
}

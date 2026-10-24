package com.meshlit.peering

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.round

/** Owner-set map pin. Disabled by default; no location is shared until explicitly enabled. */
data class HostMapPin(val latitude: Double, val longitude: Double)

class HostLocationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("host-map-location", Context.MODE_PRIVATE)
    private val enabledState = MutableStateFlow(prefs.getBoolean("enabled", false))
    val enabled = enabledState.asStateFlow()
    private val pinState = MutableStateFlow(readPin())
    val pin = pinState.asStateFlow()

    @Synchronized fun setEnabled(value: Boolean) {
        check(prefs.edit().putBoolean("enabled", value).commit()) { "Could not save map visibility" }
        enabledState.value = value
    }

    @Synchronized fun setPin(latitude: Double, longitude: Double) {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
        check(prefs.edit().putLong("latitude", latitude.toBits()).putLong("longitude", longitude.toBits())
            .putBoolean("has_pin", true).commit()) { "Could not save map location" }
        pinState.value = HostMapPin(latitude, longitude)
    }

    @Synchronized fun clearPin() {
        check(prefs.edit().remove("latitude").remove("longitude").putBoolean("has_pin", false).commit()) {
            "Could not clear map location"
        }
        pinState.value = null
    }

    fun sharedPin(): HostMapPin? = if (enabledState.value) pinState.value?.let {
        HostMapPin(round(it.latitude * 100) / 100, round(it.longitude * 100) / 100)
    } else null

    private fun readPin(): HostMapPin? = if (prefs.getBoolean("has_pin", false)) {
        val lat = Double.fromBits(prefs.getLong("latitude", 0L))
        val lon = Double.fromBits(prefs.getLong("longitude", 0L))
        if (lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0)
            HostMapPin(lat, lon) else null
    } else null
}

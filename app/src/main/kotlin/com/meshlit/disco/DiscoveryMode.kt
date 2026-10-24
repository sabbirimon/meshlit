package com.meshlit.disco

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class DiscoveryMode { AUTO, ON, OFF }

/** AUTO scans while the main activity is visible; ON scans for this app process; OFF stops it. */
class DiscoveryModeStore(context:Context) {
    private val prefs=context.applicationContext.getSharedPreferences("mesh-discovery-mode",Context.MODE_PRIVATE)
    private val mutable=MutableStateFlow(runCatching { DiscoveryMode.valueOf(prefs.getString("mode",null) ?: "AUTO") }.getOrDefault(DiscoveryMode.AUTO))
    val mode:StateFlow<DiscoveryMode> = mutable
    private val agentMutable=MutableStateFlow(prefs.getBoolean("agent-allowed",false))
    val agentAllowed:StateFlow<Boolean> = agentMutable
    fun saveHuman(value:DiscoveryMode) {
        check(prefs.edit().putString("mode",value.name).commit()) { "Discovery mode could not be saved" }
        mutable.value=value
    }
    fun allowAgentHuman(allowed:Boolean) {
        check(prefs.edit().putBoolean("agent-allowed",allowed).commit()) { "Discovery delegation could not be saved" }
        agentMutable.value=allowed
    }
    fun saveAgent(value:DiscoveryMode) {
        check(agentMutable.value) { "Human discovery mode delegation is off" }
        saveHuman(value)
    }
}

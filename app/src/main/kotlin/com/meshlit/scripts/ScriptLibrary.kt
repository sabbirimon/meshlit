package com.meshlit.scripts

import com.meshlit.core.common.ConfigScript
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process script library. Today's design stores scripts as
 * immutable snapshots in memory; a DataStore-backed persistence
 * layer will arrive once we know what the editor saves look like.
 *
 * The runner + the `ScriptsScreen` both read from the same instance.
 */
class ScriptLibrary {

    private val _scripts = MutableStateFlow<List<ConfigScript>>(emptyList())
    val scripts: StateFlow<List<ConfigScript>> = _scripts.asStateFlow()

    fun save(script: ConfigScript) {
        _scripts.value = _scripts.value.filter { it.name != script.name } + script
    }

    fun remove(name: String) {
        _scripts.value = _scripts.value.filter { it.name != name }
    }

    fun load(name: String): ConfigScript? = _scripts.value.firstOrNull { it.name == name }
}
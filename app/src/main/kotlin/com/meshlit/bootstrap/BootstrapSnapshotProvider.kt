package com.meshlit.bootstrap

import com.meshlit.core.bootstrap.BootstrapSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-side latch for the latest [BootstrapSnapshot]. The bootstrap
 * coordinator calls [publish] once `boot()` succeeds; everything
 * else (Koin factories, ViewModels) reads from this holder rather
 * than reaching into DataStore on every access.
 *
 * The provider exposes [nodeIdOrEmpty] for the synchronous-readers
 * the lifecycle controller needs at construction time.
 */
class BootstrapSnapshotProvider {
    private val state = MutableStateFlow<BootstrapSnapshot?>(null)

    fun publish(snapshot: BootstrapSnapshot) {
        state.value = snapshot
    }

    fun snapshotFlow(): StateFlow<BootstrapSnapshot?> = state.asStateFlow()

    fun snapshot(): BootstrapSnapshot? = state.value

    /** Synchronous accessor used by Koin factories that need a
     *  stable identity during construction (before the bootstrap
     *  coroutine has run). Returns "" when the bootstrap hasn't
     *  published yet. */
    fun nodeIdOrEmpty(): String = state.value?.nodeId.orEmpty()
}

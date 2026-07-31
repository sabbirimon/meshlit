package com.meshlit.setup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meshlit.core.common.OemSetupStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Tracks first-run setup completion across app launches.
 *
 * The OEM setup wizard (and the device-profile first-run flow)
 * persists a `Set<String>` of completed step tags. The detected
 * [com.meshlit.core.common.OemProfile] tells us which steps are
 * required; the completed set tells us which the user has finished.
 *
 * Reset: a "Reset setup" affordance from Settings wipes everything
 * so the user can re-walk the wizard (e.g. after a factory reset).
 */
class FirstRunSetupRepository(private val context: Context) {

    private val store: DataStore<Preferences> = context.firstRunDataStore

    /** Tags of steps the user has marked done. */
    val completedStepsFlow: Flow<Set<OemSetupStep>> = store.data.map { prefs ->
        prefs[Keys.completedSteps].orEmpty()
            .mapNotNull { tag -> OemSetupStep.entries.firstOrNull { it.tag == tag } }
            .toSet()
    }

    val hasFinishedFirstRunFlow: Flow<Boolean> = store.data.map { it[Keys.firstRunDone] ?: false }

    suspend fun markStepDone(step: OemSetupStep) {
        store.edit { prefs ->
            val current = prefs[Keys.completedSteps]?.toMutableSet() ?: mutableSetOf()
            current += step.tag
            prefs[Keys.completedSteps] = current
        }
    }

    suspend fun markStepUndone(step: OemSetupStep) {
        store.edit { prefs ->
            val current = prefs[Keys.completedSteps]?.toMutableSet() ?: mutableSetOf()
            current -= step.tag
            prefs[Keys.completedSteps] = current
        }
    }

    suspend fun setFirstRunFinished(finished: Boolean) {
        store.edit { it[Keys.firstRunDone] = finished }
    }

    suspend fun resetAll() {
        store.edit { prefs ->
            prefs.remove(Keys.completedSteps)
            prefs[Keys.firstRunDone] = false
        }
    }

    private object Keys {
        val completedSteps = stringSetPreferencesKey("first_run.completed_steps")
        val firstRunDone = booleanPreferencesKey("first_run.done")
    }
}

private val Context.firstRunDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "meshlit_first_run")
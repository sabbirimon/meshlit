package com.meshlit.ui.quickactions

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.inference.RunAnywhereCatalogEngine
import kotlinx.coroutines.launch

/**
 * ViewModel for the drawer's Sync quick action. Calls the
 * application's [RunAnywhereCatalogEngine.refresh] which forces
 * a fresh fetch of the on-device model registry and surfaces a
 * Toast with the result count or the error.
 *
 * This lives in `app/` because it needs the Android `Context`
 * (for Toasts) and the catalog engine — both unavailable in
 * `:core-inference`. The VM follows the pattern used elsewhere
 * in the app (see `CatalogScreen.kt:161-169`).
 */
class SyncViewModel(
    private val app: MeshlitApplication,
) : ViewModel() {

    private val engine: RunAnywhereCatalogEngine = app.catalogEngine

    /**
     * Trigger one re-sync. Concurrent calls are no-ops thanks to
     * the engine's internal mutex; the user may tap repeatedly
     * without effect.
     *
     * Surfaces a Toast on completion. Error messages come from the
     * engine's [com.meshlit.core.inference.MeshlitResult.Failure].
     */
    fun sync(context: Context) {
        viewModelScope.launch {
            val result = engine.refresh()
            val message = when (result) {
                is MeshlitResult.Success -> {
                    val n = result.value
                    context.getString(R.string.drawer_sync_ok, n)
                }
                is MeshlitResult.Failure -> {
                    context.getString(R.string.drawer_sync_failed, result.error.message ?: "unknown")
                }
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
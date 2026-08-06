package com.meshlit.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshlit.MeshlitApplication
import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.core.inference.LlmModelChangeInterlock
import com.meshlit.core.inference.ModelPredicates
import com.meshlit.core.inference.RuntimeModelSelection
import com.meshlit.core.inference.RuntimeModelSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RunAnywhere-parity Models-section state + actions.
 *
 * Mirrors `com.runanywhere.runanywhereai.ui.screens.models
 * .ModelSelectionViewModel` from the upstream RunAnywhere sample.
 * Holds:
 *
 *  - `models` — list of catalog entries (curated + RunAnywhere)
 *  - `currentModelId` — process-wide current model mirror
 *  - `busyModelId` — id whose download is in flight (null otherwise)
 *  - `progressPercent` — 0..100, live during a download
 *  - `isLoading` — true during `reload()`
 *  - `error` — typed `MeshlitError` from the last action; null after
 *    `clearError()` (the upstream VM exposes this so the UI can
 *    surface a dismissable dialog)
 *
 * Actions:
 *  - `refresh()` — re-list models via the catalog engine
 *  - `download(model)` — start streaming download (or surface HF-token
 *    state via the `requiresHfToken` returned)
 *  - `cancelDownload(modelId)` — abort in-flight download
 *  - `delete(model)` — drop the on-disk file + clear mirror
 *  - `select(model)` — load via coordinator (awaits the interlock)
 *  - `prepare(model)` — `awaitDownload` + `select`
 *  - `clearError()` — null the `error` field
 *
 * For this PR the VM is intentionally a thin facade: it holds the
 * state and forwards actions to existing modules
 * ([com.meshlit.inference.RunAnywhereCatalog] and the coordinator).
 * The screen collects `state` as `State<ModelSelectionState>` so
 * recomposition stays bounded.
 */
@Immutable
data class ModelSelectionState(
    val models: List<ModelSelectionEntry> = emptyList(),
    val currentModelId: String? = null,
    val busyModelId: String? = null,
    val progressPercent: Int = 0,
    val isLoading: Boolean = false,
    val error: MeshlitError? = null,
    val activeFramework: ModelPredicates.ActiveFramework = ModelPredicates.ActiveFramework.ALL,
    val searchQuery: String = "",
)

/**
 * Lightweight entry the VM surfaces to the UI. Mirrors the union of
 * `com.meshlit.inference.RunAnywhereCatalog.Entry` and
 * `com.meshlit.models.ModelCatalog.Entry` — the VM exposes a single
 * list so the picker doesn't have to merge them per-render.
 */
@Immutable
data class ModelSelectionEntry(
    val id: String,
    val displayName: String,
    val family: String,
    val origin: String,
    val language: String,
    val license: String,
    val approxSizeMb: Long,
    val source: ModelSource,
    val url: String,
    val requiresHfAuth: Boolean = false,
    val tags: List<String> = emptyList(),
    val isBuiltIn: Boolean = false,
    val isDownloaded: Boolean = false,
) {
    enum class ModelSource { RUNANYWHERE_CATALOG, ALTERNATIVE_IMPORT }
}

class ModelSelectionViewModel(
    private val coordinator: InferenceCoordinator,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow(ModelSelectionState())
    val state: StateFlow<ModelSelectionState> = _state.asStateFlow()

    /** Snapshot of the current selection context (read-only). */
    val currentSnapshot: Flow<RuntimeModelSnapshot?> =
        RuntimeModelSelection.observe(com.meshlit.core.inference.ModelSelectionContext.LLM)

    /** Re-list models and reconcile against the coordinator. */
    fun refresh() {
        _state.value = _state.value.copy(isLoading = true)
        appScope.launch {
            try {
                val models = buildModelList()
                val currentSnapshot = coordinator.loadedModel()?.modelPath
                _state.value = _state.value.copy(
                    models = models,
                    currentModelId = currentSnapshot,
                    isLoading = false,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = MeshlitError.Native(
                        "model.refresh:${t.message ?: t.javaClass.simpleName}",
                        t,
                    ),
                )
            }
        }
    }

    /**
     * Begin a streaming download for [entry]. Marks `busyModelId` and
     * surfaces `progressPercent` updates as the underlying flow
     * emits. When the download completes the entry flips to
     * `isDownloaded = true`.
     */
    fun download(entry: ModelSelectionEntry) {
        if (_state.value.busyModelId != null) return
        _state.value = _state.value.copy(
            busyModelId = entry.id,
            progressPercent = 0,
            error = null,
        )
        appScope.launch {
            try {
                val outcome = when (entry.source) {
                    ModelSelectionEntry.ModelSource.RUNANYWHERE_CATALOG -> {
                        val llm = coordinator.runAnywhereEngine()
                        if (entry.url.isNotBlank()) {
                            llm.setCatalogDownloadUrl(entry.id, entry.url)
                        }
                        withContext(Dispatchers.IO) {
                            llm.downloadModelById(
                                modelId = entry.id,
                                url = entry.url,
                                displayName = entry.displayName,
                                memoryRequirementBytes = entry.approxSizeMb * 1024L * 1024L,
                            )
                        }
                    }
                    ModelSelectionEntry.ModelSource.ALTERNATIVE_IMPORT -> {
                        // Alternative-import downloads route through the
                        // existing ModelCatalog.download helper. We
                        // delegate to the screen-level coroutine via
                        // ModelsScreen, so the VM only flips state —
                        // the screen does the actual download work.
                        _state.value = _state.value.copy(busyModelId = null)
                        return@launch
                    }
                }
                outcome.collect { progress ->
                    val pct = (progress.progress * 100f).toInt().coerceIn(0, 100)
                    _state.value = _state.value.copy(progressPercent = pct)
                }
                _state.value = _state.value.copy(
                    busyModelId = null,
                    progressPercent = 100,
                    models = _state.value.models.map {
                        if (it.id == entry.id) it.copy(isDownloaded = true) else it
                    },
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    busyModelId = null,
                    error = MeshlitError.Native(
                        "model.download:${t.message ?: t.javaClass.simpleName}",
                        t,
                    ),
                )
            }
        }
    }

    /** Abort an in-flight download. No-op when nothing is busy. */
    fun cancelDownload(modelId: String) {
        if (_state.value.busyModelId != modelId) return
        _state.value = _state.value.copy(
            busyModelId = null,
            progressPercent = 0,
        )
    }

    /**
     * Drop the on-disk file for [entry] and clear the model mirror.
     * No-op when the entry isn't downloaded. The actual file deletion
     * happens on the caller side; the VM just clears the mirror.
     */
    fun delete(entry: ModelSelectionEntry) {
        appScope.launch {
            val result = when (entry.source) {
                ModelSelectionEntry.ModelSource.RUNANYWHERE_CATALOG -> {
                    val llm = coordinator.runAnywhereEngine()
                    withContext(Dispatchers.IO) { llm.deleteDownloadedModel(entry.id) }
                }
                ModelSelectionEntry.ModelSource.ALTERNATIVE_IMPORT -> {
                    // Same as `download` — the file lives under
                    // filesDir/imported-models/<id>.gguf, and the
                    // screen handles the deletion. The VM flips
                    // state.
                    MeshlitResult.Success(Unit)
                }
            }
            if (result is MeshlitResult.Success) {
                _state.value = _state.value.copy(
                    models = _state.value.models.map {
                        if (it.id == entry.id) it.copy(isDownloaded = false) else it
                    },
                    currentModelId = if (_state.value.currentModelId == entry.id) {
                        null
                    } else {
                        _state.value.currentModelId
                    },
                )
            } else if (result is MeshlitResult.Failure) {
                _state.value = _state.value.copy(error = result.error)
            }
        }
    }

    /**
     * Load [entry] into the coordinator. Awaits
     * [LlmModelChangeInterlock.awaitReadyForModelChange] so any
     * in-flight chat generation drains before the load lands.
     */
    fun select(entry: ModelSelectionEntry) {
        appScope.launch {
            try {
                LlmModelChangeInterlock.awaitReadyForModelChange()
                val path = when (entry.source) {
                    ModelSelectionEntry.ModelSource.RUNANYWHERE_CATALOG ->
                        RUNANYWHERE_SCHEME + entry.id
                    ModelSelectionEntry.ModelSource.ALTERNATIVE_IMPORT ->
                        // Files live under filesDir/imported-models/<id>.gguf;
                        // callers wire the actual path through ModelsScreen.
                        return@launch
                }
                val result = coordinator.loadModel(path)
                when (result) {
                    is MeshlitResult.Success -> {
                        _state.value = _state.value.copy(
                            currentModelId = entry.id,
                            error = null,
                        )
                    }
                    is MeshlitResult.Failure -> {
                        _state.value = _state.value.copy(error = result.error)
                    }
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    error = MeshlitError.Native(
                        "model.select:${t.message ?: t.javaClass.simpleName}",
                        t,
                    ),
                )
            }
        }
    }

    /** Convenience: download then load. */
    fun prepare(entry: ModelSelectionEntry) {
        download(entry)
        appScope.launch {
            // Wait for the download to finish; the busy flag clears
            // when `download` completes.
            while (_state.value.busyModelId == entry.id) {
                kotlinx.coroutines.delay(100)
            }
            select(entry)
        }
    }

    fun setActiveFramework(framework: ModelPredicates.ActiveFramework) {
        _state.value = _state.value.copy(activeFramework = framework)
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun buildModelList(): List<ModelSelectionEntry> {
        val context = appContext ?: return emptyList()
        val alt = com.meshlit.models.ModelCatalog.all.map { entry ->
            ModelSelectionEntry(
                id = entry.id,
                displayName = entry.displayName,
                family = entry.family,
                origin = entry.origin,
                language = entry.language,
                license = entry.license,
                approxSizeMb = entry.approxSizeMb,
                source = ModelSelectionEntry.ModelSource.ALTERNATIVE_IMPORT,
                url = "",
                tags = entry.strengths,
                isBuiltIn = ModelPredicates.isBuiltIn(entry.id),
                isDownloaded = ModelPredicates.isDownloadedOnDisk(context, entry.id),
            )
        }
        val runAnywhere = com.meshlit.inference.RunAnywhereCatalog.all.map { entry ->
            ModelSelectionEntry(
                id = entry.id,
                displayName = entry.displayName,
                family = entry.family,
                origin = entry.origin,
                language = entry.language,
                license = entry.license,
                approxSizeMb = entry.approxSizeMb,
                source = ModelSelectionEntry.ModelSource.RUNANYWHERE_CATALOG,
                url = entry.url,
                tags = emptyList(),
                isBuiltIn = ModelPredicates.isBuiltIn(entry.id),
                isDownloaded = ModelPredicates.isDownloadedOnDisk(context, entry.id),
            )
        }
        return (alt + runAnywhere).distinctBy { it.id }
    }

    private var appContext: android.content.Context? = null

    fun attachContext(context: android.content.Context) {
        appContext = context.applicationContext
        refresh()
    }

    /** ViewModel factory that wires the coordinator + app scope. */
    class Factory(
        private val coordinator: InferenceCoordinator,
        private val appScope: CoroutineScope,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ModelSelectionViewModel(coordinator, appScope) as T
    }
}

private const val RUNANYWHERE_SCHEME = "runanywhere:"

/**
 * Compose helper that retrieves the VM keyed to the parent
 * `ViewModelStoreOwner` and attaches the Android `Context` so the
 * VM can read on-disk file presence. Returns the `State` flow as
 * Compose state.
 */
@Composable
fun rememberModelSelectionState(): Pair<ModelSelectionViewModel, ModelSelectionState> {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    val viewModel: ModelSelectionViewModel = viewModel(
        factory = ModelSelectionViewModel.Factory(
            coordinator = app.inferenceCoordinator,
            appScope = app.appScope,
        ),
    )
    LaunchedEffect(Unit) { viewModel.attachContext(context) }
    val state by viewModel.state.collectAsState()
    return viewModel to state
}
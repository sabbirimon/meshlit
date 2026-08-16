package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.R
import com.meshlit.core.inference.ModelPredicates
import com.meshlit.ui.components.RaGetButton
import com.meshlit.ui.components.RaPillChip
import com.meshlit.ui.components.RaPillTone

/**
 * Trailing-slot state machine for a model row. Mirrors the upstream
 * `ModelRow.TrailingAction` with the four-state model the user
 * shared in the screenshot:
 *
 *  1. **Loaded** — `RaPillChip("Loaded", ACTIVE)`. The currently
 *     active model (matches the green Loaded chip on Qwen3.5 0.8B
 *     in the screenshot).
 *  2. **Cancel-progress** — `CircularProgressIndicator` inside a
 *     tap-to-cancel slot. Shown when a download is in flight.
 *  3. **Use** — `RaPillChip("Use", BUNDLED)`. The model is on disk
 *     but not loaded — tap to load it.
 *  4. **Set token** — `RaGetButton(label = "Set token")`. The
 *     model requires a HuggingFace token and the user hasn't set
 *     one yet. Mirrors upstream `DownloadChip` "Set token"
 *     behavior.
 *  5. **Get** — `RaGetButton(label = "Get")`. Default entry path.
 *
 * The actual state is computed by the caller and passed in. This
 * keeps the composable pure (no `ViewModel` coupling) and lets the
 * same helper serve both `AlternativeModelsCard` and
 * `RunAnywhereCatalogCard`.
 */
@Composable
fun ModelTrailingAction(
    isCurrent: Boolean,
    isReady: Boolean,
    isBusy: Boolean,
    progressPercent: Int,
    requiresHfAuth: Boolean,
    onCancel: () -> Unit,
    onDownload: () -> Unit,
    onSelect: () -> Unit,
    onSetToken: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        isCurrent -> RaPillChip(
            text = stringResource(R.string.ra_loaded),
            tone = RaPillTone.ACTIVE,
            modifier = modifier,
        )
        isBusy -> CancelableProgress(
            progressPercent = progressPercent,
            onCancel = onCancel,
            modifier = modifier,
        )
        isReady -> RaPillChip(
            text = stringResource(R.string.ra_use),
            tone = RaPillTone.BUNDLED,
            modifier = modifier,
        )
        requiresHfAuth -> RaGetButton(
            onClick = onSetToken,
            label = stringResource(R.string.ra_set_token),
            modifier = modifier,
        )
        else -> RaGetButton(
            onClick = onDownload,
            label = stringResource(R.string.ra_get),
            modifier = modifier,
        )
    }
}

@Composable
private fun CancelableProgress(
    progressPercent: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Lightweight spinner; a more elaborate tap-to-cancel surface
    // can be added when we wire UI-side cancellation through the
    // VM. For now the parent VM exposes a `cancel` action and the
    // row surfaces the spinner + percentage text.
    androidx.compose.foundation.layout.Row(modifier = modifier) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
        )
    }
}

/**
 * Build a stable list of recommended model ids given the device's
 * available RAM. The smallest model that fits 33% of free RAM is
 * marked "Top pick" — mirrors the upstream `isRecommended(framework)`
 * heuristic that picks `qwen3_5_0_8b` on most consumer hardware.
 *
 * Today the policy is a placeholder; the upstream implementation
 * walks the device NPU profile. We default to "first model whose
 * size ≤ 33% of free RAM" so the screenshot's Top pick highlight
 * doesn't lie when no real NPU probe exists yet.
 */
fun recommendTopPick(
    entries: List<ModelSelectionEntry>,
    freeRamMb: Long,
): String? {
    val budget = (freeRamMb / 3).coerceAtLeast(0L)
    return entries
        .filter { it.approxSizeMb in 1..budget }
        .minByOrNull { it.approxSizeMb }
        ?.id
}

/**
 * Search-filter predicate for the picker — case-insensitive substring
 * match against every searchable field the screen shows in its
 * tooltip / row subtitle. Mirrors the upstream
 * `SettingsSearchIndex`-style match but bound to model rows.
 */
fun ModelSelectionEntry.matchesQuery(query: String): Boolean {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return true
    return displayName.lowercase().contains(needle) ||
        family.lowercase().contains(needle) ||
        origin.lowercase().contains(needle) ||
        language.lowercase().contains(needle) ||
        license.lowercase().contains(needle) ||
        id.lowercase().contains(needle) ||
        tags.any { it.lowercase().contains(needle) }
}

/**
 * Filter predicate for the active-framework chip row. When the
 * filter is `ALL`, every entry is visible. When `LLAMA_CPP` or
 * `NPU`, only matching entries are surfaced.
 */
fun ModelSelectionEntry.matchesFramework(
    activeFramework: ModelPredicates.ActiveFramework,
): Boolean = when (activeFramework) {
    ModelPredicates.ActiveFramework.ALL -> true
    ModelPredicates.ActiveFramework.LLAMA_CPP ->
        // Today every entry is llama.cpp; once the NPU catalog
        // lands, this branches on the model's framework tag.
        true
    ModelPredicates.ActiveFramework.NPU ->
        // No NPU-tagged rows until the NPU engine ships. Empty
        // filter result is the correct outcome.
        false
}
package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.R
import com.meshlit.core.inference.ModelPredicates

/**
 * Backend filter chip row used by the Models picker. Mirrors
 * upstream `ModelSelectionViewModel.FilterChipRow` — `All`,
 * `Llama.cpp`, `NPU`. Shown only when more than one
 * `InferenceFramework` is actually available; today only
 * `LLAMA_CPP` ships, so the row collapses to a single "All" chip
 * unless the caller passes `multiFrameworkAvailable = true` (which
 * the picker does once the NPU engine lands).
 *
 * The filter is a one-way data flow: the parent VM holds the
 * `activeFramework` state and this row just emits `onSelect(...)`
 * when the user taps a chip.
 */
@Composable
fun ModelFilterRow(
    active: ModelPredicates.ActiveFramework,
    onSelect: (ModelPredicates.ActiveFramework) -> Unit,
    modifier: Modifier = Modifier,
    multiFrameworkAvailable: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = active == ModelPredicates.ActiveFramework.ALL,
            onClick = { onSelect(ModelPredicates.ActiveFramework.ALL) },
            label = { Text(stringResource(R.string.ra_backend_filter_all)) },
            colors = FilterChipDefaults.filterChipColors(),
        )
        if (multiFrameworkAvailable) {
            FilterChip(
                selected = active == ModelPredicates.ActiveFramework.LLAMA_CPP,
                onClick = { onSelect(ModelPredicates.ActiveFramework.LLAMA_CPP) },
                label = { Text(stringResource(R.string.ra_backend_filter_llamacpp)) },
            )
            FilterChip(
                selected = active == ModelPredicates.ActiveFramework.NPU,
                onClick = { onSelect(ModelPredicates.ActiveFramework.NPU) },
                label = { Text(stringResource(R.string.ra_backend_filter_npu)) },
            )
        }
    }
}
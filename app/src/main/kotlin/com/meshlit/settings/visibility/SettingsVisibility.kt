package com.meshlit.settings.visibility

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Visibility filter for Settings rows.
 *
 * Used by every screen in `ui/screens/settings/` to gate
 * ADVANCED rows behind the hub's Simple/Advanced `ChipRow`.
 *
 * The contract is:
 *  - When [simpleMode] is `true`, every ADVANCED row is skipped.
 *  - When [simpleMode] is `false`, every row is rendered.
 *  - Sections that are "advanced-only" should also wrap a
 *    `SectionLabel` with `tier = ADVANCED` so the heading
 *    disappears too — otherwise the user sees a section label
 *    pointing at nothing.
 *
 * Rendering is a plain `Column` — no LazyColumn because every
 * settings screen stays small (< 30 rows) and a LazyColumn
 * would lose scroll state on visibility flips.
 */
object SettingsVisibility {

    @Composable
    fun Render(
        rows: List<RowDescriptor>,
        simpleMode: Boolean,
        modifier: Modifier = Modifier,
    ) {
        Column(modifier = modifier) {
            rows.forEach { row ->
                if (simpleMode && row.tier == Visibility.ADVANCED) return@forEach
                row.content()
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    /**
     * Pure-logic filter, exposed for unit tests so the rule
     * can be locked in without spinning Compose.
     */
    fun filter(rows: List<RowDescriptor>, simpleMode: Boolean): List<RowDescriptor> =
        rows.filter { row -> !simpleMode || row.tier == Visibility.SIMPLE }
}

package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshlit.ui.components.AppleGroupedCard

/**
 * Phase 4.x — Settings menu rewrite: fallback screen for any
 * `SettingsCategory` that doesn't yet have a specialized
 * branch in [CategoryScreen]. Renders the search-index rows
 * for the category grouped by their `advanced` flag, so the
 * user sees at least *something* useful — labels and
 * descriptions, not blank rows.
 *
 * Used as the `else ->` arm of [CategoryScreen]'s
 * exhaustive `when`. New categories should prefer writing a
 * dedicated screen over relying on this fallback.
 */
@Composable
fun SettingsSearchFallbackScreen(
    category: SettingsCategory,
) {
    val all = remember(category) {
        SettingsSearchIndex.all().filter { it.category == category }
    }
    val simple = all.filter { !it.advanced }
    val advanced = all.filter { it.advanced }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        if (simple.isNotEmpty()) {
            item { SectionLabel("Basic") }
            items(simple, key = { it.tag }) { match ->
                AppleGroupedCard(modifier = Modifier.fillMaxSize()) {
                    FallbackRow(match = match)
                }
            }
        }
        if (advanced.isNotEmpty()) {
            item { SectionLabel("Advanced") }
            items(advanced, key = { it.tag }) { match ->
                AppleGroupedCard(modifier = Modifier.fillMaxSize()) {
                    FallbackRow(match = match)
                }
            }
        }
        if (simple.isEmpty() && advanced.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Nothing in this category yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FallbackRow(match: SettingsSearchIndex.Match) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = match.label,
            style = MaterialTheme.typography.titleSmall,
        )
        if (match.description.isNotBlank()) {
            Text(
                text = match.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.R

/**
 * Generic shell for a single settings category. Renders the category
 * title + back button, a [Simple] / [Advanced] toggle, and the list
 * of settings for that category grouped by section.
 *
 * Settings are sourced from [SettingsSearchIndex.entries] filtered by
 * [category]. Phase 1 ships the read-only surface — every setting
 * shows its label and description and a placeholder "coming soon"
 * hint. Phase 2 wires each [Match.tag] to its real control.
 *
 * To specialize a category (e.g. Theme — needs live previews),
 * branch in the [specialized] slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    category: SettingsCategory,
    onBack: () -> Unit,
) {
    // rememberSaveable keeps the toggle state across config changes
    // and process death. Per-category via the route key so toggling
    // "advanced" on Device doesn't affect Theme.
    var advanced by rememberSaveable(category) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_search_clear),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_simple_toggle)) },
                            onClick = {
                                advanced = false
                                menuOpen = false
                            },
                            trailingIcon = {
                                if (!advanced) {
                                    Text("✓", style = MaterialTheme.typography.bodyMedium)
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_advanced_toggle)) },
                            onClick = {
                                advanced = true
                                menuOpen = false
                            },
                            trailingIcon = {
                                if (advanced) {
                                    Text("✓", style = MaterialTheme.typography.bodyMedium)
                                }
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Specialize per category — only Theme and Device are wired today.
            when (category) {
                SettingsCategory.THEME -> {
                    ThemeCustomizationScreen(advanced = advanced)
                    return@Column
                }
                SettingsCategory.DEVICE -> {
                    DeviceScreen(onBack = onBack)
                    return@Column
                }
                else -> Unit
            }

            val sections = categorySections(category, advanced = advanced)
            if (sections.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.settings_category_coming_soon),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(32.dp),
                    )
                }
                return@Column
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                sections.forEach { section ->
                    item(key = "section-${section.title}") {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(section.matches, key = { it.tag }) { match ->
                        SettingRow(match = match)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedToggle(
    advanced: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(
                if (advanced) R.string.settings_advanced_toggle else R.string.settings_simple_toggle,
            ),
            style = MaterialTheme.typography.labelMedium,
        )
        Switch(checked = advanced, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingRow(match: SettingsSearchIndex.Match) {
    com.meshlit.ui.components.RaNavRow(
        leadingIcon = Icons.Filled.ChevronRight,
        title = match.label,
        subtitle = match.description,
        onClick = { /* match click is wired by the parent category list */ },
        modifier = Modifier.fillMaxWidth(),
    )
}

private data class Section(
    val title: String,
    val matches: List<SettingsSearchIndex.Match>,
)

/**
 * Filters the indexed settings for a [SettingsCategory] down to those
 * that should show on the current "advanced" surface.
 *
 *  - `advanced = false` → curated Simple surface; only entries tagged
 *    `advanced = false` are visible.
 *  - `advanced = true`  → all entries for the category, in the index
 *    order, no separate "Advanced" sub-section (the toggle itself is
 *    the visual signal that power-user knobs are now shown).
 */
@Composable
private fun categorySections(
    category: SettingsCategory,
    advanced: Boolean,
): List<Section> {
    val all = SettingsSearchIndex.all().filter { it.category == category }
    if (all.isEmpty()) return emptyList()

    val visible = if (advanced) all else all.filter { !it.advanced }
    if (visible.isEmpty()) return emptyList()
    return listOf(
        Section(
            title = stringResource(
                if (advanced) R.string.settings_category_section_advanced
                else R.string.settings_category_section_basic,
            ),
            matches = visible,
        ),
    )
}
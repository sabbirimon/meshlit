package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.R

/**
 * Top-level Settings hub. Shows a search bar at the top and a list
 * of category cards. Each card opens a [CategoryScreen] for that
 * group. The Advanced toggle in [CategoryScreen] reveals every
 * knob; without it, the user sees the curated "Simple" surface.
 *
 * Search is built on [SettingsSearchIndex] — every setting across
 * every category is indexed by label + description keywords. When
 * the user types, the screen filters to matching settings (deep
 * linking to the right card). Phase 1 ships the index; Phase 2
 * adds in-app action buttons on search results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenCategory: (SettingsCategory) -> Unit,
    onOpenDrawer: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val tier = (context.applicationContext as MeshlitApplication).capabilityTier

    Scaffold(
        topBar = {
            com.meshlit.ui.components.MeshlitHeader(
                title = stringResource(R.string.screen_settings),
                subtitle = null,
                tier = tier,
                active = false,
                onOpenDrawer = onOpenDrawer,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (query.isBlank()) {
                // Default view: category cards.
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    items(SettingsCategory.entries) { cat ->
                        CategoryCard(category = cat, onClick = { onOpenCategory(cat) })
                    }
                }
            } else {
                // Search view: filtered settings.
                val matches = remember(query) { SettingsSearchIndex.search(query) }
                SearchResultsList(matches = matches, onOpenCategory = onOpenCategory)
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: SettingsCategory,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    matches: List<SettingsSearchIndex.Match>,
    onOpenCategory: (SettingsCategory) -> Unit,
) {
    if (matches.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.settings_no_results),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        items(matches) { match ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenCategory(match.category) },
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = match.label,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = match.description,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * Each top-level category. Order matters — the most-touched
 * (Device, Notifications, Theme) come first.
 */
enum class SettingsCategory(
    val displayName: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    DEVICE("Device", "Chipset, role, eGPU, peripherals", Icons.Default.Devices),
    THEME("Theme & Display", "Colors, font, density, animations", Icons.Default.Palette),
    NOTIFICATIONS("Notifications", "Per-category controls", Icons.Default.Notifications),
    CLUSTER("Cluster & Network", "Transports, firewall, tunnel", Icons.Outlined.Hub),
    MODELS("Models", "Default quant, auto-download", Icons.Outlined.Storage),
    ACCOUNT("Account", "Tier, tokens, audit trail", Icons.Default.AccountCircle),
    PERFORMANCE("Performance", "CPU threads, GPU layers, thermal", Icons.Default.Speed),
    PRIVACY("Privacy & Security", "Trust tiers, keys, audit", Icons.Default.Security),
    ABOUT("About", "Version, licenses, third-party", Icons.Default.Info),
    DEVELOPER("Developer", "Logs, sample rate, debug", Icons.Default.Build);
}
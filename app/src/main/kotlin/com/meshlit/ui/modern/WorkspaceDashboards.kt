package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.BuildProfile
import com.meshlit.di.koinInject
import com.meshlit.routing.ModelRoutes
import com.meshlit.workspace.DashboardDirectory

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WorkspaceDashboards(page: String, onBack: () -> Unit, navigate: (String) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(if (page == "dashboards") "Dashboards" else
        DashboardDirectory.entries.first { it.id == page }.title) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("dashboard-$page"),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (page) {
                "dashboards" -> {
                    item { Text("Your everyday workspaces", style = MaterialTheme.typography.headlineSmall)
                        Text("Manage models and connections here. Keep preferences in Settings.") }
                    items(DashboardDirectory.entries.filter { BuildProfile.routeAllowed(it.id) }, key = { it.id }) { entry ->
                        Card(onClick = { navigate(entry.id) }, modifier = Modifier.fillMaxWidth().testTag("dashboard-open-${entry.id}"),
                            shape = RoundedCornerShape(16.dp)) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                SettingsIconTile(entry.id)
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title, style = MaterialTheme.typography.titleMedium)
                                    Text(entry.description, style = MaterialTheme.typography.bodyMedium)
                                    if (entry.id == "usage") Text("Recorded text requests · device budgets", style = MaterialTheme.typography.labelSmall)
                                    if (entry.id == "vpn") Text("Real app-network observations · optional companion", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    item {
                        listOf("providers" to "Online providers", "device-dashboard" to "Devices",
                            "rag" to "Knowledge library", "hyperl" to "HyperL workbench").filter { BuildProfile.routeAllowed(it.first) }
                            .forEach { (id, title) -> TextButton(onClick = { navigate(id) }) { Text(title) } }
                    }
                }
                "usage" -> item { UsageDashboard() }
                "vpn" -> item { VpnDashboard(navigate) }
            }
        }
    }
}

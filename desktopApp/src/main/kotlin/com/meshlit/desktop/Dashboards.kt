package com.meshlit.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshlit.workspace.DashboardDirectory
import com.meshlit.workspace.GenerationUsage

@Composable internal fun DashboardHub(onNavigate: (String) -> Unit) {
    Text("Your workspace, at a glance", style = MaterialTheme.typography.h5)
    Text("Models, connections and activity have their own homes. Preferences stay in Settings.",
        style = MaterialTheme.typography.body2)
    BoxWithConstraints {
        val columns = if (maxWidth >= 560.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardDirectory.entries.chunked(columns).forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { entry ->
                        Surface(modifier = Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = .14f))) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                MenuHeading(entry.id, entry.title)
                                Text(entry.description, style = MaterialTheme.typography.body2)
                                Text(when (entry.id) {
                                    "models" -> "Local models and compatible hosts"
                                    "router" -> "Manual selection · automatic routing pending"
                                    "usage" -> "Encrypted history · device budgets"
                                    else -> "Optional Tailscale · observed status"
                                }, style = MaterialTheme.typography.caption)
                                Spacer(Modifier.weight(1f))
                                TextButton({ onNavigate(entry.id) }) { Text("Open ${entry.title}") }
                            }
                        }
                    }
                }
            }
        }
    }
    Text("Other workspaces", style = MaterialTheme.typography.subtitle1)
    listOf("device-dashboard" to "Devices", "rag" to "Knowledge library", "hyperl" to "HyperL workbench",
        "monitor" to "Resources").forEach { (id, label) -> TextButton({ onNavigate(id) }) { Text(label) } }
}

@Composable internal fun RoutingDashboard(onModels: () -> Unit, onProviders: () -> Unit) {
    Text("Choose a model explicitly", style = MaterialTheme.typography.h6)
    Text("Studio currently uses the model and host you select. Scenario rules and automatic fallback are not yet available. Usage budgets check admission before chat requests.")
    OutlinedButton(onModels) { Text("Choose model") }
    TextButton(onProviders) { Text("Manage host connection") }
    Text("Direct free or paid services and optional gateways can be reached through a compatible host. Review the host's own routing and billing; Studio cannot enforce a free-only policy there.",
        style = MaterialTheme.typography.body2)
}

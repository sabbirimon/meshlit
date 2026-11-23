package com.meshlit.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.meshlit.workspace.GenerationUsage

@Composable internal fun DashboardHub(modelStatus: String? = null, onNavigate: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val groups = remember(query) {
        DesktopDestinations.search(query).filter { it.available && it.id !in setOf("management", "ide") }
            .groupBy { desktopGroup(it.id) }
    }
    Text("Studio dashboard", style = MaterialTheme.typography.h4)
    modelStatus?.let { Text(it, style = MaterialTheme.typography.subtitle2, color = MaterialTheme.colors.primary) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ onNavigate("code") }) { Text("Open coding IDE") }
        OutlinedButton({ onNavigate("chat") }) { Text("Chat") }
        TextButton({ onNavigate("appearance") }) { Text("Settings") }
    }
    OutlinedTextField(query, { query = it.take(200) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text("Find a dashboard or setting") }, trailingIcon = {
            if (query.isNotEmpty()) TextButton({ query = "" }) { Text("Clear") }
        })
    if (groups.isEmpty()) Text("No matching dashboards.")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 660.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            groups.entries.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { (group, items) ->
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = .16f))) {
                            Column(Modifier.padding(14.dp)) {
                                Text(group, style = MaterialTheme.typography.h6, color = MaterialTheme.colors.primary)
                                items.forEach { item ->
                                    Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Open ${item.title}") { onNavigate(item.id) }
                                        .padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(item.title, Modifier.weight(1f))
                                        Text("›", color = MaterialTheme.colors.primary)
                                    }
                                }
                            }
                        }
                    }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable internal fun RoutingDashboard(onModels: () -> Unit, onProviders: () -> Unit) {
    Text("Choose a model explicitly", style = MaterialTheme.typography.h6)
    Text("Studio currently uses the model and host you select. Scenario rules and automatic fallback are not yet available. Usage budgets check admission before chat requests.")
    OutlinedButton(onModels) { Text("Choose model") }
    TextButton(onProviders) { Text("Manage host connection") }
    Text("Direct free or paid services and optional gateways can be reached through a compatible host. Review the host's own routing and billing; Studio cannot enforce a free-only policy there.",
        style = MaterialTheme.typography.body2)
}

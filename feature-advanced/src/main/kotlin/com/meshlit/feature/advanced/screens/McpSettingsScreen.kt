package com.meshlit.feature.advanced.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * MCP settings screen. Toggle the embedded HTTP server on/off
 * (binds 127.0.0.1 by default; "LAN reachable" expands the bind
 * to 0.0.0.0). Add / remove user-MCP entries through the existing
 * [com.meshlit.core.mcp.UserMcpServerStore].
 *
 * Pre-filled with the [onToggleServer] / [onAddServer] /
 * [onRemoveServer] callbacks the parent (advanced hub) wires up
 * to the app singletons.
 */
@Composable
fun McpSettingsScreen(
    accent: Color,
    accentDim: Color,
    onBack: () -> Unit,
    serverRunning: Boolean,
    boundHost: String? = null,
    boundPort: Int? = null,
    onToggleServer: (Boolean) -> Unit = {},
    userServers: List<String> = emptyList(),
    onAddServer: (name: String, command: String) -> Unit = { _, _ -> },
    onRemoveServer: (String) -> Unit = {},
) {
    var lanReachable by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("github") }
    var newCommand by remember { mutableStateOf("/usr/local/bin/github-mcp") }

    SectionScreen(
        title = "MCP settings",
        subtitle = "Embedded HTTP server + user tools.",
        accent = accent,
        accentDim = accentDim,
        onBack = onBack,
        content = {
            SectionCard(title = "Server", accent = accent) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (serverRunning) "Running on ${boundHost ?: "?"}:${boundPort ?: "?"}"
                        else "Stopped",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = serverRunning,
                        onCheckedChange = { onToggleServer(it) },
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("LAN reachable (0.0.0.0)", modifier = Modifier.weight(1f))
                    Switch(checked = lanReachable, onCheckedChange = { lanReachable = it })
                }
                if (lanReachable) {
                    Text(
                        text = "Server will bind on 0.0.0.0 — anyone on the network can connect.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            SectionCard(title = "User MCP servers", accent = accent) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newCommand,
                    onValueChange = { newCommand = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newCommand.isNotBlank()) {
                            onAddServer(newName, newCommand)
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Add") }
                userServers.forEach { name ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Text(name, modifier = Modifier.weight(1f))
                        Button(onClick = { onRemoveServer(name) }) { Text("Remove") }
                    }
                }
                if (userServers.isEmpty()) {
                    Text("No user MCP servers configured.", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
    )
}

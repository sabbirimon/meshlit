package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable internal fun MenuLayoutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val saved by MenuShortcutStore.ids(context).collectAsState()
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun update(ids: List<String>) {
        try { MenuShortcutStore.save(context, ids); error = null }
        catch (e: Exception) { error = e.message ?: "Could not save menu" }
    }
    val selected = saved.mapNotNull { id -> MenuShortcutStore.choices().firstOrNull { it.id == id } }
    val available = MenuShortcutStore.choices().filter { it.id !in saved &&
        (query.isBlank() || "${it.title} ${it.description} ${it.keywords}".contains(query, ignoreCase = true)) }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("menu-layout-list"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { TextButton(onClick = onBack) { Text("Back to settings") } }
        item { Text("Customize menu", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Choose 1–12 shortcuts. Their order appears here, in Settings quick access and in the main drawer. Changes save immediately.") }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        items(selected, key = { "selected:${it.id}" }) { entry ->
            val index = saved.indexOf(entry.id)
            Card { Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SettingsIconTile(entry.id)
                Text(entry.title, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { val next = saved.toMutableList(); next.add(index - 1, next.removeAt(index)); update(next) },
                    enabled = index > 0, modifier = Modifier.testTag("menu-up-${entry.id}")) { Icon(Icons.Default.ArrowUpward, "Move ${entry.title} up") }
                IconButton(onClick = { val next = saved.toMutableList(); next.add(index + 1, next.removeAt(index)); update(next) },
                    enabled = index < saved.lastIndex, modifier = Modifier.testTag("menu-down-${entry.id}")) { Icon(Icons.Default.ArrowDownward, "Move ${entry.title} down") }
                IconButton(onClick = { update(saved - entry.id) }, enabled = saved.size > 1,
                    modifier = Modifier.testTag("menu-remove-${entry.id}")) { Icon(Icons.Default.RemoveCircleOutline, "Remove ${entry.title}") }
            } }
        }
        item { HorizontalDivider(); Text("Add a shortcut", style = MaterialTheme.typography.titleMedium) }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("menu-add-search"),
            label = { Text("Find a setting") }, singleLine = true) }
        items(available, key = { "available:${it.id}" }) { entry ->
            OutlinedButton(onClick = { update(saved + entry.id) }, enabled = saved.size < 12,
                modifier = Modifier.fillMaxWidth().testTag("menu-add-${entry.id}")) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(entry.title, Modifier.weight(1f))
            }
        }
    }
}

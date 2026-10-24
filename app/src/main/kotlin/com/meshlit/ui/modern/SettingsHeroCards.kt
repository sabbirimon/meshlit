package com.meshlit.ui.modern

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Original native menu artwork; cards navigate to implemented screens. */
@Composable internal fun SettingsHeroCards(onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val entries = listOf(Triple("models", "Models", "Your engines"),
        if (com.meshlit.BuildProfile.coreCandidate) Triple("files", "Files", "Your workspace") else Triple("agents", "Agents", "Your controls"),
        Triple("appearance", "Style", "Make it yours"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEachIndexed { index, (id, title, caption) ->
            val tint = if (com.meshlit.ui.theme.LocalMeshlitThemeConfig.current.highContrast) colors.onSurface
                else listOf(Color(0xFF69B9FF), colors.primary, Color(0xFF61DCA4))[index]
            Card(onClick = { onSelect(id) }, shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f).testTag("settings-hero-$id"),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .3f))) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(48.dp).background(Brush.radialGradient(listOf(tint.copy(alpha = .24f), tint.copy(alpha = .05f))), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(settingsIcon(id), null, Modifier.size(28.dp), tint = tint)
                    }
                    Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(caption, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Keep important routes visible even when their full settings list is below the fold. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SettingsQuickAccess(onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val ids by MenuShortcutStore.ids(context).collectAsState()
    val entries = ids.mapNotNull { id -> SettingsDestinations.all.firstOrNull { it.id == id } }
    if (entries.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Quick access", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { onSelect("menu-layout") }, modifier = Modifier.testTag("settings-edit-menu")) { Text("Edit menu") }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEach { entry ->
                val shortLabel = when (entry.id) {
                    "network" -> "Network & pairing"
                    "monitor" -> "Diagnostics"
                    "commands" -> "Terminal commands"
                    else -> entry.title
                }
                AssistChip(onClick = { onSelect(entry.id) }, label = { Text(shortLabel, maxLines = 2) },
                    leadingIcon = { Icon(settingsIcon(entry.id), null, Modifier.size(18.dp)) },
                    modifier = Modifier.widthIn(max = availableWidth).testTag("settings-quick-${entry.id}"))
            }
        }
        }
    }
}

@Composable internal fun SettingsIconTile(id: String) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(11.dp), color = colors.primary.copy(alpha = .10f)) {
        Icon(settingsIcon(id), null, Modifier.padding(9.dp).size(22.dp), tint = if (com.meshlit.ui.theme.LocalMeshlitThemeConfig.current.highContrast) colors.onSurface else colors.primary)
    }
}

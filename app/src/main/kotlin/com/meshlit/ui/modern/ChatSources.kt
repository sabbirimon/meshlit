package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meshlit.chat.*

/** Built-in icons only; displaying a source never requests its favicon or page. */
@Composable internal fun ChatToolActivityIndicator(tool: String) {
    val label = chatToolActivity(tool) ?: return
    Row(Modifier.padding(start = 10.dp).testTag("chat-tool-activity"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(if (tool == "crawl_url") Icons.Default.Language else Icons.Default.Search,
            contentDescription = label, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2)
        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
    }
}

@Composable internal fun ChatWebSources(messageId: String, sources: List<ChatWebSource>) {
    val visible = remember(sources) { mergeChatSources(sources, emptyList()) }
    if (visible.isEmpty()) return
    var expanded by remember(messageId) { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().testTag("chat-web-sources")) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("chat-source-toggle")) {
            Icon(Icons.Default.Language, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Web sources · ${visible.size}")
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Hide sources" else "Show sources")
        }
        if (expanded) visible.forEachIndexed { index, source ->
            TextButton(onClick = {
                runCatching { uri.openUri(source.url) }.onFailure {
                    android.widget.Toast.makeText(context, "Could not open this source link", android.widget.Toast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier.fillMaxWidth().testTag("chat-source-$index")) {
                Icon(Icons.Default.Language, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(source.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(chatSourceHost(source.url), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val detail = if (source.kind == ChatSourceKind.SEARCH_RESULT) "Search result · page not fetched"
                        else "Page retrieved" + if (source.truncated == true) " · truncated" else ""
                    Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.OpenInNew, "Open ${chatSourceHost(source.url)}", Modifier.size(18.dp))
            }
        }
    }
}

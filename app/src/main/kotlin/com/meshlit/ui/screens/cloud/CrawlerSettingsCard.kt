package com.meshlit.ui.screens.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.meshlit.core.mcp.builtin.CrawlSettings
import com.meshlit.core.mcp.builtin.CrawlSettingsStore
import com.meshlit.di.koinInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CrawlerSettingsCard() {
    val store: CrawlSettingsStore = koinInject()
    val saved = remember { store.load() }
    var endpoint by remember { mutableStateOf(saved.endpoint) }
    // Tokens are never restored into display text or saved-instance state.
    var token by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(saved.enabled) }
    var status by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Web crawler", style = MaterialTheme.typography.titleMedium)
            Text("Crawl4AI runs on your own host. Enabling it sends requested URLs to that host. " +
                "Sites receive browser requests; retrieved pages are untrusted content.")
            OutlinedTextField(
                value = endpoint, onValueChange = { endpoint = it }, singleLine = true,
                label = { Text("HTTPS companion URL") }, modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
            )
            OutlinedTextField(
                value = token, onValueChange = { token = it }, singleLine = true,
                label = { Text("New token (blank keeps token for the same host)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), enabled = !saving,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Allow web crawling", modifier = Modifier.weight(1f))
                Switch(checked = enabled, enabled = !saving, onCheckedChange = {
                    enabled = it
                    // Revocation takes effect immediately, without waiting for Save.
                    if (!it) { store.disable(); status = "Crawler disabled" }
                })
            }
            Button(enabled = !saving, onClick = {
                val pending = CrawlSettings(enabled, endpoint)
                val pendingToken = token
                saving = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { store.save(pending, pendingToken) }
                        token = ""
                        status = "Crawler configuration saved"
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: IllegalArgumentException) {
                        status = error.message ?: "Invalid crawler configuration"
                    } catch (_: Exception) {
                        status = "Could not save crawler configuration"
                    } finally {
                        saving = false
                    }
                }
            }) { Text("Save crawler configuration") }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

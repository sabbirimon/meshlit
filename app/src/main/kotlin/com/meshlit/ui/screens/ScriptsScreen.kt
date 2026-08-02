package com.meshlit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.core.common.ConfigScript
import com.meshlit.core.common.ScriptEvent
import com.meshlit.scripts.ConfigScriptRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Phase C.3 — Ansible-style scripts screen.
 *
 * Three horizontal sections stacked top-to-bottom:
 *  1. **Library** — saved scripts. Tap a row to load into the
 *     editor. Use `Add demo` to seed the library with a starter
 *     playbook so first-time users don't stare at an empty list.
 *  2. **Editor** — name / description / steps JSON. Saving writes
 *     to [com.meshlit.scripts.ScriptLibrary]; the Json parser is
 *     forgiving (see [json]) so a syntax error pops up below the
 *     field instead of crashing.
 *  3. **Run** — taps `Run selected` and streams [ScriptEvent]s into
 *     a log-style LazyColumn. The runner is a singleton on the app
 *     — re-running replaces the previous run.
 *
 * Multi-device "upload" is a placeholder button for v1. Phase 2.5
 * wires it to `POST /v1/scripts/push` so a script can be deployed
 * to every peer in the registry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptsScreen(
    onOpenDrawer: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    val scope = rememberCoroutineScope()

    val library = remember { app.scriptLibrary }
    val scripts by library.scripts.collectAsState()

    val runner = remember { ConfigScriptRunner(library, app.peerRegistry) }
    val lastEvent by runner.events.collectAsState()

    // Editor state
    var draftName by remember { mutableStateOf("") }
    var draftDescription by remember { mutableStateOf("") }
    var draftJson by remember { mutableStateOf("[]") }
    var draftError by remember { mutableStateOf<String?>(null) }

    fun loadIntoEditor(script: ConfigScript) {
        draftName = script.name
        draftDescription = script.description
        draftJson = json.encodeToString(script.steps)
        draftError = null
    }

    fun saveDraft() {
        val parsed = try {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(
                    com.meshlit.core.common.ConfigScriptStep.serializer(),
                ),
                draftJson,
            )
        } catch (t: Throwable) {
            draftError = t.message ?: "invalid JSON"
            return
        }
        library.save(ConfigScript(
            name = draftName.ifBlank { "untitled" },
            description = draftDescription,
            steps = parsed,
        ))
        draftError = null
    }

    Scaffold(
        topBar = {
            com.meshlit.ui.components.MeshlitHeader(
                title = stringResource(R.string.scripts_title),
                subtitle = null,
                tier = (context.applicationContext as com.meshlit.MeshlitApplication).capabilityTier,
                active = false,
                onOpenDrawer = onOpenDrawer,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SectionTitle(stringResource(R.string.scripts_library)) }
            if (scripts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Text(
                            text = stringResource(R.string.scripts_empty),
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(scripts, key = { it.name }) { script ->
                    ScriptRow(
                        script = script,
                        onClick = { loadIntoEditor(script) },
                    )
                }
            }
            item {
                TextButton(onClick = {
                    val demo = demoScript()
                    library.save(demo)
                    loadIntoEditor(demo)
                }) { Text(stringResource(R.string.scripts_seed_demo)) }
            }

            item { SectionTitle(stringResource(R.string.scripts_run) + " — editor") }
            item {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    label = { Text(stringResource(R.string.scripts_editor_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = draftDescription,
                    onValueChange = { draftDescription = it },
                    label = { Text(stringResource(R.string.scripts_editor_description_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = draftJson,
                    onValueChange = { draftJson = it; draftError = null },
                    label = { Text(stringResource(R.string.scripts_editor_json_hint)) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    isError = draftError != null,
                    supportingText = draftError?.let { { Text(it) } },
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { saveDraft() }) { Text(stringResource(R.string.scripts_save)) }
                    Button(
                        enabled = scripts.any { it.name == draftName.ifBlank { "untitled" } },
                        onClick = {
                            scope.launch {
                                val script = library.load(draftName.ifBlank { "untitled" })
                                    ?: return@launch
                                runner.run(script)
                            }
                        },
                    ) { Text(stringResource(R.string.scripts_run_selected)) }
                    TextButton(
                        onClick = {
                            // Phase C.3.1: uploads only logged — remote
                            // dispatch not yet wired.
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null)
                            Text(stringResource(R.string.scripts_upload))
                        }
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.scripts_run)) }
            item { LastEventCard(lastEvent) }
        }
    }
}

@Composable
private fun SectionTitle(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun ScriptRow(script: ConfigScript, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = script.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (script.description.isNotBlank()) {
                    Text(
                        text = script.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${script.steps.size} step(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onClick) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LastEventCard(event: ScriptEvent?) {
    val (label, accent) = when (event) {
        null -> stringResource(R.string.scripts_idle) to MaterialTheme.colorScheme.onSurfaceVariant
        is ScriptEvent.Start -> stringResource(R.string.scripts_event_start) to MaterialTheme.colorScheme.primary
        is ScriptEvent.StepOk -> "${stringResource(R.string.scripts_event_step_ok)} ${event.label}" to MaterialTheme.colorScheme.primary
        is ScriptEvent.StepFail -> "${stringResource(R.string.scripts_event_step_fail)} ${event.label}" to MaterialTheme.colorScheme.error
        is ScriptEvent.Done -> if (event.success) {
            stringResource(R.string.scripts_event_done) to MaterialTheme.colorScheme.primary
        } else {
            stringResource(R.string.scripts_event_done_failed) to MaterialTheme.colorScheme.error
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .padding(4.dp)
                    .background(color = accent, shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = event?.javaClass?.simpleName ?: "Idle",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "kind"
    prettyPrint = true
}

private fun demoScript(): ConfigScript = ConfigScript(
    name = "demo-bring-up",
    description = "Set vars, assert a condition, wait, parallel assigns",
    steps = listOf(
        com.meshlit.core.common.ConfigScriptStep.Set(
            key = "bringUpBy",
            value = "hello-meshlit",
        ),
        com.meshlit.core.common.ConfigScriptStep.Assert(
            label = "ensure-bring-up",
            expression = "true",
        ),
        com.meshlit.core.common.ConfigScriptStep.Wait(
            label = "settle",
            durationMs = 500,
        ),
        com.meshlit.core.common.ConfigScriptStep.Parallel(
            label = "fan-out",
            children = listOf(
                com.meshlit.core.common.ConfigScriptStep.Set(key = "peer1", value = "ready"),
                com.meshlit.core.common.ConfigScriptStep.Set(key = "peer2", value = "ready"),
            ),
        ),
    ),
)
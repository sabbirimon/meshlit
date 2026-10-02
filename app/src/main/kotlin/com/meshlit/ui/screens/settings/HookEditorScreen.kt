package com.meshlit.ui.screens.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.core.common.HookDefinition
import com.meshlit.core.common.HookTrigger
import com.meshlit.di.koinInject
import com.meshlit.settings.SettingsRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Single-hook editor. Mirrors the shape of `ScriptsScreen`'s editor
 * pane — name + script JSON + supporting fields — but operates on a
 * [HookDefinition] and adds a trigger list + a timeout slider.
 *
 * The script body is edited as raw JSON for v1 (matches
 * `ScriptsScreen`). A schema-aware editor is a follow-up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HookEditorScreen(
    hookId: String,
    onBack: () -> Unit,
) {
    val settings: SettingsRepository = koinInject()
    val vm = remember { HooksViewModel(settings) }
    val state by vm.uiState.collectAsState()

    val existing = state.hooks.firstOrNull { it.id == hookId }
    var draft by remember(existing) {
        mutableStateOf(existing ?: HooksViewModel.newDraft().copy(id = hookId))
    }

    Scaffold(
        topBar = {
            com.meshlit.ui.components.MeshlitHeader(
                title = "Edit hook",
                subtitle = draft.name,
                tier = koinInject(),
                active = false,
                onOpenDrawer = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Enabled",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Switch(
                        checked = draft.enabled,
                        onCheckedChange = { draft = draft.copy(enabled = it) },
                    )
                }
            }

            item { Text("Trigger", style = MaterialTheme.typography.titleSmall) }
            // Iterate the canonical trigger set as a `List<HookTrigger>` and
            // key each row by its singleton identity (the `data object`
            // instance — Kotlin's plain `object` is preferred over
            // `data object` here because of an upstream Kotlin / R8
            // interaction where `INSTANCE` can read as null at the
            // companion-init moment — see `HookTrigger.all`'s doc).
            // The `filterNotNull` + non-null key is a belt-and-suspenders
            // defense that survives even a stale snapshot.
            items(
                items = HookTrigger.all.filterNotNull(),
                key = { t -> t::class.simpleName ?: t.javaClass.simpleName },
            ) { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = draft.trigger == t,
                        onClick = { draft = draft.copy(trigger = t) },
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(t.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = t.chipLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Column {
                    Text(
                        text = "Timeout: ${draft.timeoutMs / 1000}s",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Slider(
                        value = (draft.timeoutMs / 1000f).coerceIn(1f, 60f),
                        onValueChange = { draft = draft.copy(timeoutMs = it.toLong() * 1000L) },
                        valueRange = 1f..60f,
                        steps = 59,
                    )
                }
            }

            item { Text("Script body", style = MaterialTheme.typography.titleSmall) }
            item {
                ScriptJsonEditor(
                    draft = draft,
                    onChange = { updated -> draft = updated },
                )
            }

            item {
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Button(onClick = {
                        vm.upsert(draft)
                        onBack()
                    }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptJsonEditor(
    draft: HookDefinition,
    onChange: (HookDefinition) -> Unit,
) {
    val json = remember {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
    }
    var text by remember(draft.id) {
        mutableStateOf(json.encodeToString(draft.script))
    }
    var error by remember(draft.id) { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = try {
                        val parsed = json.decodeFromString(
                            com.meshlit.core.common.ConfigScript.serializer(),
                            it,
                        );
                        onChange(draft.copy(script = parsed))
                        null
                    } catch (t: Throwable) {
                        t.message ?: "invalid JSON"
                    }
                },
                label = { Text("ConfigScript JSON") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
            )
        }
    }
}
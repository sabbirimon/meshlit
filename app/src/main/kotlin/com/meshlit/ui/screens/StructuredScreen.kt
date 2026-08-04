package com.meshlit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.core.inference.RunAnywhereStructuredEngine
import com.meshlit.ui.components.MeshlitHeader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Phase 2.x — Structured-output + tool-calling screen. Drives the
 * SDK's `generateStructuredStream` against a JSON schema, then
 * renders the running JSON and the final parsed payload. Toggling
 * "Allow tool calling" routes through `generateWithTools` and
 * surfaces each tool invocation as its own card.
 *
 * Templates (Contact / Todo / Summary / Sentiment) cover the common
 * "extract a thing from text" use cases; "Custom JSON" opens a
 * multi-line editor for arbitrary schema authoring.
 *
 * The screen calls the engine directly — no InferenceCoordinator
 * detour, because structured output is a one-shot prompt rather
 * than an ongoing generation the FGS owns. If the LLM model isn't
 * yet loaded in the FGS, the SDK lazily initialises its own
 * runtime against the bundled GGUF.
 */
@Composable
fun StructuredScreen(onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    val engine = app.structuredEngine
    val scope = rememberCoroutineScope()

    var template by remember { mutableStateOf(SchemaTemplate.Contact) }
    var customSchemaJson by remember { mutableStateOf("") }
    var showSchemaEditor by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf("") }
    var allowTools by remember { mutableStateOf(false) }
    var partialJson by remember { mutableStateOf("") }
    var rawText by remember { mutableStateOf("") }
    var isValid by remember { mutableStateOf(false) }
    var validationReason by remember { mutableStateOf<String?>(null) }
    var toolCalls by remember { mutableStateOf<List<RunAnywhereStructuredEngine.ToolCallView>>(emptyList()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }

    var runJob by remember { mutableStateOf<Job?>(null) }

    fun run() {
        if (running || prompt.isBlank()) return
        running = true
        partialJson = ""
        rawText = ""
        toolCalls = emptyList()
        statusMessage = null
        validationReason = null
        isValid = false
        runJob?.cancel()
        runJob = scope.launch {
            try {
                if (allowTools) {
                    // Tool-calling path — one-shot via
                    // `generateWithTools`. The schema isn't used on
                    // this branch because the model is free to pick
                    // any tool, but we keep the schema picker
                    // around for the structured branch.
                    val result = engine.generateWithTools(prompt)
                    when (result) {
                        is RunAnywhereStructuredEngine.ToolRunView.Done -> {
                            rawText = result.text
                            toolCalls = result.toolCalls
                            isValid = result.isComplete
                            if (!result.isComplete) {
                                validationReason = result.errorMessage
                            }
                        }
                        is RunAnywhereStructuredEngine.ToolRunView.Failed -> {
                            statusMessage = context.getString(R.string.structured_failed) +
                                ": " + result.message
                        }
                    }
                } else {
                    // Structured-output path — stream partial JSON
                    // until the SDK signals completion. The screen
                    // hands the engine a `Map<String, String>` of
                    // fields; the engine builds the `JSONSchema`
                    // proto internally so the screen never touches
                    // Wire-generated types.
                    val fieldList = template.fields
                    if (fieldList == null) {
                        statusMessage = "Custom schema: type the field list above"
                        running = false
                        return@launch
                    }
                    engine.generateStructuredFromFields(
                        prompt = prompt,
                        title = template.label,
                        description = template.description,
                        fields = fieldList,
                    ).collect { event ->
                        when (event) {
                            is RunAnywhereStructuredEngine.StructuredStreamView.Token ->
                                rawText += event.text
                            is RunAnywhereStructuredEngine.StructuredStreamView.PartialJson ->
                                partialJson = event.json
                            is RunAnywhereStructuredEngine.StructuredStreamView.Validation -> {
                                isValid = event.isValid
                                validationReason = event.reason
                            }
                            is RunAnywhereStructuredEngine.StructuredStreamView.Done -> {
                                rawText = event.rawText
                                partialJson = event.parsedJson ?: event.rawText
                                isValid = event.isValid
                                validationReason = event.errorMessage
                            }
                            is RunAnywhereStructuredEngine.StructuredStreamView.Failed ->
                                statusMessage = context.getString(R.string.structured_failed) +
                                    ": " + event.message
                        }
                    }
                }
            } finally {
                running = false
            }
        }
    }

    Scaffold(
        topBar = {
            MeshlitHeader(
                title = stringResource(R.string.structured_title),
                subtitle = stringResource(R.string.structured_subtitle),
                tier = app.capabilityTier,
                active = running,
                onOpenDrawer = onOpenDrawer,
            )
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TemplateRow(
                    current = template,
                    onPick = {
                        template = it
                        if (it == SchemaTemplate.Custom) showSchemaEditor = true
                    },
                )

                if (template == SchemaTemplate.Custom) {
                    OutlinedTextField(
                        value = customSchemaJson,
                        onValueChange = { customSchemaJson = it },
                        label = { Text(stringResource(R.string.structured_template_custom)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                    )
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.structured_prompt_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = allowTools,
                        onCheckedChange = { allowTools = it },
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        stringResource(R.string.structured_tools_toggle),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.size(12.dp))
                    Button(
                        onClick = ::run,
                        enabled = !running && prompt.isNotBlank(),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.structured_run))
                    }
                }

                statusMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(
                            msg,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                if (partialJson.isNotBlank()) {
                    SectionCard(stringResource(R.string.structured_partial)) {
                        Text(
                            text = partialJson,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (rawText.isNotBlank() || partialJson.isNotBlank()) {
                    SectionCard(stringResource(R.string.structured_section_result)) {
                        Text(
                            text = if (isValid) stringResource(R.string.structured_valid)
                            else stringResource(R.string.structured_invalid),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isValid)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                        validationReason?.takeIf { it.isNotBlank() }?.let { reason ->
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            text = partialJson.ifBlank { rawText },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (toolCalls.isNotEmpty()) {
                    SectionCard(stringResource(R.string.structured_section_tools)) {
                        toolCalls.forEach { call ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = call.name,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = call.arguments,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    call.resultJson?.takeIf { it.isNotBlank() }?.let { result ->
                                        Text(
                                            text = "→ $result",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    call.errorMessage?.takeIf { it.isNotBlank() }?.let { err ->
                                        Text(
                                            text = "✗ $err",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(
    current: SchemaTemplate,
    onPick: (SchemaTemplate) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.structured_schema) + ": " + current.label,
            style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { expanded = true }) {
                Text(current.label)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                SchemaTemplate.entries.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t.label) },
                        onClick = {
                            expanded = false
                            onPick(t)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

/**
 * Schema templates the screen can pick from. Each template's
 * [fields] list is passed to [RunAnywhereStructuredEngine.buildObjectSchema]
 * at run time; the engine owns the `JSONSchema` proto conversion
 * so this module never references Wire-generated types.
 *
 * The Custom template's [fields] is `null` — the screen shows a
 * "type the field list above" prompt and falls back to typed
 * input in the on-screen editor.
 */
private enum class SchemaTemplate(
    val label: String,
    val description: String,
    val fields: List<Triple<String, String, String>>?,
) {
    Contact("Contact", "Extract a contact record", contactFields),
    Todo("Todo", "Extract a task list", todoFields),
    Summary("Summary", "Summarize a passage", summaryFields),
    Sentiment("Sentiment", "Score the sentiment of a passage", sentimentFields),
    Custom("Custom JSON…", "User-authored schema", null);

    companion object {
        val entries: List<SchemaTemplate> get() = values().toList()
    }
}

private val contactFields = listOf(
    Triple("name", "string", "Person's full name"),
    Triple("email", "string", "Email address"),
    Triple("phone", "string", "Phone number"),
    Triple("city", "string", "City of residence"),
)

private val todoFields = listOf(
    Triple("title", "string", "Task title"),
    Triple("priority", "string", "low | medium | high"),
    Triple("due", "string", "ISO date or relative phrasing"),
    Triple("done", "boolean", "Whether the task is complete"),
)

private val summaryFields = listOf(
    Triple("headline", "string", "One-line headline"),
    Triple("key_points", "array", "Bullet points"),
    Triple("tone", "string", "neutral | positive | negative"),
)

private val sentimentFields = listOf(
    Triple("label", "string", "positive | neutral | negative"),
    Triple("score", "number", "Confidence 0..1"),
    Triple("evidence", "string", "Quoted text supporting the label"),
)
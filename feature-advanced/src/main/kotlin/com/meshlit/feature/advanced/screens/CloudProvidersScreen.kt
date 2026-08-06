package com.meshlit.feature.advanced.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Cloud providers — add / remove a cloud STT fallback (OpenAI,
 * Google Cloud Speech, AssemblyAI). The list is in-memory only in
 * this MVP; wiring it through the inference coordinator is a Step
 * 8 task.
 */
@Composable
fun CloudProvidersScreen(
    accent: Color,
    accentDim: Color,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("openai") }
    var apiKey by remember { mutableStateOf("") }
    val providers = remember { mutableStateListOf<Pair<String, String>>() }
    SectionScreen(
        title = "Cloud providers",
        subtitle = "STT cloud fallback.",
        accent = accent,
        accentDim = accentDim,
        onBack = onBack,
        content = {
            SectionCard(title = "Add", accent = accent) {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { providers.add(name to apiKey); apiKey = "" }) { Text("Add") }
            }
            SectionCard(title = "Configured", accent = accent) {
                providers.forEach { (n, _) ->
                    Text(n, style = MaterialTheme.typography.bodyMedium)
                }
                if (providers.isEmpty()) {
                    Text("None yet.", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
    )
}
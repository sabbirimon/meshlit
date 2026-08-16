package com.meshlit.feature.advanced.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

/**
 * Lists the YAML-driven engine chains bundled in the app assets.
 *
 * The cards deliberately read from `AssetManager` when expanded rather
 * than embedding a second copy in Kotlin. This keeps the configuration
 * shown to the user identical to what the native RunAnywhere solution
 * loader consumes.
 */
@Composable
fun SolutionsScreen(
    accent: Color,
    accentDim: Color,
    onBack: () -> Unit,
) {
    SectionScreen(
        title = "Solutions",
        subtitle = "YAML pipelines that chain engines.",
        accent = accent,
        accentDim = accentDim,
        onBack = onBack,
        content = {
            SolutionCard(
                title = "Voice agent",
                chain = "VAD → STT → LLM → TTS",
                assetPath = "solutions/voice_agent.yaml",
                accent = accent,
            )
            SolutionCard(
                title = "RAG",
                chain = "Query → Embed → Retrieve → Context → LLM",
                assetPath = "solutions/rag.yaml",
                accent = accent,
            )
        },
    )
}

@Composable
private fun SolutionCard(
    title: String,
    chain: String,
    assetPath: String,
    accent: Color,
) {
    val context = LocalContext.current
    var expanded by remember(assetPath) { mutableStateOf(false) }
    val yamlText = remember(expanded, assetPath) {
        if (!expanded) {
            null
        } else {
            runCatching {
                context.assets.open(assetPath).bufferedReader().use { it.readText() }
            }.getOrElse { error ->
                "Unable to load $assetPath: ${error.message ?: error::class.java.simpleName}"
            }
        }
    }

    SectionCard(title = title, accent = accent) {
        Text(
            text = chain,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (expanded) "Hide YAML" else "View YAML")
        }
        yamlText?.let { source ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                Text(
                    text = source,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

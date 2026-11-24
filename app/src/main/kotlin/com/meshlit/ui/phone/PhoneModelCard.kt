package com.meshlit.ui.phone

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.core.inference.models.HubRepository
import com.meshlit.models.LibraryModel
import com.meshlit.ui.modern.formatBytes
import java.text.NumberFormat

private fun modelNumber(value: Number) = NumberFormat.getIntegerInstance().format(value)

internal fun modelFeatures(repo: HubRepository?): List<String> {
    if (repo == null) return emptyList()
    val labels = mapOf("text-generation" to "Text generation", "conversational" to "Chat",
        "chat" to "Chat", "image-text-to-text" to "Vision", "vision" to "Vision",
        "automatic-speech-recognition" to "Transcription", "text-to-speech" to "Speech synthesis",
        "feature-extraction" to "Embeddings", "tool-use" to "Tool use", "function-calling" to "Tool use",
        "reasoning" to "Reasoning", "text-classification" to "Classification", "translation" to "Translation")
    return (listOfNotNull(repo.task) + repo.tags).mapNotNull { labels[it.lowercase()] }.distinct()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ModelBadges(labels: List<String>) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
        labels.forEach { label ->
            Surface(shape=RoundedCornerShape(6.dp), color=MaterialTheme.colorScheme.secondaryContainer,
                contentColor=MaterialTheme.colorScheme.onSecondaryContainer) {
                Text(label, Modifier.padding(horizontal=9.dp, vertical=4.dp), style=MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable internal fun ModelFacts(values: List<Pair<String,String>>) {
    if (values.isEmpty()) return
    Surface(shape=RoundedCornerShape(16.dp), color=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp)) {
            values.chunked(2).forEachIndexed { index, pair ->
                if (index > 0) HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(vertical=12.dp), horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    pair.forEach { (label,value) ->
                        Column(Modifier.weight(1f)) {
                            Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value,style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun InstalledModelDetails(model: LibraryModel, repository: HubRepository?, repoId: String?,
                                               activeContext: Int?, action: String, enabled: Boolean, run: () -> Unit) {
    val uri=androidx.compose.ui.platform.LocalUriHandler.current
    val meta=model.metadata
    val starter = model.bundled && repoId == "Qwen/Qwen2.5-0.5B-Instruct-GGUF"
    val task=repository?.task?.replace('-', ' ')?.replaceFirstChar { it.uppercase() }
    val quant=meta?.quantization
    val format=if (meta!=null || model.path.endsWith(".gguf",true) || model.url.endsWith(".gguf",true)) "GGUF" else null
    val title=if (quant!=null) model.name.removeSuffix(" · "+quant) else model.name
    val features=(modelFeatures(repository) + if(starter) listOf("Text generation","Chat","Instruction following","Multilingual") else emptyList()).distinct()
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(repoId!=null) PublisherLogo(repoId.substringBefore('/'))
                    else Icon(androidx.compose.ui.res.painterResource(com.meshlit.R.drawable.meshlit_mark),null,Modifier.size(32.dp))
                    Text(repoId?.substringBefore('/') ?: "Local model",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                    Text(if(action=="Unload") "Loaded" else model.phase,style=MaterialTheme.typography.labelLarge,
                        color=MaterialTheme.colorScheme.primary)
                }
                Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
                if(starter) Text("A compact instruction-tuned language model for multilingual text conversations.",
                    style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                ModelBadges(listOfNotNull(format,quant,model.sizeBytes.takeIf{it>0}?.let(::formatBytes)))
                FilledTonalButton(onClick=run,enabled=enabled){Text(action)}
            }
        }
        Text("Model specifications",style=MaterialTheme.typography.titleMedium)
        ModelFacts(listOfNotNull(
            (task ?: "Text generation".takeIf{starter})?.let{"Model type" to it},
            if (starter) "Parameters" to "0.49B" else repository?.parameters?.let { "GGUF parameters" to modelNumber(it) },
            (meta?.architecture ?: repository?.architecture)?.let{"Architecture" to it},
            meta?.blocks?.let{"Layers" to modelNumber(it)},
            (meta?.maxContext?.toLong() ?: repository?.contextLength)?.let{"Model context" to (modelNumber(it)+" tokens")},
            activeContext?.takeIf{it>0}?.let{"Active context" to (modelNumber(it)+" tokens")},
            (repository?.license ?: "Apache-2.0".takeIf{starter})?.let{"License" to it}
        ))
        Text("Capabilities",style=MaterialTheme.typography.titleMedium)
        if(features.isEmpty()) Text("Capabilities are unreported for this model.",style=MaterialTheme.typography.bodyMedium)
        else ModelBadges(features)
        repoId?.let { TextButton(onClick={uri.openUri("https://huggingface.co/"+it)}){Text("Publisher model card ↗")} }
    }
}

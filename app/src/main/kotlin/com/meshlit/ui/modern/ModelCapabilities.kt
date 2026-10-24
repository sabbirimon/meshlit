package com.meshlit.ui.modern
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshlit.models.LibraryModel
import com.meshlit.inference.RunAnywhereCatalog
import com.meshlit.di.koinInject
import com.meshlit.models.ModelLibrary
import com.meshlit.pipeline.PipelineHost
import com.meshlit.core.inference.models.DeviceRuntimePolicy

data class ModelFeature(val name:String,val evidence:String)
internal fun modelFeatures(model:LibraryModel?)=listOf(
    ModelFeature("Text / chat",if(model!=null) "Configured GGUF text runtime. Loading and a real response are required for device qualification." else "Depends on the selected provider and model; local capability is unknown."),
    ModelFeature("Coding", "Can be requested as text. No coding accuracy benchmark is recorded for this entry."),
    ModelFeature("Reasoning", "Can be requested as text. No reasoning benchmark or correctness guarantee is recorded."),
    ModelFeature("Tools / web / phone actions", "Requires explicit chat grants and a model qualified for the tool protocol. The starter model is not qualified for reliable autonomy."),
    ModelFeature("Images / camera", "Requires a vision-capable model and image adapter. A text GGUF entry alone does not enable it."),
    ModelFeature("Image / audio / video generation", "Separate media models and provider adapters are required; text-model availability does not prove these capabilities."),
    ModelFeature("Voice conversation", "Speech recognition and speech synthesis are separate from the selected text model. Installed Android voices can read its replies."),
    ModelFeature("Learning / memory", "Optional local preference recall and personality. This does not retrain weights or validate generated facts.")
)
@Composable internal fun ModelCapabilitiesDialog(model:LibraryModel?,selectedName:String,onClose:()->Unit) {
    val library=koinInject<ModelLibrary>()
    val pipeline=koinInject<PipelineHost>()
    val plan=remember(model?.id) { library.devicePlan() }
    AlertDialog(onDismissRequest=onClose,title={Text("Model details and capabilities")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(model?.name ?: selectedName,style=MaterialTheme.typography.titleMedium)
        model?.let{entry->
            Text("${entry.source} · ${entry.sizeBytes} bytes · ${if(entry.installed) "Installed" else entry.phase}")
            Text("Architecture: ${entry.metadata?.architecture ?: "unknown"} · Weights: ${entry.metadata?.quantization ?: "unknown"}")
            Text("Training context: ${entry.metadata?.maxContext ?: "unknown"} · runtime ${entry.runtimeOptions.backend}")
            val kv=entry.metadata?.kvBytes(entry.runtimeOptions.contextSize,entry.runtimeOptions.keyCacheType)
            Text("Device class: ${plan.category} · observed load budget ${formatBytes(plan.memoryBudget)} · planned CPU threads ${plan.cpuThreads}")
            Text(if(entry.installed && DeviceRuntimePolicy.fits(plan,entry.sizeBytes,kv))
                "Single-phone load estimate: fits current budget; actual load still needs a runtime check."
                else if(!entry.installed) "Single-phone load estimate: download/import first; catalog size is not a validated file."
                else "Single-phone load estimate: may exceed current budget or thermal/runtime limit. Change model/context or use approved workers.")
            Text("Layer sharding: ${pipeline.groupPeers().size} saved workers in ${pipeline.activeGroup()} · native runtime ${if(library.nativeRuntimeAvailable()) "packaged" else "unavailable"}. Two fresh approved workers, a complete coordinator GGUF and live negotiation are required.")
            Text("Multiple models: scenario routes run sequentially; parallel typed agent jobs may use separate approved providers. One phone model context serializes generation.")
            RunAnywhereCatalog.all.firstOrNull{catalog->catalog.id==entry.id || catalog.sources.any{it.url.isNotBlank() && it.url==entry.url}}?.let{catalog->
                Text("Catalog: ${catalog.family} · ${catalog.language} · ${catalog.license}")
                Text("Catalog use-case tags: ${catalog.strengths.joinToString()}. These are catalog descriptions, not device-tested accuracy or speed.")
            }
        }
        Text("Runtime availability, model-card claims and measured quality are different. No capability is inferred from a marketing name.")
        modelFeatures(model).forEach{feature->Text(feature.name,style=MaterialTheme.typography.titleSmall);Text(feature.evidence,style=MaterialTheme.typography.bodyMedium)}
    }},confirmButton={TextButton(onClick=onClose){Text("Close")}})
}

package com.meshlit.ui.modern
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.di.koinInject
import com.meshlit.models.LocalBehaviorSettings
import com.meshlit.core.inference.models.LocalModelBehavior
@Composable fun LocalBehaviorScreen(onBack:()->Unit,onModels:()->Unit) {
    val repository=koinInject<LocalBehaviorSettings>();val saved by repository.state.collectAsStateWithLifecycle()
    var instructions by remember(saved){mutableStateOf(saved.instructions)};var enabled by remember(saved){mutableStateOf(saved.enabled)};var error by remember{mutableStateOf<String?>(null)}
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{TextButton(onClick=onBack){Text("Back to settings")};Text("Custom local model behavior",style=MaterialTheme.typography.headlineSmall)}
        item{Text("Import compatible custom or uncensored GGUF weights in Models. A setting cannot remove a model’s learned refusals. These instructions apply to text generation through the local coordinator, including chat, model recipes and the phone provider. They do not modify hosted provider policies or grant tools, root or Android access.")}
        item{Row{Text("Apply custom local instructions",Modifier.weight(1f));Switch(enabled,{enabled=it})}}
        item{OutlinedTextField(instructions,{if(it.length<=4000) instructions=it},Modifier.fillMaxWidth(),label={Text("Your instructions")},minLines=5,maxLines=12,supportingText={Text("${instructions.length}/4,000 characters · takes effect on the next generation")})}
        item{Button(onClick={try{repository.save(LocalModelBehavior(enabled,instructions));error=null}catch(e:Exception){error=e.message}}){Text("Save behavior")};OutlinedButton(onClick=onModels){Text("Import or choose a local model")}}
        item{Text("No jailbreak success is claimed. Behavior depends on the selected weights and context budget. Save with the switch off to return to the original prompt; direct SDK vision/voice paths use their own controls.")}
        error?.let{item{ErrorCard(it){error=null}}}
    }
}

package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.core.inference.*
import com.meshlit.core.common.MeshlitResult
import com.meshlit.di.koinInject
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** Uses the current coordinator. JSON validation is not constrained decoding or schema proof. */
@Composable fun ModernStructuredScreen() {
    val coordinator=koinInject<InferenceCoordinator>()
    val state by coordinator.state.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var prompt by remember {mutableStateOf("")}
    var output by remember {mutableStateOf("")}
    var error by remember {mutableStateOf<String?>(null)}
    var job by remember {mutableStateOf<Job?>(null)}
    var running by remember {mutableStateOf(false)}
    DisposableEffect(Unit) {onDispose {if(running) {job?.cancel();coordinator.cancel()}}}
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Structured output",style=MaterialTheme.typography.headlineSmall)
        Text("Generate JSON with the loaded on-device model, then validate its actual output. Model compliance and JSON syntax are separate; this does not claim schema-constrained decoding.")
        OutlinedTextField(prompt,{if(it.length<=6000) prompt=it},Modifier.fillMaxWidth(),label={Text("Describe the JSON result")},minLines=4,maxLines=8,enabled=!running)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(enabled=!running && state is CoordinatorState.Ready && prompt.isNotBlank(),onClick={
                val path=coordinator.loadedModel()?.modelPath ?: return@Button
                job=scope.launch {running=true;error=null;output=""
                    try {
                        val result=withTimeout(120_000) {coordinator.infer(InferenceRequest(
                            prompt="Return a JSON object or array only. No Markdown fences. Task: $prompt",maxTokens=512,
                            expectedModelPath=path,onDeviceOnly=true,publishEvents=false,onToken={output+=it}))}
                        when(result) {
                            is MeshlitResult.Failure -> error("Generation failed: ${result.error.tag}")
                            is MeshlitResult.Success -> {output=result.value.finalText;validateStructuredJson(output)}
                        }
                    } catch(c:CancellationException){throw c}
                    catch(e:Exception){error=e.message ?: "Generation failed"}
                    finally{running=false}
                }
            }){Text("Generate JSON")}
            OutlinedButton(enabled=running,onClick={job?.cancel();coordinator.cancel()}){Text("Stop")}
        }
        if(running) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(output.isNotEmpty()) {
            SelectionContainer {Text(output,fontFamily=com.meshlit.ui.theme.MeshlitMapleMono)}
            MessageActions(output)
            OutlinedButton(enabled=!running,onClick={try{validateStructuredJson(output);error=null}catch(e:Exception){error=e.message}}){Text("Validate JSON")}
            if(!running && error==null) Text("Valid JSON object or array",color=MaterialTheme.colorScheme.primary)
        }
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }
}

internal fun validateStructuredJson(text:String) {
    require(text.length in 1..131072){"Output is empty or exceeds the JSON limit"}
    val parsed=try{Json.parseToJsonElement(text)}catch(_:Exception){throw IllegalArgumentException("Model output is not valid JSON; review it before use")}
    require(parsed is JsonObject || parsed is JsonArray){"JSON must be an object or array"}
}

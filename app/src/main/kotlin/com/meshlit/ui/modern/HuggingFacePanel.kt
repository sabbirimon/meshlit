package com.meshlit.ui.modern
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.meshlit.core.inference.models.*
import com.meshlit.models.*
import com.meshlit.ui.theme.ChatTokens as T
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

@Composable fun HuggingFacePanel(library:ModelLibrary,initiallyExpanded:Boolean=false) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val credentials=remember{ModelCredentials(context)}
    val client=remember{HuggingFaceClient()};val scope=rememberCoroutineScope()
    var expanded by remember{mutableStateOf(initiallyExpanded)};var tab by remember{mutableStateOf(0)}
    var query by remember{mutableStateOf("")};var repoId by remember{mutableStateOf("")}
    var results by remember{mutableStateOf<List<String>>(emptyList())}
    var repository by remember{mutableStateOf<HubRepository?>(null)}
    var quant by remember{mutableStateOf("")};var message by remember{mutableStateOf<String?>(null)}
    var running by remember{mutableStateOf<Job?>(null)}
    var config by remember{mutableStateOf(HostedModelConfig())};var savedConfig by remember{mutableStateOf(HostedModelConfig())}
    var inferenceToken by remember{mutableStateOf("")};var prompt by remember{mutableStateOf("")}
    var output by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(credentials){config=withContext(Dispatchers.IO){credentials.hostedConfig()};savedConfig=config}
    fun request(block:suspend()->Unit) {if(running?.isActive==true) return;running=scope.launch {
        message=null
        try{block()}catch(e:CancellationException){message="Request cancelled";throw e}
        catch(e:Exception){message=e.message ?: "Hugging Face request failed"}finally{running=null}
    }}
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)) {
        TextButton(onClick={expanded=!expanded}){Text(if(expanded) "Close Hugging Face" else "Hugging Face · Hub, access and hosted API")}
        if(expanded) {
            Row(horizontalArrangement=Arrangement.spacedBy(T.small)){FilterChip(tab==0,{tab=0},label={Text("Local downloads")});FilterChip(tab==1,{tab=1},label={Text("Hosted API")})}
            if(tab==0) {
                Text("Search real GGUF repositories or enter namespace/repository. Downloads pin the reported revision and checksum. A paid account does not override a model's license or gate.")
                OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Search Hub")},singleLine=true)
                OutlinedButton(enabled=query.isNotBlank() && running==null,onClick={request{val token=withContext(Dispatchers.IO){credentials.token()};results=client.search(query.trim(),token)}}){Text("Search")}
                results.take(20).forEach{id ->TextButton(onClick={repoId=id;request{val token=withContext(Dispatchers.IO){credentials.token()};repository=client.repository(id,token)}}){Text(id)}}
                OutlinedTextField(repoId,{repoId=it},Modifier.fillMaxWidth(),label={Text("namespace/repository")},singleLine=true)
                Button(enabled=repoId.isNotBlank() && running==null,onClick={request{val token=withContext(Dispatchers.IO){credentials.token()};repository=client.repository(repoId.trim(),token)}}){Text("Read repository files")}
                repository?.let { repo ->
                    Text("${repo.id} · license ${repo.license ?: "not reported"} · gated ${repo.gated} · private ${repo.private}")
                    OutlinedTextField(quant,{quant=it},Modifier.fillMaxWidth(),label={Text("Filter filename / quantization (Q4, Q8…)")})
                    val filtered=repo.files.filter{it.fileName.contains(quant,true)}
                    if(filtered.isEmpty()) Text("No supported single-file GGUF. Split GGUF, adapters and safetensors are not supported by this loader.")
                    filtered.take(12).forEach { artifact ->Column {
                        Text(artifact.fileName,style=MaterialTheme.typography.labelLarge)
                        Text("${artifact.sizeBytes?.let(::formatBytes) ?: "Size not reported"} · ${if(artifact.sha256!=null) "Pinned SHA-256" else "No published checksum"}",style=MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement=Arrangement.spacedBy(T.small)) {
                            TextButton(enabled=running==null,onClick={request{library.addHubArtifact(artifact,ModelDownloadBackend.VERIFIED_HTTP);message="Transfer queued in Models"}}){Text("HTTPS / token")}
                            TextButton(enabled=running==null && !repo.private && repo.gated=="false",onClick={request{library.addHubArtifact(artifact,ModelDownloadBackend.RUNANYWHERE);message="RunAnywhere transfer queued"}}){Text("RunAnywhere")}
                        }
                    }}
                    if(filtered.size>12) Text("Showing 12 of ${filtered.size}; narrow the filename filter.")
                }
            } else {
                Text("Hosted inference sends your prompt to Hugging Face and its selected provider. Requests may consume free credits or incur charges. This app does not purchase credits, know your balance, or guarantee a free tier.")
                Row {Text("Allow hosted requests (may be billed)",Modifier.weight(1f));Switch(config.enabled,{config=config.copy(enabled=it)})}
                OutlinedTextField(config.model,{config=config.copy(model=it)},Modifier.fillMaxWidth(),label={Text("namespace/model[:provider or cheapest]")},singleLine=true)
                OutlinedTextField(config.endpoint,{config=config.copy(endpoint=it)},Modifier.fillMaxWidth(),label={Text("HF router or dedicated HF endpoint")})
                OutlinedTextField(config.billTo,{config=config.copy(billTo=it)},Modifier.fillMaxWidth(),label={Text("Bill organization/resource group (optional)")},singleLine=true)
                OutlinedTextField(inferenceToken,{inferenceToken=it},Modifier.fillMaxWidth(),label={Text("New HF inference token (stored encrypted)")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
                Row(horizontalArrangement=Arrangement.spacedBy(T.small)) {
                    Button(enabled=running==null,onClick={request{withContext(Dispatchers.IO){credentials.saveHostedConfig(config);if(inferenceToken.isNotBlank()) credentials.saveInferenceToken(inferenceToken.trim())};savedConfig=config;inferenceToken="";message="Hosted configuration saved"}}){Text("Save")}
                    TextButton(enabled=running==null,onClick={request{withContext(Dispatchers.IO){credentials.saveInferenceToken("");credentials.saveHostedConfig(savedConfig.copy(enabled=false))};config=config.copy(enabled=false);savedConfig=savedConfig.copy(enabled=false);message="Inference token removed; hosted requests disabled"}}){Text("Remove token")}
                }
                OutlinedButton(enabled=running==null,onClick={request{val token=withContext(Dispatchers.IO){credentials.inferenceToken()};val response=client.hostedModels(token);output=(response.jsonObject["data"] as? JsonArray)?.take(20)?.mapNotNull{it.jsonObject["id"]?.jsonPrimitive?.contentOrNull}?.joinToString("\n") ?: response.toString()}}){Text("List actual hosted models")}
                OutlinedTextField(prompt,{prompt=it},Modifier.fillMaxWidth(),label={Text("Hosted request prompt")},minLines=2,maxLines=5)
                Button(enabled=savedConfig.enabled && savedConfig==config && prompt.isNotBlank() && running==null,onClick={request{
                    val token=withContext(Dispatchers.IO){credentials.inferenceToken()};val response=client.generate(savedConfig,token,prompt)
                    output=response.toString()
                }}){Text("Send to saved HF endpoint")}
                Text("Save configuration changes before sending. No automatic cloud fallback or automatic paid retries.",style=MaterialTheme.typography.bodySmall)
                output?.let{SelectionContainer{Text(it.take(16000),style=MaterialTheme.typography.bodySmall)}}
            }
            if(running!=null){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={running?.cancel()}){Text("Cancel request")}}
            message?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
        }
    }}
}

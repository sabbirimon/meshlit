package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.di.koinInject
import com.meshlit.providers.OnlineProviders
import com.meshlit.core.inference.models.*
import kotlinx.coroutines.*
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable fun OnlineProvidersScreen(onBack:()->Unit) {
    val providers=koinInject<OnlineProviders>();val profiles by providers.profiles.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope();val context=androidx.compose.ui.platform.LocalContext.current
    var editing by remember{mutableStateOf<OnlineProfile?>(null)}
    var key by remember{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)}
    var discovered by remember{mutableStateOf<List<String>>(emptyList())};var busy by remember{mutableStateOf(false)}
    var prices by remember{mutableStateOf<List<PublishedModelPrice>>(emptyList())}
    var freeOnly by remember{mutableStateOf(false)}
    var catalogQuery by remember{mutableStateOf("")}
    var inputRate by remember{mutableStateOf("")};var outputRate by remember{mutableStateOf("")}
    fun edit(profile:OnlineProfile){editing=profile;key="";discovered=emptyList();prices=emptyList();inputRate=profile.inputPricePerMillion?.toString().orEmpty();outputRate=profile.outputPricePerMillion?.toString().orEmpty()}
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{TextButton(onClick=onBack){Text("Back")};Text("Online models",style=MaterialTheme.typography.headlineSmall)
            Text("Requests send conversation content to the selected provider. Keys are encrypted locally. Local failures never trigger a cloud request. API access and billing are separate from consumer chat subscriptions.")}
        items(profiles,key={it.id}){profile->Card{Column(Modifier.padding(16.dp)){Text(profile.name,style=MaterialTheme.typography.titleMedium);Text("${profile.model.ifBlank{"Model not selected"}} · ${if(profile.enabled) "Enabled" else "Disabled"}");Text(profile.endpoint,style=MaterialTheme.typography.bodySmall)
            Row{TextButton(onClick={edit(profile)}){Text("Edit")};TextButton(onClick={providers.remove(profile.id)}){Text("Remove profile and key")}}}}}
        item{Text("Add a provider",style=MaterialTheme.typography.titleMedium);FlowRow{
            listOf(Triple("OpenAI","https://api.openai.com/v1",OnlineProtocol.OPENAI),Triple("Claude","https://api.anthropic.com/v1",OnlineProtocol.ANTHROPIC),
                Triple("DeepSeek","https://api.deepseek.com/v1",OnlineProtocol.OPENAI_COMPATIBLE),Triple("Qwen · US region","https://dashscope-us.aliyuncs.com/compatible-mode/v1",OnlineProtocol.OPENAI_COMPATIBLE),
                Triple("Gemini","https://generativelanguage.googleapis.com/v1beta",OnlineProtocol.GEMINI),Triple("Hugging Face","https://router.huggingface.co/v1",OnlineProtocol.OPENAI_COMPATIBLE),
                Triple("Agentgateway / LiteLLM","",OnlineProtocol.OPENAI_COMPATIBLE),Triple("Custom compatible","",OnlineProtocol.OPENAI_COMPATIBLE)).forEach{(name,url,protocol)->OutlinedButton(onClick={edit(OnlineProfile(UUID.randomUUID().toString(),name,url,protocol=protocol))}){Text(name)}}}}
        editing?.let{profile->item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Configure ${profile.name}",style=MaterialTheme.typography.titleMedium)
            OutlinedTextField(profile.name,{editing=profile.copy(name=it)},label={Text("Profile name")})
            OutlinedTextField(profile.endpoint,{editing=profile.copy(endpoint=it,pricingSource=null,priceCheckedAtMs=null);inputRate="";outputRate=""},label={Text("HTTPS API base URL")},supportingText={Text("Qwen requires an endpoint and key for your region/workspace. Custom keys are sent only to this endpoint.")})
            FlowRow{OnlineProtocol.entries.forEach{protocol->FilterChip(profile.protocol==protocol,{editing=profile.copy(protocol=protocol)},label={Text(protocol.name)})}}
            OutlinedTextField(profile.credentialEnvironmentId.orEmpty(),{editing=profile.copy(credentialEnvironmentId=it.takeIf{value->value.isNotBlank()})},label={Text("Optional Cloud vault API environment ID")},singleLine=true)
            if(profile.credentialEnvironmentId!=null) OutlinedTextField(profile.credentialVariable,{editing=profile.copy(credentialVariable=it)},label={Text("Vault variable name, e.g. OPENROUTER_API_KEY")},singleLine=true)
            Text("Vault API profiles must be bound to this endpoint's HTTPS origin. A vault reference overrides the saved key; agent use also needs vault delegation.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(key,{key=it},label={Text(if(providers.token(profile.id).isBlank()) "API key" else "Replacement key (blank keeps saved key)")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
            OutlinedTextField(profile.model,{editing=profile.copy(model=it,pricingSource=null,priceCheckedAtMs=null);inputRate="";outputRate=""},label={Text("Model ID")})
            OutlinedButton(enabled=!busy,onClick={scope.launch{busy=true;error=null;try{discovered=providers.client.models(profile.copy(enabled=false),if(profile.credentialEnvironmentId!=null) providers.resolveToken(profile) else key.ifBlank{providers.token(profile.id)})}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{busy=false}}}){Text(if(busy) "Discovering…" else "Discover models from API")}
            discovered.take(100).forEach{model->TextButton(onClick={editing=profile.copy(model=model)}){Text(model)}}
            Row{Text("API key authentication",Modifier.weight(1f));Switch(profile.requiresApiKey,{editing=profile.copy(requiresApiKey=it)})}
            Text("Disable key authentication only for an endpoint that explicitly supports anonymous requests. Public HF catalog discovery works without a login; hosted inference normally needs a token.",style=MaterialTheme.typography.bodySmall)
            Row{Text("Enable online requests",Modifier.weight(1f));Switch(profile.enabled,{editing=profile.copy(enabled=it)})}
            Row{Text("Allow delegated agents to use this profile",Modifier.weight(1f));Switch(profile.agentAllowed,{editing=profile.copy(agentAllowed=it)})}
            Row{Text("Send temperature (model must support it)",Modifier.weight(1f));Switch(profile.sendTemperature,{editing=profile.copy(sendTemperature=it)})}
            OutlinedTextField(inputRate,{inputRate=it;editing=profile.copy(pricingSource=null,priceCheckedAtMs=null)},label={Text("Input price / million tokens (optional)")})
            OutlinedTextField(outputRate,{outputRate=it;editing=profile.copy(pricingSource=null,priceCheckedAtMs=null)},label={Text("Output price / million tokens (optional)")})
            OutlinedTextField(profile.currency,{editing=profile.copy(currency=it.uppercase())},label={Text("Currency code")})
            PublishedPricingClient.officialPage(profile.endpoint)?.let{url->OutlinedButton(onClick={context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(url)))}){Text("Open current official prices")}}
            if(runCatching{java.net.URI(profile.endpoint).host=="router.huggingface.co"}.getOrDefault(false)) {
                OutlinedButton(enabled=!busy,onClick={scope.launch{busy=true;try{prices=PublishedPricingClient().huggingFace()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{busy=false}}}){Text("Refresh live HF provider prices")}
                OutlinedTextField(catalogQuery,{catalogQuery=it},label={Text("Filter live catalog by model or provider")})
                Row{Text("Only explicitly free offers",Modifier.weight(1f));Switch(freeOnly,{freeOnly=it})}
                val filteredPrices=prices.filter{(!freeOnly || it.isFree==true) && "${it.model} ${it.provider}".contains(catalogQuery,true)}
                Text("${filteredPrices.size} matching live offers. Free status comes from the catalog; missing status is unknown. Inference may still require login/key, quotas or regional access.")
                filteredPrices.take(100).forEach{price->TextButton(onClick={inputRate=price.inputPerMillion?.toString().orEmpty();outputRate=price.outputPerMillion?.toString().orEmpty();editing=profile.copy(model="${price.model}:${price.provider}",currency="USD",pricingSource=price.source,priceCheckedAtMs=price.fetchedAtMs)}){Text("${price.model} / ${price.provider}: input ${price.inputPerMillion ?: "unknown"}, output ${price.outputPerMillion ?: "unknown"} USD / million · free ${price.isFree ?: "unknown"} · select")}}
            }
            profile.priceCheckedAtMs?.let{Text("Price snapshot: ${java.util.Date(it)} · ${profile.pricingSource}. Refresh before relying on it.",style=MaterialTheme.typography.bodySmall)}
            Text("Prices are your assumptions, not a provider quote. Estimates exclude cache discounts, tools, taxes and unreported reasoning usage.",style=MaterialTheme.typography.bodySmall)
            Button(onClick={try{val input=inputRate.takeIf{it.isNotBlank()}?.let{it.toDoubleOrNull() ?: error("Invalid input price")};val output=outputRate.takeIf{it.isNotBlank()}?.let{it.toDoubleOrNull() ?: error("Invalid output price")};providers.save(profile.copy(inputPricePerMillion=input,outputPricePerMillion=output),key.takeIf{it.isNotBlank()});editing=null;key=""}catch(e:Exception){error=e.message}}){Text("Save profile")}
        }}}}
        error?.let{item{ErrorCard(it){error=null}}}
    }
}

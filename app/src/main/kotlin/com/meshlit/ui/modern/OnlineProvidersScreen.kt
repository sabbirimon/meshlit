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
import com.meshlit.providers.GatewayDashboardEndpoint
import com.meshlit.core.inference.models.*
import kotlinx.coroutines.*
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable fun OnlineProvidersScreen(onBack:()->Unit) {
    val providers=koinInject<OnlineProviders>();val profiles by providers.profiles.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope();val context=androidx.compose.ui.platform.LocalContext.current
    var editing by remember{mutableStateOf<OnlineProfile?>(null)}
    var dashboard by remember{mutableStateOf<GatewayDashboardEndpoint?>(null)}
    var key by remember{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)}
    var discovered by remember{mutableStateOf<List<String>>(emptyList())};var busy by remember{mutableStateOf(false)}
    var catalogJob by remember{mutableStateOf<Job?>(null)};var catalogRevision by remember{mutableIntStateOf(0)}
    var modelQuery by remember{mutableStateOf("")};var catalogAt by remember{mutableStateOf<Long?>(null)}
    var storedKey by remember{mutableStateOf<Boolean?>(null)}
    var prices by remember{mutableStateOf<List<PublishedModelPrice>>(emptyList())}
    var freeOnly by remember{mutableStateOf(false)}
    var catalogQuery by remember{mutableStateOf("")}
    var inputRate by remember{mutableStateOf("")};var outputRate by remember{mutableStateOf("")}
    fun cancelCatalog(){catalogRevision++;catalogJob?.cancel();catalogJob=null;busy=false;discovered=emptyList();catalogAt=null}
    fun edit(profile:OnlineProfile){cancelCatalog();editing=profile;key="";modelQuery="";prices=emptyList();inputRate=profile.inputPricePerMillion?.toString().orEmpty();outputRate=profile.outputPricePerMillion?.toString().orEmpty()}
    DisposableEffect(Unit){onDispose{catalogJob?.cancel()}}
    LaunchedEffect(editing?.id,editing?.endpoint,editing?.protocol,editing?.credentialEnvironmentId,editing?.credentialVariable,editing?.requiresApiKey,profiles) {
        cancelCatalog();storedKey=null
        val selected=editing ?: return@LaunchedEffect
        try {storedKey=withContext(Dispatchers.IO){providers.hasStoredKey(selected)}}
        catch(e:CancellationException){throw e}
        catch(_:Exception){storedKey=null}
    }
    dashboard?.let { address->GatewayDashboardScreen(address){dashboard=null};return }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{TextButton(onClick=onBack){Text("Back")};Text("Model connections",style=MaterialTheme.typography.headlineSmall)
            Text("Requests send conversation content to the selected provider. Keys are encrypted locally. Local failures never trigger a cloud request. API access and billing are separate from consumer chat subscriptions.")}
        item{OmniRouteDashboardSetup(providers){address->cancelCatalog();key="";dashboard=address}}
        items(profiles,key={it.id}){profile->Card{Column(Modifier.padding(16.dp)){Text(profile.name,style=MaterialTheme.typography.titleMedium);Text("${profile.model.ifBlank{"Model not selected"}} · ${if(profile.enabled) "Enabled" else "Disabled"}");Text(profile.endpoint,style=MaterialTheme.typography.bodySmall)
            Row{TextButton(onClick={edit(profile)}){Text("Edit")};TextButton(onClick={try{cancelCatalog();providers.remove(profile.id);if(editing?.id==profile.id){editing=null;key=""}}catch(_:Exception){error="Profile and key could not be removed."}}){Text("Remove profile and key")}}}}}
        item{Text("Device and cluster endpoints",style=MaterialTheme.typography.titleMedium)
            Text("The phone's loaded model and negotiated group are selected in Chat → Conversation settings. Add a compatible endpoint here for a local server, remote device or existing cluster. Saving a group endpoint does not start or shard a model.")
            FlowRow { listOf(ModelSourceKind.LOCAL_SERVER to "Local server",ModelSourceKind.MULTI_LOCAL to "Multiple local models",ModelSourceKind.REMOTE_DEVICE to "Remote device",ModelSourceKind.GROUP_CLUSTER to "Group / cluster").forEach { (kind,label) ->
                OutlinedButton(onClick={edit(OnlineProfile(UUID.randomUUID().toString(),label,"",sourceKind=kind))}) { Text(label) }
            } }
        }
        item{Text("Add a provider",style=MaterialTheme.typography.titleMedium);FlowRow{
            listOf(Triple("OpenAI","https://api.openai.com/v1",OnlineProtocol.OPENAI),Triple("Claude","https://api.anthropic.com/v1",OnlineProtocol.ANTHROPIC),
                Triple("DeepSeek","https://api.deepseek.com/v1",OnlineProtocol.OPENAI_COMPATIBLE),Triple("Qwen · US region","https://dashscope-us.aliyuncs.com/compatible-mode/v1",OnlineProtocol.OPENAI_COMPATIBLE),
                Triple("Gemini","https://generativelanguage.googleapis.com/v1beta",OnlineProtocol.GEMINI),Triple("Hugging Face","https://router.huggingface.co/v1",OnlineProtocol.OPENAI_COMPATIBLE),
                Triple("OmniRoute (optional)","",OnlineProtocol.OPENAI_COMPATIBLE),Triple("FreeLLMAPI (experimental)","",OnlineProtocol.OPENAI_COMPATIBLE),
                Triple("Agentgateway / LiteLLM","",OnlineProtocol.OPENAI_COMPATIBLE),Triple("Custom compatible","",OnlineProtocol.OPENAI_COMPATIBLE)).forEach{(name,url,protocol)->OutlinedButton(onClick={edit(OnlineProfile(UUID.randomUUID().toString(),name,url,protocol=protocol,gateway=when { name.startsWith("OmniRoute") -> OnlineGateway.OMNIROUTE; name.startsWith("FreeLLMAPI") -> OnlineGateway.FREELLMAPI; name.startsWith("Agentgateway") -> OnlineGateway.CUSTOM; else -> OnlineGateway.DIRECT }))}){Text(name)}}}}
        editing?.let{profile->item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Configure ${profile.name}",style=MaterialTheme.typography.titleMedium)
            Text("Source: ${profile.sourceKind.name.lowercase().replace('_',' ')}")
            if (profile.gateway != OnlineGateway.DIRECT) Text("Optional gateway: prompts and keys go to your chosen deployment. Downstream routing, retries, quota and costs are controlled there. Local free-only and budgeted requests block opaque gateways. FreeLLMAPI is experimental, for personal use. No provider entitlement is included.")
            OutlinedTextField(profile.name,{editing=profile.copy(name=it)},label={Text("Profile name")})
            OutlinedTextField(profile.endpoint,{cancelCatalog();key="";editing=profile.copy(endpoint=it,pricingSource=null,priceCheckedAtMs=null);inputRate="";outputRate=""},label={Text("API base URL · HTTPS or loopback") },supportingText={Text("Use the host's model API base, commonly ending in /v1; the dashboard has its own address above. Changing endpoint or protocol clears the old key when saved; enter a key bound to the new endpoint.")})
            if(profile.protocol==OnlineProtocol.OPENAI_COMPATIBLE) Row {
                Text("This endpoint routes to other providers", Modifier.weight(1f))
                Switch(profile.gateway != OnlineGateway.DIRECT, { editing=profile.copy(gateway=if(it) OnlineGateway.CUSTOM else OnlineGateway.DIRECT) })
            }
            FlowRow{OnlineProtocol.entries.forEach{protocol->FilterChip(profile.protocol==protocol,{cancelCatalog();key="";editing=profile.copy(protocol=protocol,gateway=if(protocol==OnlineProtocol.OPENAI_COMPATIBLE) profile.gateway else OnlineGateway.DIRECT)},label={Text(protocol.name)})}}
            OutlinedTextField(profile.credentialEnvironmentId.orEmpty(),{editing=profile.copy(credentialEnvironmentId=it.takeIf{value->value.isNotBlank()})},label={Text("Optional Cloud vault API environment ID")},singleLine=true)
            if(profile.credentialEnvironmentId!=null) OutlinedTextField(profile.credentialVariable,{editing=profile.copy(credentialVariable=it)},label={Text("Vault variable name, e.g. OPENROUTER_API_KEY")},singleLine=true)
            Text("Vault API profiles must be bound to this endpoint's HTTPS origin. A vault reference overrides the saved key; agent use also needs vault delegation.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(key,{cancelCatalog();key=it.take(4096)},label={Text(if(storedKey==true) "Replacement key (blank keeps saved key)" else "API key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,
                supportingText={Text(when(storedKey){true->"Key stored encrypted for this exact endpoint and protocol";false->"No saved key for this endpoint";null->"Stored-key status unavailable or checking"})})
            if(storedKey==true) TextButton(onClick={try{cancelCatalog();providers.removeStoredKey(profile.id);editing=profile.copy(enabled=false);key="";storedKey=false}catch(_:Exception){error="Saved key could not be removed."}}){Text("Remove saved key and disable profile")}
            if(profile.gateway==OnlineGateway.OMNIROUTE) Text("Use an OmniRoute client API key here. Manage upstream provider keys, accounts and routing on its dashboard. Removing this saved key disables this profile; it does not revoke the key on OmniRoute or delete a Cloud vault credential.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(profile.model,{editing=profile.copy(model=it,pricingSource=null,priceCheckedAtMs=null);inputRate="";outputRate=""},label={Text("Model ID")})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled=!busy,onClick={
                    cancelCatalog();val revision=catalogRevision;val enteredKey=key
                    catalogJob=scope.launch{busy=true;error=null
                        try {
                            val rows=withTimeout(15000){val token=withContext(Dispatchers.IO){if(profile.credentialEnvironmentId!=null) providers.resolveToken(profile) else enteredKey.ifBlank{providers.tokenFor(profile)}}
                                providers.client.models(profile.copy(enabled=false),token)}
                            ensureActive()
                            if(revision==catalogRevision) {discovered=rows.filter{it.length in 1..200 && it.none{char->char.isWhitespace() || char.isISOControl() || char in "?#\\"}}.distinct().sorted();catalogAt=System.currentTimeMillis()}
                        } catch(_:TimeoutCancellationException){if(revision==catalogRevision)error="Model catalog exceeded 15 seconds. No automatic retry was made."}
                        catch(e:CancellationException){throw e}
                        catch(_:Exception){if(revision==catalogRevision)error="Model discovery failed. Check the exact API base, stored key, host availability and TLS certificate."}
                        finally{if(revision==catalogRevision){busy=false;catalogJob=null}}
                    }
                }){Text(if(busy) "Refreshing…" else "Refresh model catalog")}
                if(catalogJob!=null) TextButton(onClick={cancelCatalog();error="Model discovery stopped."}){Text("Stop")}
            }
            Text("Refresh sends only a model-catalog request to this API host. It does not generate text or verify upstream accounts, free usage, credits or model readiness.",style=MaterialTheme.typography.bodySmall)
            if(catalogAt!=null) {
                OutlinedTextField(modelQuery,{modelQuery=it.take(200)},Modifier.fillMaxWidth(),label={Text("Search returned model IDs")},singleLine=true)
                val choices=discovered.filter{it.contains(modelQuery,true)}
                Text("${choices.size} matching models · catalog checked ${java.util.Date(catalogAt!!)}. Showing up to 100 matches; narrow the search. Selection applies after Save profile.",style=MaterialTheme.typography.bodySmall)
                if(discovered.isEmpty()) Text("This API returned no usable model IDs for this key. Configure providers in the host dashboard, then refresh.")
                choices.take(100).forEach{model->TextButton(onClick={editing=profile.copy(model=model,pricingSource=null,priceCheckedAtMs=null);inputRate="";outputRate=""}){Text(if(profile.model==model) "$model · selected" else model)}}
            }
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
            Row {
                Button(onClick={try{val input=inputRate.takeIf{it.isNotBlank()}?.let{it.toDoubleOrNull() ?: error("Invalid input price")};val output=outputRate.takeIf{it.isNotBlank()}?.let{it.toDoubleOrNull() ?: error("Invalid output price")};providers.save(profile.copy(inputPricePerMillion=input,outputPricePerMillion=output,pricingSource=profile.pricingSource ?: "User reviewed rates",priceCheckedAtMs=System.currentTimeMillis()),key.takeIf{it.isNotBlank()});cancelCatalog();editing=null;key=""}catch(_:Exception){error="Profile could not be saved. Check its endpoint, model, key and numeric price fields."}}){Text("Save profile")}
                TextButton(onClick={cancelCatalog();editing=null;key=""}){Text("Cancel")}
            }
        }}}}
        error?.let{item{ErrorCard(it){error=null}}}
    }
}

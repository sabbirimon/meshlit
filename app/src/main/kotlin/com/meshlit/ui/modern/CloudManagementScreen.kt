package com.meshlit.ui.modern

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.cloud.CloudManagement
import com.meshlit.control.AgentBackend
import com.meshlit.core.cloudmcp.management.*
import com.meshlit.di.koinInject
import com.meshlit.ui.theme.ChatTokens as T
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun CloudManagementScreen(back:()->Unit,onHuggingFace:(()->Unit)?=null){
    val repository=koinInject<CloudManagement>();val backend=koinInject<AgentBackend>()
    val state by repository.state.collectAsStateWithLifecycle();val scope=rememberCoroutineScope()
    var ready by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)}
    var tab by remember{mutableStateOf("Dashboard")};var profile by remember{mutableStateOf<CloudProfile?>(null)}
    var environment by remember{mutableStateOf<EnvironmentDescription?>(null)}
    var secretName by remember{mutableStateOf("")};var secretValue by remember{mutableStateOf("")};var functions by remember{mutableStateOf("")}
    var delegated by remember{mutableStateOf(backend.delegated(AgentBackend.Scope.CLOUD))}
    val pretty=remember{Json{prettyPrint=true}}
    BackHandler{back()}
    fun run(action:suspend()->Unit){scope.launch{busy=true;error=null;try{action()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message ?: "Cloud operation failed"}finally{busy=false}}}
    LaunchedEffect(repository){try{repository.ready.await();ready=true}catch(e:CancellationException){throw e}catch(_:Exception){error="Encrypted cloud storage could not be opened; no profiles were reset"}}
    Scaffold(topBar={TopAppBar(title={Text("Cloud management dashboard")},navigationIcon={IconButton(onClick=back){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}})}){padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(T.large),verticalArrangement=Arrangement.spacedBy(T.medium)){
            if(onHuggingFace!=null) item{OutlinedButton(onClick=onHuggingFace,modifier=Modifier.fillMaxWidth()){Text("Hugging Face account, downloads and inference")}}
            item{FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){listOf("Dashboard","Providers","Credentials","Agent settings").forEach{label->FilterChip(tab==label,{tab=label;profile=null;environment=null;secretName="";secretValue=""},label={Text(label)})}}}
            if(busy || !ready) item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            error?.let{item{ErrorCard(it){error=null}}}
            when(tab){
                "Dashboard"->{
                    item{Text("Resources and costs",style=MaterialTheme.typography.headlineSmall);Text("Explicit refresh only. Results identify the source and fetch time. Inventory may be paginated; missing costs stay unknown. Provisioning, deletion, budgets and deployment are not implemented by these read adapters.")}
                    item{Card{Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)){
                        Text("Cost metric coverage",style=MaterialTheme.typography.titleMedium)
                        Text("AWS · month-to-date Cost Explorer amount (through yesterday UTC, when authorized)")
                        Text("Azure · ActualCost query (when authorized)")
                        Text("DigitalOcean · balance and month-to-date usage")
                        Text("OpenRouter · key usage and limits; currency depends on provider response")
                        Text("GCP · billing association only; spend unavailable without a billing export")
                        Text("Hugging Face and custom providers · live spend unavailable")
                        Text("Other clouds · connect a scoped read-only custom HTTPS API for raw data; normalized costs need a provider adapter. No currency totals are synthesized from unrelated responses.",style=MaterialTheme.typography.bodySmall)
                    }}}
                    if(state.profiles.isEmpty()) item{Text("Create an encrypted credential environment and configure a provider to see real resources.")}
                    items(state.profiles,key={it.id}){p->Card{Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)){
                        Text("${p.name} · ${p.vendor}",style=MaterialTheme.typography.titleMedium)
                        Text("Human: ${if(p.humanEnabled) "enabled" else "disabled"} · Agent: ${if(p.agentEnabled) "enabled" else "disabled"}")
                        p.actions().forEach{action ->
                            val observation=state.observations.firstOrNull{it.profileId==p.id && it.action==action}
                            OutlinedButton(enabled=ready && !busy && p.humanEnabled && action in p.humanActions,onClick={run{repository.execute(p.id,action,CloudActor.HUMAN)}}){Text("Refresh $action")}
                            if(observation!=null){
                                Text("${observation.action} · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(observation.fetchedAtMs))}",style=MaterialTheme.typography.labelMedium)
                                Text(observation.source,style=MaterialTheme.typography.bodySmall)
                                Text(observation.note+if(observation.partial) ". Response is partial or truncated; this is not a full inventory." else "")
                                val summary=CloudDashboard.summary(p.vendor,action,observation.data)
                                summary.metrics.forEach{(label,value)->Row(Modifier.fillMaxWidth()){Text(label,Modifier.weight(1f));Text(value)}}
                                if(summary.resources.isNotEmpty()) Text("${summary.resources.size} resources shown from this response (at most 100)",style=MaterialTheme.typography.titleSmall)
                                summary.resources.take(10).forEach{(id,label)->Text("$label · $id",style=MaterialTheme.typography.bodySmall)}
                                val output=pretty.encodeToString(kotlinx.serialization.json.JsonElement.serializer(),observation.data)
                                var expanded by remember(p.id,action){mutableStateOf(false)}
                                TextButton(onClick={expanded=!expanded}){Text(if(expanded) "Hide API details" else "Show API details")}
                                if(expanded) SelectionContainer{Text(output.take(12000),style=MaterialTheme.typography.bodySmall)}
                                if(expanded && output.length>12000) Text("Display truncated at 12,000 characters; full bounded result is available through the typed command.")
                            }
                        }
                    }}}
                }
                "Providers"->{
                    item{Text("Provider configuration",style=MaterialTheme.typography.headlineSmall);Text("AWS temporary or access keys, Azure/GCP short-lived bearer tokens, DigitalOcean/OpenRouter tokens, and custom HTTPS GET adapters. OAuth login/refresh and arbitrary service deployment need future adapters.")}
                    items(state.profiles,key={it.id}){p->Card{Row(Modifier.padding(T.medium)){
                        Text("${p.name} · ${p.vendor}",Modifier.weight(1f));TextButton(enabled=!busy,onClick={profile=p;functions=p.functions.joinToString("\n"){"${it.id}=${it.path}"}}){Text("Edit")};TextButton(enabled=!busy,onClick={run{repository.removeProfile(p.id)}}){Text("Remove")}
                    }}}
                    item{Button(enabled=ready && !busy,onClick={profile=CloudProfile(UUID.randomUUID().toString(),"",CloudVendor.AWS,state.environments.firstOrNull{it.purpose in setOf(EnvironmentPurpose.CLOUD,EnvironmentPurpose.API)}?.id.orEmpty());functions=""}){Text("Add provider")}}
                    profile?.let{p->item{Card{Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)){
                        OutlinedTextField(p.name,{profile=p.copy(name=it)},label={Text("Profile name")},singleLine=true)
                        FlowRow{CloudVendor.entries.forEach{vendor->FilterChip(p.vendor==vendor,{profile=p.copy(vendor=vendor,humanActions=emptySet(),agentActions=emptySet())},label={Text(vendor.name)})}}
                        Text("Credential environment",style=MaterialTheme.typography.titleSmall)
                        FlowRow{state.environments.filter{it.purpose in setOf(EnvironmentPurpose.CLOUD,EnvironmentPurpose.API)}.forEach{e->FilterChip(p.environmentId==e.id,{profile=p.copy(environmentId=e.id)},label={Text(e.name)})}}
                        Text("Variables: ${p.credentialNames().joinToString()}",style=MaterialTheme.typography.bodySmall)
                        if(p.vendor==CloudVendor.AWS) OutlinedTextField(p.region,{profile=p.copy(region=it)},label={Text("AWS region (costs adapter uses us-east-1)")},singleLine=true)
                        if(p.vendor==CloudVendor.GCP) OutlinedTextField(p.project,{profile=p.copy(project=it)},label={Text("GCP project ID for compute/billing")},singleLine=true)
                        if(p.vendor==CloudVendor.CUSTOM){
                            OutlinedTextField(p.customOrigin,{profile=p.copy(customOrigin=it)},label={Text("HTTPS origin, e.g. https://api.example.com")},singleLine=true)
                            OutlinedTextField(functions,{text->functions=text;val parsed=text.lineSequence().filter{it.isNotBlank()}.map{line->val pair=line.split('=',limit=2);CloudFunction(pair[0].trim(),pair.getOrElse(1){""}.trim())}.toList();profile=p.copy(functions=parsed,humanActions=p.humanActions.intersect(parsed.map{it.id}.toSet()),agentActions=p.agentActions.intersect(parsed.map{it.id}.toSet()))},label={Text("Functions, one id=/path per line (GET only)")},maxLines=5)
                        }
                        Text("Human settings",style=MaterialTheme.typography.titleMedium)
                        CloudSwitch("Enable human cloud calls",p.humanEnabled){profile=p.copy(humanEnabled=it)}
                        FlowRow{p.actions().forEach{action->FilterChip(action in p.humanActions,{profile=p.copy(humanActions=if(action in p.humanActions) p.humanActions-action else p.humanActions+action)},label={Text(action)})}}
                        Text("Agent settings",style=MaterialTheme.typography.titleMedium)
                        CloudSwitch("Delegate this provider to agents",p.agentEnabled){profile=p.copy(agentEnabled=it)}
                        FlowRow{p.actions().forEach{action->FilterChip(action in p.agentActions,{profile=p.copy(agentActions=if(action in p.agentActions) p.agentActions-action else p.agentActions+action)},label={Text(action)})}}
                        OutlinedTextField(p.maxAgentCallsPerDay.toString(),{value->value.toIntOrNull()?.let{profile=p.copy(maxAgentCallsPerDay=it)}},label={Text("Agent cloud calls per UTC day (0–1000)")},singleLine=true)
                        OutlinedTextField(p.minimumRefreshSeconds.toString(),{value->value.toIntOrNull()?.let{profile=p.copy(minimumRefreshSeconds=it)}},label={Text("Refresh cooldown, seconds (5–3600)")},singleLine=true)
                        if(p.vendor==CloudVendor.AWS) CloudSwitch("Allow metered Cost Explorer queries",p.meteredReadsAllowed){profile=p.copy(meteredReadsAllowed=it)}
                        Text("Agent calls also need global Cloud delegation, per-device Cloud approval, and permission to use the chosen environment. No action can enable its own access.")
                        Row{Button(enabled=!busy,onClick={run{repository.saveProfile(p);profile=null}}){Text("Save provider")};TextButton(onClick={profile=null}){Text("Cancel")}}
                    }}}}
                }
                "Credentials"->{
                    item{Text("Credential and environment vault",style=MaterialTheme.typography.headlineSmall);Text("All values are encrypted on this installation. Variables are resolved only by a bound service; they are never global process environment or agent outputs. SSH uses SSH_PASSWORD or SSH_PRIVATE_KEY; web login uses LOGIN_USERNAME and LOGIN_PASSWORD with an exact HTTPS origin. API tokens are managed by name. Export of secret values is unavailable.")}
                    items(state.environments,key={it.id}){e->Card{Column(Modifier.padding(T.large)){
                        Text("${e.name} · ${e.purpose}",style=MaterialTheme.typography.titleMedium);SelectionContainer{Text("ID: ${e.id}",style=MaterialTheme.typography.bodySmall)};Text(e.variableNames.joinToString().ifEmpty{"No variables"});Text("Agent use: ${e.agentAllowed}"+if(e.serviceBinding.isNotBlank()) " · ${e.serviceBinding}" else "")
                        e.expiresAtMs?.let{Text("Expiry: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))}")}
                        Row{TextButton(enabled=!busy,onClick={environment=e;secretName="";secretValue=""}){Text("Edit / rotate")};TextButton(enabled=!busy,onClick={run{repository.removeEnvironment(e.id)}}){Text("Delete vault profile")}}
                    }}}
                    item{Button(enabled=ready && !busy,onClick={environment=EnvironmentDescription(UUID.randomUUID().toString(),"",emptyList(),false,null,EnvironmentPurpose.CLOUD,"");secretName="";secretValue=""}){Text("Add environment or credential profile")}}
                    environment?.let{e->item{Card{Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)){
                        OutlinedTextField(e.name,{environment=e.copy(name=it)},label={Text("Environment name")},singleLine=true)
                        FlowRow{EnvironmentPurpose.entries.forEach{purpose->FilterChip(e.purpose==purpose,{environment=e.copy(purpose=purpose)},label={Text(purpose.name)})}}
                        if(e.purpose in setOf(EnvironmentPurpose.SSH,EnvironmentPurpose.WEB_LOGIN,EnvironmentPurpose.API)) OutlinedTextField(e.serviceBinding,{environment=e.copy(serviceBinding=it)},label={Text(if(e.purpose==EnvironmentPurpose.SSH) "Exact SSH host / IP" else "Exact HTTPS service origin (API/login)")},singleLine=true)
                        OutlinedTextField(e.expiresAtMs?.toString().orEmpty(),{value->if(value.isBlank()) environment=e.copy(expiresAtMs=null) else value.toLongOrNull()?.let{environment=e.copy(expiresAtMs=it)}},label={Text("Optional expiry, Unix milliseconds")},singleLine=true)
                        CloudSwitch("Allow agents to consume this environment",e.agentAllowed){environment=e.copy(agentAllowed=it)}
                        e.variableNames.forEach{name->Row{Text(name,Modifier.weight(1f));TextButton(enabled=!busy,onClick={run{repository.saveEnvironment(e,removeVariable=name);environment=repository.state.value.environments.single{it.id==e.id}}}){Text("Remove variable")}}}
                        OutlinedTextField(secretName,{secretName=it},label={Text("Variable / credential name, e.g. OPENROUTER_API_KEY")},singleLine=true)
                        OutlinedTextField(secretValue,{secretValue=it},label={Text("New value (blank preserves saved values)")},keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),visualTransformation=PasswordVisualTransformation(),maxLines=4)
                        Row{Button(enabled=!busy,onClick={val name=secretName;val value=secretValue;run{require(name.isBlank()==value.isBlank()){ "Provide both variable name and value, or leave both blank" };repository.saveEnvironment(e,name.takeIf{it.isNotBlank()},value.takeIf{it.isNotBlank()});secretName="";secretValue="";environment=null}}){Text("Save encrypted")};TextButton(onClick={environment=null;secretName="";secretValue=""}){Text("Cancel")}}
                    }}}}
                }
                else->item{Card{Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)){
                    Text("Agent cloud access",style=MaterialTheme.typography.headlineSmall)
                    CloudSwitch("Global Cloud delegation",delegated){value->run{backend.setDelegated(AgentBackend.Scope.CLOUD,value);delegated=value}}
                    Text("Agents use CLOUD_PROFILES, ENVIRONMENT_PROFILES and CLOUD_EXECUTE. Commands reference profiles and action IDs; credentials never enter the command journal. Credential edits and permission changes are human-only. Revocation is checked again before saving a result.")
                    Text("Paid/destructive provisioning, OAuth refresh, service-specific API schemas and cross-cloud deployments remain future work.")
                }}}
            }
        }
    }
}
@Composable private fun CloudSwitch(label:String,value:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth()){Text(label,Modifier.weight(1f));Switch(value,onChange)}}

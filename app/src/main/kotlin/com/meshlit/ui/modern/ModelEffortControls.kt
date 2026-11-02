package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meshlit.core.inference.models.*
import java.net.URI

internal fun chatSupportedEfforts(options:com.meshlit.chat.ChatOptions,profiles:List<OnlineProfile>,routes:List<ModelRoute>):Set<ReasoningEffort> {
    if(options.onlineProfileId!=null)return profiles.firstOrNull{it.id==options.onlineProfileId && it.enabled}?.supportedEfforts().orEmpty()
    val choices=if(options.routeId=="__auto__")routes.filter{it.enabled} else routes.filter{it.id==options.routeId && it.enabled}
    if(options.routeId==null || choices.isEmpty() || choices.any{route->route.steps.isEmpty() || route.steps.any{!it.modelId.startsWith("cloud:")}})return emptySet()
    return choices.flatMap{it.steps}.map{step->profiles.firstOrNull{it.id==step.modelId.removePrefix("cloud:") && it.enabled}?.supportedEfforts().orEmpty()}
        .reduceOrNull{a,b->a.intersect(b)}.orEmpty()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ModelEffortSetup(profile:OnlineProfile,onChange:(OnlineProfile)->Unit) {
    var open by remember{mutableStateOf(false)}
    val supported=profile.supportedEfforts()
    Text("Reasoning effort",style=MaterialTheme.typography.titleSmall)
    Text(if(supported.isEmpty())"Auto only · no current model-specific support declared" else "Configured levels: ${supported.joinToString{it.label}}",style=MaterialTheme.typography.bodySmall)
    if(profile.protocol in setOf(OnlineProtocol.OPENAI,OnlineProtocol.OPENAI_COMPATIBLE)) {
        TextButton(onClick={open=true},enabled=profile.model.isNotBlank()){Text("Configure documented effort support")}
        if(profile.effortSupport!=null)TextButton(onClick={onChange(profile.copy(effortSupport=null))}){Text("Clear effort support")}
    }
    if(open) {
        var adapter by remember(profile.endpoint,profile.model){mutableStateOf(profile.effortSupport?.takeIf{it.endpoint==profile.endpoint && it.model==profile.model}?.adapter ?: EffortAdapter.OPENAI)}
        var values by remember(profile.endpoint,profile.model){mutableStateOf(supported)}
        AlertDialog(onDismissRequest={open=false},title={Text("Review this model's effort API")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("${profile.endpoint}\n${profile.model}",style=MaterialTheme.typography.bodySmall)
            Text("Choose only settings documented by this provider for this exact model. This declaration is not a live execution test. Changes apply after Save profile.")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                FilterChip(adapter==EffortAdapter.OPENAI,{adapter=EffortAdapter.OPENAI},label={Text("reasoning_effort")})
                if(runCatching{URI(profile.endpoint).host=="openrouter.ai"}.getOrDefault(false))FilterChip(adapter==EffortAdapter.OPENROUTER,{adapter=EffortAdapter.OPENROUTER},label={Text("OpenRouter reasoning.effort")})
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {ReasoningEffort.entries.filter{it!=ReasoningEffort.AUTO}.forEach { level ->
                FilterChip(level in values,{values=if(level in values)values-level else values+level},label={Text(level.label)})
            } }
        }},confirmButton={TextButton(enabled=values.isNotEmpty(),onClick={onChange(profile.copy(effortSupport=EffortSupport(profile.endpoint,profile.model,adapter,values)));open=false}){Text("Use declared support")}},dismissButton={TextButton(onClick={open=false}){Text("Cancel")}})
    }
}

@Composable internal fun ReasoningEffortButton(effort:ReasoningEffort,allowed:Set<ReasoningEffort>,enabled:Boolean,onChange:(ReasoningEffort)->Unit) {
    var menu by remember{mutableStateOf(false)}
    Box {
        TextButton(onClick={menu=true},enabled=enabled,modifier=Modifier.testTag("chat-effort-selector"),contentPadding=PaddingValues(horizontal=6.dp)){Text("${effort.label} ▾",style=MaterialTheme.typography.labelMedium)}
        DropdownMenu(menu,{menu=false}) {
            DropdownMenuItem(text={Text(if(allowed.isEmpty())"Auto only · model support unavailable" else "Reasoning effort")},enabled=false,onClick={})
            ReasoningEffort.entries.forEach { value ->
            DropdownMenuItem(text={Text(value.label+(if(value==effort)" ✓" else ""))},enabled=value==ReasoningEffort.AUTO || value in allowed,onClick={menu=false;onChange(value)})
        } }
    }
}

package com.meshlit.core.inference.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.*
import java.net.URI

@Serializable enum class ReasoningEffort(val label:String,val wire:String) {
    @SerialName("auto") AUTO("Auto","auto"),
    @SerialName("low") LOW("Low","low"),
    @SerialName("medium") MEDIUM("Medium","medium"),
    @SerialName("high") HIGH("High","high")
}
@Serializable enum class EffortAdapter {
    @SerialName("reasoning_effort") OPENAI,
    @SerialName("reasoning.effort") OPENROUTER
}
/** Owner-reviewed API capability; binding changes invalidate it. This is not execution proof. */
@Serializable data class EffortSupport(val endpoint:String,val model:String,val adapter:EffortAdapter,val values:Set<ReasoningEffort>)

fun OnlineProfile.supportedEfforts():Set<ReasoningEffort> {
    val support=effortSupport ?: return emptySet()
    if(protocol !in setOf(OnlineProtocol.OPENAI,OnlineProtocol.OPENAI_COMPATIBLE) ||
        support.endpoint!=endpoint || support.model!=model || ReasoningEffort.AUTO in support.values ||
        support.adapter==EffortAdapter.OPENROUTER && runCatching{URI(endpoint).host}.getOrNull()!="openrouter.ai")return emptySet()
    return support.values
}
fun OnlineProfile.validateEffort(effort:ReasoningEffort) {
    require(effort==ReasoningEffort.AUTO || effort in supportedEfforts()) { "Reasoning effort is unavailable for this exact API connection and model; use Auto or review its documented support" }
}
internal fun reasoningPayload(base:JsonObject,profile:OnlineProfile,effort:ReasoningEffort,maxOutput:Int):JsonObject {
    profile.validateEffort(effort)
    val adapter=profile.effortSupport?.adapter?.takeIf{profile.supportedEfforts().isNotEmpty()}
    return JsonObject(base.toMutableMap().apply {
        listOf("reasoning_effort","reasoning","think","thinking","enable_thinking","max_tokens","max_completion_tokens").forEach(::remove)
        put(if(profile.protocol==OnlineProtocol.OPENAI || adapter==EffortAdapter.OPENAI)"max_completion_tokens" else "max_tokens",JsonPrimitive(maxOutput))
        if(adapter!=null){remove("temperature");remove("top_p")}
        if(effort!=ReasoningEffort.AUTO) {
            if(adapter==EffortAdapter.OPENAI)put("reasoning_effort",JsonPrimitive(effort.wire))
            else put("reasoning",buildJsonObject{put("effort",effort.wire)})
        }
    })
}

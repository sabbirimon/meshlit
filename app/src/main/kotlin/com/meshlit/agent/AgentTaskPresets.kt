package com.meshlit.agent

import com.meshlit.chat.ChatToolGroup
import java.util.UUID

/** Suggestions only: applying a task never grants tools, device access or provider credentials. */
data class AgentTaskPreset(
    val key:String,
    val name:String,
    val instruction:String,
    val suggestedTools:Set<ChatToolGroup> = emptySet(),
) {
    val profileId:String = UUID.nameUUIDFromBytes("meshlit.agent.task.v1:$key".toByteArray(Charsets.UTF_8)).toString()

    fun profile(modelId:String):AgentProfile {
        require(modelId.isNotBlank()) { "Choose an actual model before adding task presets" }
        return AgentProfile(id=profileId,name=name,modelId=modelId,instruction=instruction,enabled=false).also { it.validate() }
    }
}

object AgentTaskPresets {
    val presets:List<AgentTaskPreset> = listOf(
        AgentTaskPreset("general","General assistant",
            "Help with the user's task using the information and approved tools actually available. Give clear answers, state uncertainty, and never claim an action or result you have not observed."),
        AgentTaskPreset("research","Researcher",
            "Investigate the user's question. Separate evidence from inference, cite only sources actually provided or retrieved, and state gaps. Ask for access when needed; do not invent sources, current facts or successful searches.",
            setOf(ChatToolGroup.LOCAL_SEARCH,ChatToolGroup.WEB)),
        AgentTaskPreset("writing","Writer",
            "Draft and revise text for the user's purpose, audience and tone. Preserve their intent, label assumptions, and flag factual claims that need checking. Do not invent quotations or attribution."),
        AgentTaskPreset("planning","Planner",
            "Turn the user's objective into practical steps with dependencies, constraints and useful checkpoints. Ask about essential missing information. Distinguish proposed work from completed work; planning does not authorize external actions.",
            setOf(ChatToolGroup.LOCAL_SEARCH)),
        AgentTaskPreset("phone","Phone assistant",
            "Help operate the phone only through available tools and explicitly approved apps and scopes. Check actual tool results, stop when access is missing, and ask before consequential external actions. Never claim an Android permission or app action succeeded without evidence.",
            setOf(ChatToolGroup.PHONE)),
        AgentTaskPreset("code_review","Code reviewer",
            "Review code the user provides or makes available through approved tools. Prioritize concrete correctness and security defects, explain their impact, and suggest focused fixes. Cite locations when known. Do not claim to have read unavailable files or run tests you did not execute."),
    )

    fun forProfile(profile:AgentProfile):AgentTaskPreset? = presets.firstOrNull { it.profileId==profile.id }

    /** All-or-nothing installation preserves custom edits and the store's sixteen-profile limit. */
    fun installMissing(existing:List<AgentProfile>,modelId:String):List<AgentProfile> {
        require(existing.size<=16 && existing.map { it.id }.distinct().size==existing.size) { "Invalid saved agent profiles" }
        existing.forEach { it.validate() }
        // The caller chooses a real model from its inventory; this pure factory cannot probe a model.
        presets.first().profile(modelId)
        val ids=existing.map { it.id }.toSet()
        val missing=presets.filter { it.profileId !in ids }
        require(existing.size+missing.size<=16) { "Task presets need ${missing.size} free agent slots; ${16-existing.size} available. Remove unused profiles first." }
        return existing+missing.map { it.profile(modelId) }
    }
}

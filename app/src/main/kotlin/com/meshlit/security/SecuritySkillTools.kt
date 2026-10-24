package com.meshlit.security

import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.core.common.control.OperationGate
import com.meshlit.core.mcp.*
import com.meshlit.workspace.SecuritySkills
import kotlinx.serialization.json.*

/** Read-only standard MCP references; choosing a skill never changes an execution grant. */
class SecuritySkillTools(private val gate:OperationGate) {
    fun specs()=listOf(
        McpToolSpec("cyber_skills_list","List built-in defensive security references. Does not run an assessment or change permissions.",objectSchema(emptyMap())) {
            gate.run(ManagedFeature.CYBER,true) { McpToolResult.Json(buildJsonObject {
                put("execution",false);put("skills",buildJsonArray {SecuritySkills.all.forEach{skill->add(buildJsonObject{
                    put("id",skill.id);put("title",skill.title);put("purpose",skill.purpose)
                })}})
            }) }
        },
        McpToolSpec("cyber_skill_read","Read one defensive reference for an authorized review. No scripts or network requests are executed.",
            objectSchema(mapOf("id" to stringProp("Built-in skill ID",SecuritySkills.all.map{it.id})),listOf("id"))) {args->
            gate.run(ManagedFeature.CYBER,true) {
                val id=(args as? JsonObject)?.get("id")?.jsonPrimitive?.content.orEmpty()
                val skill=SecuritySkills.find(id)
                if(skill==null) McpToolResult.Error(McpToolResult.ErrorCode.INVALID_ARGS,"Unknown built-in security skill")
                else McpToolResult.Json(buildJsonObject {put("id",id);put("reference",skill.reference());put("execution",false);put("reviewed_source",SecuritySkills.reviewedSource)})
            }
        }
    )
}

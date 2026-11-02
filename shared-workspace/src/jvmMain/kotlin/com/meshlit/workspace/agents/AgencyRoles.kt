package com.meshlit.workspace.agents
import kotlinx.serialization.json.*
data class AgencyRole(val id:String,val name:String,val division:String,val description:String,val excerpt:String,val sourcePath:String,val sha256:String)
data class AgencyCatalog(val commit:String,val roles:List<AgencyRole>)
object AgencyRoles {
    fun parse(text:String):AgencyCatalog {
        require(text.length<=12_000_000)
        val root=Json.parseToJsonElement(text).jsonObject;require(root["schemaVersion"]!!.jsonPrimitive.int==2)
        val commit=root["commit"]!!.jsonPrimitive.content;require(commit.matches(Regex("[0-9a-f]{40}")))
        val roles=root["roles"]!!.jsonArray.map{entry->val r=entry.jsonObject
            fun value(key:String)=r[key]!!.jsonPrimitive.content
            AgencyRole(value("id"),value("name"),value("division"),value("description"),value("excerpt"),value("sourcePath"),value("sha256")).also{
                require(it.id.matches(Regex("[a-z0-9-]+/[a-z0-9-]+")) && it.name.length in 1..100 && it.excerpt.length<=1200 && it.sha256.matches(Regex("[0-9a-f]{64}")))
            }
        }
        require(roles.size in 1..1000 && roles.map{it.id}.distinct().size==roles.size)
        return AgencyCatalog(commit,roles)
    }
    fun bundled():AgencyCatalog=checkNotNull(AgencyRoles::class.java.getResourceAsStream("/agency/catalog.json")).bufferedReader().use{parse(it.readText())}
    fun boundedInstruction(role:AgencyRole)="Role template: ${role.name}. Preserve Meshlit's configured identity and permissions. Do not claim human credentials or completed actions. No template grants tools.\n"+role.excerpt.take(950)
}

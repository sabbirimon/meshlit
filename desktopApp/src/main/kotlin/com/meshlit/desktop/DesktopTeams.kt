package com.meshlit.desktop

import com.meshlit.core.mcp.control.*
import com.meshlit.workspace.agents.AgencyRoles
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Owner configuration enters through private IPC. Model tools can only use the saved delegated plan. */
internal class DesktopTeams(
    private val read:()->String?,private val save:(String)->Unit,
    private val execute:suspend(TeamMember,String,JsonObject?)->String,
):AutoCloseable {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val jobs=ConcurrentHashMap<String,Job>()
    private val pauses=ConcurrentHashMap<String,MutableStateFlow<Boolean>>()
    private val codec=Json{encodeDefaults=true}
    private var loaded=false
    private var runs=emptyList<JsonObject>()
    private var admissions=emptyList<JsonObject>()
    private var delegated:TeamPlan?=null
    private var delegatedRoutes=JsonObject(emptyMap())
    private fun load() {
        if(loaded)return
        val state=read()?.let{Json.parseToJsonElement(it).jsonObject}
        if(state!=null){
            require(state.getValue("version").jsonPrimitive.int==1)
            runs=state.getValue("runs").jsonArray.map{it.jsonObject}.also{require(it.size<=8)}.map{run->
                if(run.getValue("phase").jsonPrimitive.content in activePhases)JsonObject(run.toMutableMap().apply{
                    put("phase",JsonPrimitive("INTERRUPTED"));put("active",JsonArray(emptyList()))
                    put("error",JsonPrimitive("Process restarted. Inspect remote outcomes before starting another run."))
                }) else run
            }
            admissions=state["admissions"]?.jsonArray.orEmpty().map{it.jsonObject}.also{require(it.size<=128)}
            delegated=(state["delegated"] as? JsonObject)?.let{Json.decodeFromJsonElement(TeamPlan.serializer(),it).also(TeamPlan::validate)}
            delegatedRoutes=state["delegatedRoutes"] as? JsonObject ?: JsonObject(emptyMap())
        }
        commit(runs,admissions,delegated,delegatedRoutes);loaded=true
    }
    private fun commit(next:List<JsonObject>,receipts:List<JsonObject> = admissions,plan:TeamPlan?=delegated,routes:JsonObject=delegatedRoutes) {
        val text=buildJsonObject{
            put("version",1);put("runs",JsonArray(next));put("admissions",JsonArray(receipts))
            put("delegated",plan?.let{codec.encodeToJsonElement(TeamPlan.serializer(),it)} ?: JsonNull)
            put("delegatedRoutes",routes)
        }.toString()
        save(text);runs=next;admissions=receipts;delegated=plan;delegatedRoutes=routes
    }
    @Synchronized private fun update(id:String,change:(MutableMap<String,JsonElement>)->Unit) {
        commit(runs.map{run->if(run.getValue("id").jsonPrimitive.content==id)JsonObject(run.toMutableMap().apply(change)) else run})
    }
    @Synchronized fun status(agentOnly:Boolean=false):JsonObject {
        load();if(agentOnly)require(delegated!=null){"Team delegation is disabled"}
        return buildJsonObject {
            put("runs",JsonArray(runs.filter{!agentOnly || it["agentInitiated"]?.jsonPrimitive?.booleanOrNull==true}))
            put("delegationEnabled",delegated!=null)
            // Never export saved route credentials, endpoints or the encrypted document.
            put("delegatedPlan",delegated?.let{codec.encodeToJsonElement(TeamPlan.serializer(),it)} ?: JsonNull)
        }
    }
    @Synchronized fun delegate(plan:TeamPlan?,routes:JsonObject=JsonObject(emptyMap())) {
        load();plan?.validate();if(plan!=null)validateRoutes(plan,routes)
        commit(runs,plan=plan,routes=if(plan==null)JsonObject(emptyMap()) else routes)
        runs.filter{it["agentInitiated"]?.jsonPrimitive?.booleanOrNull==true}.forEach{jobs[it.getValue("id").jsonPrimitive.content]?.cancel()}
    }
    private fun validateRoutes(plan:TeamPlan,routes:JsonObject) {
        require(routes.size<=6)
        plan.members.filter{it.sourceId!="local"}.forEach{member->
            val route=routes.getValue(member.sourceId).jsonObject
            require(route.getValue("id").jsonPrimitive.content==member.sourceId && route.getValue("model").jsonPrimitive.content==member.modelId)
            require(route.getValue("kind").jsonPrimitive.content=="compatible")
            HostEndpoint.parse(route.getValue("endpoint").jsonPrimitive.content)
            require(route["token"]?.jsonPrimitive?.content.orEmpty().length<=8192)
        }
    }
    @Synchronized fun startDelegated(goal:String,requestId:String):String {
        load();require(requestId.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        val plan=checkNotNull(delegated){"No owner-approved team setup"}.copy(goal=goal)
        return start(plan,delegatedRoutes,true,requestId)
    }
    @Synchronized fun start(plan:TeamPlan,routes:JsonObject=JsonObject(emptyMap()),agent:Boolean=false,requestId:String?=null):String {
        load();plan.validate();validateRoutes(plan,routes)
        val fingerprint=MessageDigest.getInstance("SHA-256").digest(codec.encodeToJsonElement(TeamPlan.serializer(),plan).toString().toByteArray()).joinToString(""){"%02x".format(it)}
        if(agent){
            require(requestId!=null)
            admissions.firstOrNull{it.getValue("requestId").jsonPrimitive.content==requestId}?.let{
                require(it.getValue("fingerprint").jsonPrimitive.content==fingerprint){"Request ID already used with a different task"}
                return it.getValue("runId").jsonPrimitive.content
            }
            require(admissions.size<128){"Team admission history is full"}
        }
        require(jobs.size<2){"Two teams are already active"}
        val id=UUID.randomUUID().toString()
        if(agent)commit(runs,admissions+buildJsonObject{put("requestId",requestId);put("fingerprint",fingerprint);put("runId",id)})
        val record=buildJsonObject{
            put("id",id);put("plan",codec.encodeToJsonElement(TeamPlan.serializer(),plan));put("phase","QUEUED")
            put("agentInitiated",agent);put("outputs",JsonArray(emptyList()));put("active",JsonArray(emptyList()));put("messages",JsonArray(emptyList()))
        }
        commit(runs.takeLast(7)+record)
        val pause=MutableStateFlow(false);pauses[id]=pause
        val job=scope.launch(start=CoroutineStart.LAZY){
            try {
                update(id){it["phase"]=JsonPrimitive("RUNNING")}
                val report=AgentTeamCoordinator().runDetailed(plan,beforeCall={
                    pause.first{!it}
                    if(agent)synchronized(this@DesktopTeams){require(delegated?.copy(goal=plan.goal)==plan){"Team delegation changed"}}
                },onMessage={message->update(id){
                    it["messages"]=JsonArray((it["messages"] as? JsonArray).orEmpty()+codec.encodeToJsonElement(AgentDialogueMessage.serializer(),message))
                }},initiator=if(agent)"assistant" else "human"){member,prompt,index->
                    update(id){it["active"]=JsonArray(it.getValue("active").jsonArray+JsonPrimitive(member.id))}
                    try {
                        val result=execute(member,prompt,routes[member.sourceId] as? JsonObject)
                        update(id){it["outputs"]=JsonArray(it.getValue("outputs").jsonArray+codec.encodeToJsonElement(TeamMemberOutput.serializer(),TeamMemberOutput(member.id,member.sourceId,result,index)))}
                        result
                    }finally{withContext(NonCancellable){update(id){
                        val active=it.getValue("active").jsonArray.filter{entry->entry.jsonPrimitive.content!=member.id}
                        it["active"]=JsonArray(active)
                        if(pause.value && active.isEmpty())it["phase"]=JsonPrimitive("PAUSED")
                    }}}
                }
                update(id){it["phase"]=JsonPrimitive(if(report.outcome==TeamOutcome.COMPLETED)"SUCCEEDED" else "LIMIT_REACHED");it["outputs"]=JsonArray(report.outputs.map{out->codec.encodeToJsonElement(TeamMemberOutput.serializer(),out)});it["messages"]=JsonArray(report.messages.map{message->codec.encodeToJsonElement(AgentDialogueMessage.serializer(),message)})}
            }catch(_:TimeoutCancellationException){withContext(NonCancellable){update(id){it["phase"]=JsonPrimitive("LIMIT_REACHED");it["active"]=JsonArray(emptyList())}}}
            catch(_:CancellationException){withContext(NonCancellable){update(id){it["phase"]=JsonPrimitive("CANCELLED");it["active"]=JsonArray(emptyList())}}}
            catch(_:Exception){withContext(NonCancellable){update(id){it["phase"]=JsonPrimitive("FAILED");it["active"]=JsonArray(emptyList());it["error"]=JsonPrimitive("Check model readiness, Usage, manager JSON and connection permissions. Remote cancellation is best effort; inspect the receiving server.")}}}
            finally{jobs.remove(id);pauses.remove(id)}
        }
        jobs[id]=job;job.start();return id
    }
    @Synchronized fun control(id:String,action:String,agent:Boolean=false) {
        load();val run=runs.single{it.getValue("id").jsonPrimitive.content==id}
        if(agent)require(run["agentInitiated"]?.jsonPrimitive?.booleanOrNull==true)
        when(action){
            "stop"->jobs[id]?.cancel()
            "pause"->{pauses[id]?.value=true;update(id){if(jobs.containsKey(id))it["phase"]=JsonPrimitive(if(it.getValue("active").jsonArray.isEmpty())"PAUSED" else "PAUSING")}}
            "resume"->{if(agent)require(delegated!=null);update(id){if(it.getValue("phase").jsonPrimitive.content in setOf("PAUSED","PAUSING"))it["phase"]=JsonPrimitive("RUNNING")};pauses[id]?.value=false}
            else->error("Use pause, resume or stop")
        }
    }
    @Synchronized fun revokeSource(id:String){
        load()
        runs.filter{run->run.getValue("plan").jsonObject.getValue("members").jsonArray.any{it.jsonObject["sourceId"]?.jsonPrimitive?.content==id}}
            .forEach{jobs[it.getValue("id").jsonPrimitive.content]?.cancel()}
        if(delegated?.members?.any{it.sourceId==id}==true)delegate(null)
    }
    @Synchronized fun revokeProfile(id:String) {
        load()
        runs.filter{run->run.getValue("plan").jsonObject.getValue("members").jsonArray.any{(it.jsonObject["profileId"] as? JsonPrimitive)?.contentOrNull==id}}
            .forEach{jobs[it.getValue("id").jsonPrimitive.content]?.cancel()}
        if(delegated?.members?.any{it.profileId==id}==true)delegate(null)
    }
    override fun close(){scope.cancel()}
    companion object {
        private val activePhases=setOf("QUEUED","RUNNING","PAUSING","PAUSED")
        fun catalog()=buildJsonObject {
            val catalog=AgencyRoles.bundled();put("commit",catalog.commit)
            put("roles",buildJsonArray{catalog.roles.forEach{role->add(buildJsonObject{
                put("id",role.id);put("name",role.name);put("division",role.division);put("description",role.description)
                put("instruction",AgencyRoles.boundedInstruction(role));put("sourcePath",role.sourcePath)
            })}})
        }
    }
}

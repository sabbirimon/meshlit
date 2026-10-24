package com.meshlit.security

import android.content.Context
import com.meshlit.core.mcp.security.*
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.sandbox.RuntimeHost
import com.meshlit.core.sandbox.RuntimeMode
import com.meshlit.core.sandbox.VmState
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Human-created assessment grants; fixed read-only companion operations, no arbitrary shell template. */
class SecurityLab(context:Context,private val runtime:RuntimeHost) {
    private val operations = com.meshlit.operations.OperationsControl.get(context).gate
    private val store=EncryptedCredentialStore(context,"security-lab")
    private val json=Json {ignoreUnknownKeys=true}
    private val _assessments=MutableStateFlow(store.get("assessments")?.let {runCatching {json.decodeFromString<List<SecurityAssessment>>(it)}.getOrNull()}.orEmpty())
    val assessments=_assessments.asStateFlow()
    @Synchronized fun save(value:SecurityAssessment) {value.validate();require(value.environmentId==runtime.labIdentity());require(_assessments.value.size<50 || _assessments.value.any {it.id==value.id});val next=_assessments.value.filterNot {it.id==value.id}+value;store.putCommitted("assessments",json.encodeToString(next));_assessments.value=next}
    @Synchronized fun remove(id:String) {val next=_assessments.value.filterNot {it.id==id};store.putCommitted("assessments",json.encodeToString(next));_assessments.value=next}
    suspend fun run(id:String,tool:String,agent:Boolean=false):JsonObject = operations.run(com.meshlit.core.common.control.ManagedFeature.CYBER,agent) { runManaged(id,tool,agent) }
    private suspend fun runManaged(id:String,tool:String,agent:Boolean=false):JsonObject {
        val assessment=_assessments.value.firstOrNull {it.id==id} ?: error("Assessment missing")
        assessment.authorize(tool,assessment.sshHostId,assessment.sha256,System.currentTimeMillis(),agent)
        require(CyberTools.all.first {it.id==tool}.implemented) {"Adapter is not implemented"}
        LabGate.requireReady(runtime.config().mode.name,runtime.vm.state.name,agent,runtime.allowAgentVm())
        require(assessment.environmentId==runtime.labIdentity()) {"VM session changed; create a new grant"}
        require(assessment.sshHostId=="vm-ssh") {"Assessment must belong to the active VM"}
        val command=listOf("timeout","-k","5","50","/usr/local/bin/meshlit-cyber","run","--tool",tool,"--input",assessment.target,"--sha256",assessment.sha256)
        require(!agent || runtime.allowAgentVm()) {"Agent VM access disabled"}
        val result=runtime.executeGuest(command)
        require(!result.truncated && !result.timedOut && result.exitCode==0) {"Companion unavailable or analysis failed"}
        val value=json.parseToJsonElement(result.stdout).jsonObject
        require(value["input_sha256"]?.jsonPrimitive?.content.equals(assessment.sha256,true) && value["tool"]?.jsonPrimitive?.content==tool && value["status"]?.jsonPrimitive?.content=="completed") {"Companion evidence does not match"}
        // Verify that the grant still exists unchanged after the host operation.
        require(_assessments.value.firstOrNull {it.id==id}==assessment && assessment.environmentId==runtime.labIdentity() && System.currentTimeMillis()<assessment.expiresAtMs) {"Assessment revoked or expired during execution"}
        store.putCommitted("report-$id",value.toString());return value
    }
    fun report(id:String)=store.get("report-$id")
}

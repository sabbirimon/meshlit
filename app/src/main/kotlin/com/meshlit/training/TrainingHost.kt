package com.meshlit.training

import android.content.Context
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.ssh.SshConnections
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okio.ByteString.Companion.encodeUtf8
import java.util.UUID

@Serializable data class TrainingHostConfig(val connectionId:String="",val pythonPath:String="",val scriptPath:String="",val workspacePath:String="") {
    fun validate(){require(connectionId.matches(Regex("[a-zA-Z0-9-]{1,80}")));listOf(pythonPath,scriptPath,workspacePath).forEach{require(it.startsWith("/") && it.length in 2..1024 && it.none{c->c=='\u0000' || c=='\n' || c=='\r'}){"Enter absolute paths on the training host"}}}
}
@Serializable data class TrainingSpec(val jobId:String,val basePath:String,val datasetPath:String,val epochs:Int=1,val rank:Int=16,val quantization:String="none",val streamLayers:Boolean=false,val maxLength:Int=512,val timeoutSeconds:Int=14400) {
    fun validate(){require(runCatching{UUID.fromString(jobId).toString()==jobId}.getOrDefault(false));require(epochs in 1..5 && rank in 1..64 && maxLength in 128..4096 && timeoutSeconds in 60..86400);require(quantization in listOf("none","4bit"));listOf(basePath,datasetPath).forEach{require(it.startsWith("/") && it.length<=1024 && '\u0000' !in it)}}
}
@Serializable data class TrainingJobReference(val jobId:String,val host:TrainingHostConfig,val connectionSignature:String)
/** No arbitrary shell snippets, dependency installation or automatic retry of training starts. */
object TrainingCommand {
    fun quote(value:String):String {require('\u0000' !in value);return "'"+value.replace("'","'\"'\"'")+"'"}
    fun build(host:TrainingHostConfig,action:String,jobId:String?=null,spec:TrainingSpec?=null):String {
        host.validate();require(action in listOf("doctor","start","status","cancel"))
        val argv=mutableListOf(host.pythonPath,host.scriptPath,action,"--workspace",host.workspacePath)
        if(action=="start"){require(spec!=null);spec.validate();argv.addAll(listOf("--spec",Json.encodeToString(spec).encodeUtf8().base64()))}
        if(action=="status" || action=="cancel"){require(jobId!=null && runCatching{UUID.fromString(jobId).toString()==jobId}.getOrDefault(false));argv.addAll(listOf("--job",jobId))}
        return argv.joinToString(" "){quote(it)}.also{require(it.length<=8000){"Training command exceeds the SSH limit"}}
    }
}
class TrainingHost(private val context:Context,private val ssh:SshConnections) {
    private val store=EncryptedCredentialStore(context,"training-host")
    private val json=Json{ignoreUnknownKeys=true}
    private val lock=Mutex()
    private val _config=MutableStateFlow(runCatching{store.get("config")?.let{json.decodeFromString<TrainingHostConfig>(it).also{it.validate()}}}.getOrNull() ?: TrainingHostConfig())
    val config=_config.asStateFlow()
    private val _jobs=MutableStateFlow(runCatching{store.get("jobs")?.let{json.decodeFromString<List<TrainingJobReference>>(it)}}.getOrNull().orEmpty())
    val jobs=_jobs.asStateFlow()
    @Synchronized fun save(value:TrainingHostConfig){value.validate();signature(value.connectionId);store.put("config",json.encodeToString(value));_config.value=value}
    @Synchronized private fun remember(ref:TrainingJobReference){val next=_jobs.value.filterNot{it.jobId==ref.jobId}.takeLast(19)+ref;store.put("jobs",json.encodeToString(next));_jobs.value=next}
    private fun signature(id:String):String {val c=ssh.connections.value.firstOrNull{it.id==id} ?: error("Select an existing pinned SSH host");return "${c.host}\n${c.port}\n${c.username}\n${c.hostKeySha256}".encodeUtf8().sha256().hex()}
    private suspend fun request(host:TrainingHostConfig,action:String,job:String?=null,spec:TrainingSpec?=null):JsonObject {
        val reply=ssh.execute(host.connectionId,TrainingCommand.build(host,action,job,spec))
        require(!reply.truncated && reply.stdout.length<=60000){"Training reply was truncated; inspect the host and saved job ID"}
        val data=runCatching{json.parseToJsonElement(reply.stdout.trim()).jsonObject}.getOrNull() ?: error("Companion did not return a valid reply; inspect the host before retrying")
        require(reply.exitCode==0 && data["ok"]?.jsonPrimitive?.booleanOrNull==true){data["error"]?.jsonPrimitive?.contentOrNull ?: "Training companion failed"}
        return data["result"]?.jsonObject ?: error("Training companion omitted its result")
    }
    suspend fun doctor()=lock.withLock{val host=_config.value;host.validate();request(host,"doctor")}
    suspend fun start(spec:TrainingSpec)=lock.withLock {
        com.meshlit.operations.OperationsControl.get(context).gate.requireAllowed(com.meshlit.core.common.control.ManagedFeature.TRAINING)
        spec.validate();val host=_config.value;host.validate()
        val ref=TrainingJobReference(spec.jobId,host,signature(host.connectionId))
        // Keep the ID before sending, so a lost reply does not require a duplicate job.
        remember(ref);request(host,"start",spec=spec)
    }
    suspend fun status(ref:TrainingJobReference,cancel:Boolean=false)=lock.withLock {
        require(signature(ref.host.connectionId)==ref.connectionSignature){"SSH host identity changed; restore the original profile to manage this job"}
        request(ref.host,if(cancel) "cancel" else "status",ref.jobId)
    }
}

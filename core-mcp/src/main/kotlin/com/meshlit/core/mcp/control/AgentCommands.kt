package com.meshlit.core.mcp.control

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

@Serializable enum class AgentOperation {
    SETTINGS_READ, SETTINGS_PATCH, MODELS_LIST, MODEL_DOWNLOAD, MODEL_ADD_URL, MODEL_IMPORT,
    MODEL_IMPORT_SOURCES, MODEL_OPTIONS_SET, DEVICE_RUNTIME_STATUS, MODEL_LOAD, MODEL_GENERATE, MODEL_UNLOAD, MODEL_DELETE, CLUSTER_STATUS, CLUSTER_PLAN,
    CLUSTER_START, CLUSTER_WORKER_START, CLUSTER_STOP, RECOVERY_STATUS, CHECKPOINT_LIST, CHECKPOINT_SAVE, CHECKPOINT_RESTORE, CHECKPOINT_DELETE, MODEL_STARTUP_SET,
    TASK_LIST, TASK_CREATE, TASK_UPDATE, TASK_BATCH_UPDATE, TASK_DELETE, WORKSPACE_LIST, WORKSPACE_READ, WORKSPACE_WRITE, CLOUD_PROFILES, ENVIRONMENT_PROFILES, CLOUD_EXECUTE, BROWSER_STATUS, BROWSER_AUTONOMOUS_RUN, BROWSER_STOP
}
@Serializable data class AppearancePatch(val themeMode:String?=null,val accentHue:String?=null,
    val dynamicColors:Boolean?=null,val animationsEnabled:Boolean?=null,val fontScale:Float?=null,val uiFont:String?=null,val surfaceStyle:String?=null)
@Serializable data class AgentCommand(val requestId:String,val operation:AgentOperation,val modelId:String?=null,
    val url:String?=null,val name:String?=null,val importUri:String?=null,val appearance:AppearancePatch?=null,
    val prompt:String?=null,val maxTokens:Int=256,val temperature:Float=0.7f,
    val startupEnabled:Boolean?=null,val task:TaskMutation?=null,val fileName:String?=null,val fileText:String?=null,val expectedSha256:String?=null,val downloadBackend:String="VERIFIED_HTTP",val contextSize:Int=2048,val runtimeBackend:String="RUNANYWHERE",val keyCacheType:String="f16",val checkpointId:String?=null,val cloudProfileId:String?=null,val cloudAction:String?=null,val cloudPage:Int=1,val browserMaxSteps:Int=10) {
    fun validate(){
        require(requestId.matches(Regex("[A-Za-z0-9_-]{1,80}"))){"requestId must be a stable 1–80 character identifier"}
        require(downloadBackend in setOf("RUNANYWHERE","VERIFIED_HTTP")) {"Unknown model download backend"}
        require(contextSize in 256..8192 && runtimeBackend in setOf("RUNANYWHERE","NATIVE_LOCAL") && keyCacheType in setOf("f16","q8_0","q4_0"))
        require(name==null || name.length<=256)
        when(operation){
            AgentOperation.BROWSER_AUTONOMOUS_RUN->require(!prompt.isNullOrBlank() && prompt.length<=4000 && browserMaxSteps in 1..20){"Browser task and 1–20 steps required"}
            AgentOperation.CLOUD_EXECUTE->require(cloudProfileId?.matches(Regex("[A-Za-z0-9_-]{1,80}"))==true && cloudAction?.matches(Regex("[a-z][a-z0-9_-]{0,39}"))==true && cloudPage in 1..100){"cloudProfileId, cloudAction and bounded cloudPage required"}
            AgentOperation.CHECKPOINT_RESTORE,AgentOperation.CHECKPOINT_DELETE->require(checkpointId?.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))==true){"checkpointId required"}
            AgentOperation.MODEL_OPTIONS_SET,AgentOperation.MODEL_DOWNLOAD,AgentOperation.MODEL_LOAD,AgentOperation.MODEL_DELETE,AgentOperation.CLUSTER_PLAN,AgentOperation.CLUSTER_START ->require(!modelId.isNullOrBlank() && modelId.length<=160){"modelId required"}
            AgentOperation.MODEL_GENERATE ->{require(!modelId.isNullOrBlank() && !prompt.isNullOrBlank() && prompt.length<=32000);require(maxTokens in 1..2048 && temperature.isFinite() && temperature in 0f..2f)}
            AgentOperation.MODEL_ADD_URL ->require(!url.isNullOrBlank() && url.length<=4096){"url required"}
            AgentOperation.MODEL_IMPORT ->require(!importUri.isNullOrBlank() && importUri.length<=4096){"importUri required"}
            AgentOperation.TASK_CREATE,AgentOperation.TASK_UPDATE,AgentOperation.TASK_BATCH_UPDATE,AgentOperation.TASK_DELETE ->require(task!=null)
            AgentOperation.WORKSPACE_READ ->require(!fileName.isNullOrBlank())
            AgentOperation.WORKSPACE_WRITE ->require(!fileName.isNullOrBlank() && fileText!=null && fileText.length<=32000)
            AgentOperation.MODEL_STARTUP_SET ->require(startupEnabled!=null || !modelId.isNullOrBlank())
            AgentOperation.SETTINGS_PATCH ->require(appearance!=null){"appearance patch required"}
            else ->Unit
        }
    }
}
@Serializable enum class AgentJobPhase { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED, INTERRUPTED }
@Serializable data class AgentJob(val command:AgentCommand,val phase:AgentJobPhase=AgentJobPhase.QUEUED,
    val createdAtMs:Long=System.currentTimeMillis(),val updatedAtMs:Long=createdAtMs,
    val result:JsonElement?=null,val errorCode:String?=null,val error:String?=null) {
    val terminal get()=phase !in setOf(AgentJobPhase.QUEUED,AgentJobPhase.RUNNING)
}
interface AgentJobStore { suspend fun load():List<AgentJob>;suspend fun save(jobs:List<AgentJob>) }
class AgentCommandFailure(val code:String,message:String):Exception(message)

/** Durable admission before execution; stable IDs prevent accidental duplicate mutations.
 * Restart never silently repeats an admitted action with an uncertain outcome. */
class AgentCommandController(private val store:AgentJobStore,private val scope:CoroutineScope,
    private val execute:suspend(AgentCommand)->JsonElement) {
    private val mutex=Mutex()
    private val workers=Semaphore(2)
    private val running=ConcurrentHashMap<String,Job>()
    private val state=MutableStateFlow<List<AgentJob>>(emptyList())
    val jobs=state.asStateFlow()
    val ready=scope.async {
        mutex.withLock {
            state.value=store.load().takeLast(100).map{if(it.terminal) it else it.copy(phase=AgentJobPhase.INTERRUPTED,
                updatedAtMs=System.currentTimeMillis(),errorCode="process_interrupted",error="Outcome uncertain; inspect live state before explicit retry")}
            store.save(state.value)
        }
    }
    suspend fun submit(command:AgentCommand):AgentJob {
        command.validate();ready.await()
        return mutex.withLock {
            state.value.firstOrNull{it.command.requestId==command.requestId}?.let{
                require(it.command==command){"requestId was already used with different parameters"};return@withLock it
            }
            require(state.value.count{!it.terminal}<8){"Agent job queue is full"}
            val record=AgentJob(command)
            val retained=state.value.filter{!it.terminal}+state.value.filter{it.terminal}.takeLast(91)+record
            // Persist before changing observable state or launching work.
            store.save(retained);state.value=retained
            val job=scope.launch(start=CoroutineStart.LAZY){
                try{workers.withPermit{
                    update(command.requestId){it.copy(phase=AgentJobPhase.RUNNING)}
                    val result=withTimeout(if(command.operation in setOf(AgentOperation.MODEL_DOWNLOAD,AgentOperation.MODEL_ADD_URL,AgentOperation.MODEL_IMPORT)) 7_200_000 else 180_000){execute(command)}
                    update(command.requestId){it.copy(phase=AgentJobPhase.SUCCEEDED,result=result)}
                }}catch(timeout:TimeoutCancellationException){withContext(NonCancellable){update(command.requestId){it.copy(phase=AgentJobPhase.FAILED,errorCode="timeout",error="Command deadline exceeded")}}}
                catch(cancelled:CancellationException){withContext(NonCancellable){update(command.requestId){it.copy(phase=AgentJobPhase.CANCELLED,errorCode="cancelled")}};throw cancelled}
                catch(invalid:IllegalArgumentException){update(command.requestId){it.copy(phase=AgentJobPhase.FAILED,errorCode="invalid_args",error="Invalid command parameters")}}
                catch(failed:AgentCommandFailure){update(command.requestId){it.copy(phase=AgentJobPhase.FAILED,errorCode=failed.code,error=failed.message)}}
                catch(_:Exception){update(command.requestId){it.copy(phase=AgentJobPhase.FAILED,errorCode="execution_failed",error="Command failed; inspect live state and diagnostics")}}
                finally{running.remove(command.requestId)}
            }
            running[command.requestId]=job;job.invokeOnCompletion{running.remove(command.requestId,job)};job.start();record
        }
    }
    suspend fun cancel(requestId:String):AgentJob? {
        ready.await();running[requestId]?.cancelAndJoin()
        if(jobs.value.any{it.command.requestId==requestId && !it.terminal}) update(requestId){it.copy(phase=AgentJobPhase.CANCELLED,errorCode="cancelled")}
        return jobs.value.firstOrNull{it.command.requestId==requestId}
    }
    suspend fun retry(sourceId:String,newId:String):AgentJob {
        ready.await()
        val source=jobs.value.firstOrNull{it.command.requestId==sourceId} ?: error("Source job not found")
        require(source.phase in setOf(AgentJobPhase.INTERRUPTED,AgentJobPhase.FAILED,AgentJobPhase.CANCELLED)){"Only failed/interrupted/cancelled jobs can be retried"}
        require(newId!=sourceId){"Retry requires a new requestId"}
        return submit(source.command.copy(requestId=newId))
    }
    private suspend fun update(id:String,edit:(AgentJob)->AgentJob)=mutex.withLock {
        val next=state.value.map{if(it.command.requestId==id) edit(it).copy(updatedAtMs=System.currentTimeMillis()) else it}
        store.save(next);state.value=next
    }
}

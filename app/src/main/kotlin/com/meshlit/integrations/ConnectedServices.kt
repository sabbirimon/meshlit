package com.meshlit.integrations

import android.content.Context
import com.meshlit.core.mcp.integrations.*
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.workspace.context.ContextEvidence
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class ServiceConnection(val kind:String,val address:String,val namespace:String="viking://resources/meshlit",
    val enabled:Boolean=false,val localRecall:Boolean=false,val remoteRecall:Boolean=false,val agents:Boolean=false)
@Serializable private data class ServiceRecord(val configuration:ServiceConnection,val secret:String)
@Serializable data class ArtemisJob(val id:String,val goal:String,val serial:String,val appPackage:String,val phase:String,val observedStatus:String="",val updatedAtMs:Long=System.currentTimeMillis())
@Serializable private data class ArtemisSavedJob(val job:ArtemisJob,val host:ServiceRecord)
/** No automatic installations, capture hooks, sync, credentials or tool delegation. */
class ConnectedServices private constructor(context:Context) {
    private val app=context.applicationContext
    private val vault=EncryptedCredentialStore(app,"connected-context-services")
    private val watchers=java.util.concurrent.ConcurrentHashMap<String,Job>()
    private val jobMutex=Mutex()
    private val gate=com.meshlit.operations.OperationsControl.get(app).gate
    private fun stored(kind:String):ServiceRecord? {require(kind in setOf("openviking","artemis"));return vault.get(kind)?.let{Json.decodeFromString<ServiceRecord>(it)}}
    @Synchronized fun connection(kind:String)=stored(kind)?.configuration
    @Synchronized fun save(configuration:ServiceConnection,newSecret:String?=null) {
        require(configuration.kind in setOf("openviking","artemis"))
        val old=stored(configuration.kind)
        val secret=newSecret ?: old?.takeIf{it.configuration.address==configuration.address}?.secret.orEmpty()
        CompanionHttp(configuration.address,secret,configuration.kind=="openviking")
        if(configuration.kind=="openviking")OpenVikingClient.validateUri(configuration.namespace)
        vault.putCommitted(configuration.kind,Json.encodeToString(ServiceRecord(configuration,secret)))
    }
    @Synchronized fun remove(kind:String){vault.updateCommitted(mapOf(kind to null))}
    private fun checked(kind:String):ServiceRecord=checkNotNull(stored(kind)){"Configure the service first"}.also{require(it.configuration.enabled){"Service is disabled"}}
    suspend fun find(query:String,remote:Boolean=false,agent:Boolean=false,manual:Boolean=false):List<ContextEvidence> {
        val record=withContext(Dispatchers.IO){stored("openviking")} ?: return emptyList()
        val config=record.configuration
        if(!config.enabled || (!manual && !(if(remote)config.remoteRecall else config.localRecall)) || (agent && !config.agents))return emptyList()
        val hits=gate.run(ManagedFeature.GATEWAY,agent){OpenVikingClient(CompanionHttp(config.address,record.secret,true),config.namespace).find(query.take(4000))}
        require(withContext(Dispatchers.IO){stored("openviking")}==record){"Memory service configuration or grants changed"}
        return hits.map{ContextEvidence("OpenViking",it.uri,it.kind,it.layer,it.excerpt,System.currentTimeMillis())}
    }
    suspend fun read(uri:String,layer:Int):String {
        val record=withContext(Dispatchers.IO){checked("openviking")};val c=record.configuration
        val text=gate.run(ManagedFeature.GATEWAY){OpenVikingClient(CompanionHttp(c.address,record.secret,true),c.namespace).read(uri,layer)}
        require(withContext(Dispatchers.IO){stored("openviking")}==record){"Memory service configuration changed"};return text
    }
    suspend fun artemisStatus()=withArtemis{it.status()}
    suspend fun artemisDevices()=withArtemis{it.devices()}
    private fun savedJobs():List<ArtemisSavedJob> = vault.get("artemis-jobs")?.let{Json.decodeFromString<List<ArtemisSavedJob>>(it)}.orEmpty().also{require(it.size<=16)}
    suspend fun artemisJobs()=withContext(Dispatchers.IO){jobMutex.withLock{savedJobs().map{it.job}}}
    private fun commitJobs(jobs:List<ArtemisSavedJob>)=vault.putCommitted("artemis-jobs",Json.encodeToString(jobs))
    private suspend fun updateJob(id:String,change:(ArtemisJob)->ArtemisJob)=withContext(Dispatchers.IO){jobMutex.withLock{
        commitJobs(savedJobs().map{saved->
            if(saved.job.id!=id)saved else {
                val next=change(saved.job)
                val terminal=setOf("COMPLETED","FAILED","STOPPED")
                // A delayed status/admission reply cannot resurrect a confirmed terminal task.
                if(saved.job.phase in terminal && next.phase !in terminal)saved
                else saved.copy(job=next.copy(updatedAtMs=System.currentTimeMillis()))
            }
        })
    }}
    /** Owner-visible entry only. No agent tool until host-enforced app/action scope is qualified. */
    suspend fun artemisSubmit(id:String,goal:String,serial:String,appPackage:String,profile:String):ArtemisJob {
        require(java.util.UUID.fromString(id).toString()==id && goal.isNotBlank() && goal.length<=4000)
        require(serial.matches(Regex("[A-Za-z0-9._:-]{1,128}")) && appPackage.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) && profile in setOf("flash","pro"))
        val record=withContext(Dispatchers.IO){checked("artemis")}
        withContext(Dispatchers.IO){jobMutex.withLock{
            val old=savedJobs();require(old.none{it.job.id==id}){"This task ID already has an admission; inspect it instead of replaying"}
            require(old.size<16 && old.count{it.job.phase !in setOf("COMPLETED","FAILED","STOPPED")}<2){"Review existing automation tasks before starting another"}
            commitJobs(old+ArtemisSavedJob(ArtemisJob(id,goal,serial,appPackage,"REQUESTED"),record))
        }}
        try {
            require(withContext(Dispatchers.IO){stored("artemis")}==record){"Automation host changed"}
            val response=gate.run(ManagedFeature.AUTOMATION){ArtemisClient(CompanionHttp(record.configuration.address,record.secret)).submit(id,goal,serial,appPackage,profile)}
            updateJob(id){it.copy(phase="SUBMITTED",observedStatus=response.toString().take(4000))}
        }catch(error:Exception){
            withContext(NonCancellable){updateJob(id){it.copy(phase="OUTCOME_UNKNOWN",observedStatus="Submission did not finish reliably. Inspect this exact task ID; no automatic retry was made.")}}
            throw error
        }
        watchArtemis(id,record)
        return artemisJobs().single{it.id==id}
    }
    private fun watchArtemis(id:String,record:ServiceRecord) {
        val scope=(app as com.meshlit.MeshlitApplication).appScope
        val job=scope.launch(start=CoroutineStart.LAZY) {
            var finished=false
            try {
                gate.run(ManagedFeature.AUTOMATION){withTimeout(600_000){
                    while(isActive){
                        require(withContext(Dispatchers.IO){stored("artemis")}==record){"Automation connection changed"}
                        val state=artemisTask(id)
                        if(state.phase in setOf("COMPLETED","FAILED","STOPPED")){finished=true;break}
                        delay(3000)
                    }
                }}
            }catch(_:Exception){/* Cancellation/timeout/revocation cleanup remains bound to this admitted task. */}
            finally{
                if(!finished)withContext(NonCancellable){artemisStop(id)}
                watchers.remove(id)
            }
        };watchers[id]=job;job.start()
    }
    suspend fun artemisTask(id:String):ArtemisJob {
        val saved=withContext(Dispatchers.IO){jobMutex.withLock{savedJobs().single{it.job.id==id}}}
        val response=gate.run(ManagedFeature.AUTOMATION){ArtemisClient(CompanionHttp(saved.host.configuration.address,saved.host.secret)).task(id)}
        val status=(response as? JsonObject)?.get("status")?.jsonPrimitive?.content?.lowercase()
        val phase=when(status){"completed","success","succeeded"->"COMPLETED";"failed","error"->"FAILED";"stopped","cancelled","canceled"->"STOPPED";else->"OBSERVED"}
        updateJob(id){it.copy(phase=phase,observedStatus=response.toString().take(4000))}
        return artemisJobs().single{it.id==id}
    }
    suspend fun artemisStop(id:String):Boolean {
        val saved=withContext(Dispatchers.IO){jobMutex.withLock{savedJobs().single{it.job.id==id}}}
        // Cleanup remains available after settings revocation/emergency stop, bound to the admitted host/task.
        val confirmed=try{ArtemisClient(CompanionHttp(saved.host.configuration.address,saved.host.secret)).stop(id)}catch(_:Exception){false}
        withContext(NonCancellable){updateJob(id){it.copy(phase=if(confirmed)"STOPPED" else "STOP_UNCONFIRMED",observedStatus=if(confirmed)"Host acknowledged Stop" else "Remote stop is unconfirmed. Inspect the receiving Artemis host.")}}
        return confirmed
    }
    private suspend fun <T> withArtemis(work:suspend(ArtemisClient)->T):T {
        val record=withContext(Dispatchers.IO){checked("artemis")}
        val result=gate.run(ManagedFeature.AUTOMATION){work(ArtemisClient(CompanionHttp(record.configuration.address,record.secret)))}
        require(withContext(Dispatchers.IO){stored("artemis")}==record){"Automation host configuration changed; inspect remote outcome"}
        return result
    }
    companion object {
        @Volatile private var instance:ConnectedServices?=null
        fun get(context:Context)=instance ?: synchronized(this){instance ?: ConnectedServices(context).also{instance=it}}
    }
}

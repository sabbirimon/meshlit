package com.meshlit.control

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.meshlit.core.mcp.control.*
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.pipeline.PipelineHost
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.security.SecureRandom

/** Optional TLS control plane, independent of native worker membership. */
class WebBridgeHost(private val context:Context,private val backend:AgentBackend,private val tls:PipelineHost,private val scope:CoroutineScope){
    private val credentials=EncryptedCredentialStore(context,"web-enrollment")
    val directory=DeviceDirectory(object:DeviceDirectoryStore{
        override fun load()=credentials.get("directory")?.let{Json.decodeFromString<DeviceDirectorySnapshot>(it)} ?: DeviceDirectorySnapshot()
        override fun save(snapshot:DeviceDirectorySnapshot){credentials.put("directory",Json.encodeToString(snapshot))}
    }, deviceLimit={com.meshlit.operations.ClusterControls.get(context).state.value.deviceLimit})
    private var server:ControlBridgeServer?=null
    private var generation:String?=null
    private var invitation:String?=null;private var invitationUntil=0L
    private var enrollmentWindow=0L;private var enrollmentCount=0
    private val _active=MutableStateFlow(false);val active=_active.asStateFlow()
    val port=18792
    init{backend.remoteAuthorizer={command ->if(command.requestId.startsWith("web_")){
        if(!active.value) throw AgentCommandFailure("permission_denied","Remote interface is off")
        val device=directory.read().devices.firstOrNull{command.requestId.startsWith(prefix(it)) &&
            it.state==EnrollmentState.APPROVED && it.expiresAtMs>System.currentTimeMillis()}
            ?: throw AgentCommandFailure("permission_denied","Remote device approval was revoked")
        authorize(device,command.operation)
    }}}
    @Synchronized fun newInvitation():String{require(active.value);invitation=randomToken();invitationUntil=System.currentTimeMillis()+15*60_000;return invitation!!}
    fun fingerprint()=tls.fingerprint()
    fun ownAddress()=(context.applicationContext as com.meshlit.MeshlitApplication).localIpAddress
    suspend fun start()=withContext(Dispatchers.IO){synchronized(this@WebBridgeHost){
        com.meshlit.operations.OperationsControl.get(context).gate.requireAllowed(com.meshlit.core.common.control.ManagedFeature.AUTOMATION)
        if(server!=null) return@withContext
        val instance=ControlBridgeServer("0.0.0.0",port,context.assets.open("control/index.html").bufferedReader().use{it.readText()},::handle)
        instance.networkAllowed={remote->(context.applicationContext as com.meshlit.MeshlitApplication).meshlitFirewall.decide(remote,null,null,port).allowed}
        try{instance.startSecure(tls.controlTlsContext());server=instance;_active.value=true
            generation=java.util.UUID.randomUUID().toString()
            ContextCompat.startForegroundService(context,Intent(context,WebBridgeService::class.java).putExtra("generation",generation))
        }catch(e:Exception){instance.stop();server=null;generation=null;_active.value=false;throw e}
    }}
    @Synchronized fun stopIfGeneration(expected:String?){if(expected!=null && generation==expected) stop()}
    @Synchronized fun stop(){val stoppedAt=System.currentTimeMillis();generation=null;server?.stop();server=null;_active.value=false;invitation=null
        scope.launch{backend.controller.ready.await();backend.controller.jobs.value.filter{!it.terminal && it.createdAtMs<=stoppedAt && it.command.requestId.startsWith("web_")}.forEach{backend.controller.cancel(it.command.requestId)}}
        context.stopService(Intent(context,WebBridgeService::class.java))}
    suspend fun revoke(id:String)=withContext(Dispatchers.IO){
        val device=directory.read().devices.single{it.id==id};directory.revoke(id)
        backend.controller.jobs.value.filter{!it.terminal && it.command.requestId.startsWith(prefix(device))}.forEach{backend.controller.cancel(it.command.requestId)}
    }
    private fun prefix(device:EnrolledDevice)="web_${DeviceDirectory.hash(device.id).take(16)}_"
    private fun required(operation:AgentOperation)=when(operation){
        AgentOperation.BROWSER_STATUS,AgentOperation.BROWSER_AUTONOMOUS_RUN,AgentOperation.BROWSER_STOP->DeviceAccess.BROWSER
        AgentOperation.CLOUD_PROFILES,AgentOperation.ENVIRONMENT_PROFILES,AgentOperation.CLOUD_EXECUTE->DeviceAccess.CLOUD
        AgentOperation.RECOVERY_STATUS,AgentOperation.CHECKPOINT_LIST,AgentOperation.CHECKPOINT_SAVE,AgentOperation.CHECKPOINT_RESTORE,AgentOperation.CHECKPOINT_DELETE->DeviceAccess.RECOVERY
        AgentOperation.SETTINGS_PATCH->DeviceAccess.SETTINGS
        AgentOperation.MODEL_OPTIONS_SET,AgentOperation.MODEL_DOWNLOAD,AgentOperation.MODEL_ADD_URL,AgentOperation.MODEL_IMPORT,AgentOperation.MODEL_IMPORT_SOURCES,
        AgentOperation.MODEL_LOAD,AgentOperation.MODEL_GENERATE,AgentOperation.MODEL_UNLOAD,AgentOperation.MODEL_DELETE,AgentOperation.MODEL_STARTUP_SET->DeviceAccess.MODELS
        AgentOperation.CLUSTER_PLAN,AgentOperation.CLUSTER_START,AgentOperation.CLUSTER_WORKER_START,AgentOperation.CLUSTER_STOP->DeviceAccess.CLUSTER
        AgentOperation.TASK_LIST,AgentOperation.TASK_CREATE,AgentOperation.TASK_UPDATE,AgentOperation.TASK_BATCH_UPDATE,AgentOperation.TASK_DELETE->DeviceAccess.TASKS
        AgentOperation.WORKSPACE_LIST,AgentOperation.WORKSPACE_READ,AgentOperation.WORKSPACE_WRITE->DeviceAccess.WORKSPACE
        else->DeviceAccess.OBSERVE
    }
    private fun authorize(device:EnrolledDevice,operation:AgentOperation){
        if(required(operation) !in device.access) throw AgentCommandFailure("permission_denied","Device scope does not allow this command")
    }
    private suspend fun handle(method:String,path:String,token:String?,invite:String?,body:JsonObject):JsonElement=withContext(Dispatchers.IO){
        if(method=="POST" && path=="/api/v1/enroll"){
            synchronized(this@WebBridgeHost){
                val expected=invitation
                if(expected==null || System.currentTimeMillis()>invitationUntil || invite==null || !MessageDigest.isEqual(expected.toByteArray(),invite.toByteArray()))
                    throw BridgeHttpFailure(ResponseStatus.UNAUTHORIZED,"invitation_invalid_or_expired")
                if(System.currentTimeMillis()-enrollmentWindow>60_000){enrollmentWindow=System.currentTimeMillis();enrollmentCount=0}
                if(++enrollmentCount>10) throw BridgeHttpFailure(ResponseStatus.TOO_MANY_REQUESTS,"enrollment_rate_limited")
            }
            val device=directory.request(body["id"]!!.jsonPrimitive.content,body["name"]!!.jsonPrimitive.content,
                DeviceKind.valueOf(body["kind"]!!.jsonPrimitive.content),body["token"]!!.jsonPrimitive.content,
                (body["roles"] as? JsonArray)?.map{it.jsonPrimitive.content}?.toSet() ?: setOf("control"))
            return@withContext buildJsonObject{put("id",device.id);put("state",device.state.name);put("requiresOwnerApproval",true)
                put("expiresAtMs",device.expiresAtMs)}
        }
        val device=token?.let{directory.authenticate(it)} ?: throw BridgeHttpFailure(ResponseStatus.UNAUTHORIZED,"device_not_approved")
        directory.heartbeat(device.id)
        when{
            method=="GET" && path=="/api/v1/status"->buildJsonObject{put("deviceId",device.id);put("state",device.state.name)
                put("access",Json.encodeToJsonElement(device.access));put("nativeComputeMembership",false);put("taskReplication",false)
                put("expiresAtMs",device.expiresAtMs)
                put("commandApi","/api/v1/commands");put("jobApi","/api/v1/jobs")}
            method=="GET" && path=="/api/v1/jobs"->{backend.controller.ready.await();buildJsonObject{put("jobs",buildJsonArray{
                backend.controller.jobs.value.filter{it.command.requestId.startsWith(prefix(device))}.forEach{add(backend.publicJob(it))}})}}
            method=="POST" && path=="/api/v1/commands"->{
                val submitted=Json.decodeFromJsonElement<AgentCommand>(body)
                require(submitted.requestId.matches(Regex("[A-Za-z0-9_-]{1,50}")))
                authorize(device,submitted.operation)
                backend.publicJob(backend.controller.submit(submitted.copy(requestId=prefix(device)+submitted.requestId)))
            }
            method=="POST" && path=="/api/v1/cancel"->{
                val id=body["requestId"]!!.jsonPrimitive.content;require(id.startsWith(prefix(device)))
                backend.controller.cancel(id)?.let{backend.publicJob(it)} ?: throw BridgeHttpFailure(ResponseStatus.NOT_FOUND,"job_not_found")
            }
            else->throw BridgeHttpFailure(ResponseStatus.NOT_FOUND,"route_not_found")
        }
    }
    private fun randomToken()=ByteArray(32).also{SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it)}
}

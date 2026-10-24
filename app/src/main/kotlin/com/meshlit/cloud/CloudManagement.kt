package com.meshlit.cloud

import android.content.Context
import com.meshlit.core.cloudmcp.management.*
import com.meshlit.core.trust.EncryptedCredentialStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable private data class CloudSnapshot(val profiles:List<CloudProfile> = emptyList(),val environments:List<EnvironmentProfile> = emptyList(),val observations:List<CloudObservation> = emptyList(),val allowances:Map<String,CloudReadAllowance> = emptyMap())
data class CloudState(val profiles:List<CloudProfile> = emptyList(),val environments:List<EnvironmentDescription> = emptyList(),val observations:List<CloudObservation> = emptyList())

/** No secret values in state flows, agent descriptions, audit, exported configuration or cloud URLs. */
class CloudManagement(context:Context,scope:CoroutineScope) {
    private val operations=com.meshlit.operations.OperationsControl.get(context).gate
    private val store by lazy{EncryptedCredentialStore(context,"cloud-management-v1")}
    private val mutex=Mutex();private val json=Json{ignoreUnknownKeys=false};private var snapshot=CloudSnapshot()
    private val mutable=MutableStateFlow(CloudState());val state=mutable.asStateFlow()
    private val client=CloudApiClient()
    val ready=scope.async(Dispatchers.IO){mutex.withLock{
        snapshot=store.get("snapshot")?.let{json.decodeFromString<CloudSnapshot>(it)} ?: CloudSnapshot()
        snapshot.profiles.forEach{it.validate()};snapshot.environments.forEach{it.validate()};publish()
    }}
    private fun publish(){mutable.value=CloudState(snapshot.profiles,snapshot.environments.map{it.publicView()},snapshot.observations)}
    private fun commit(next:CloudSnapshot){store.putCommitted("snapshot",json.encodeToString(next));snapshot=next;publish()}
    suspend fun saveProfile(profile:CloudProfile){ready.await();withContext(Dispatchers.IO){mutex.withLock{
        profile.validate();require(snapshot.environments.any{it.id==profile.environmentId && it.purpose in setOf(EnvironmentPurpose.CLOUD,EnvironmentPurpose.API)}) { "Create and select a cloud/API credential environment first" }
        require(snapshot.profiles.size<32 || snapshot.profiles.any{it.id==profile.id})
        commit(snapshot.copy(profiles=snapshot.profiles.filterNot{it.id==profile.id}+profile,observations=snapshot.observations.filterNot{it.profileId==profile.id}))
    }}}
    suspend fun removeProfile(id:String){ready.await();withContext(Dispatchers.IO){mutex.withLock{commit(snapshot.copy(profiles=snapshot.profiles.filterNot{it.id==id},observations=snapshot.observations.filterNot{it.profileId==id},allowances=snapshot.allowances-id))}}}
    suspend fun saveEnvironment(description:EnvironmentDescription,key:String?=null,value:String?=null,removeVariable:String?=null){ready.await();withContext(Dispatchers.IO){mutex.withLock{
        require(snapshot.environments.size<32 || snapshot.environments.any{it.id==description.id})
        var variables=snapshot.environments.firstOrNull{it.id==description.id}?.variables.orEmpty()
        require((key==null)==(value==null));if(key!=null) variables=variables+(key to requireNotNull(value));if(removeVariable!=null) variables=variables-removeVariable
        val environment=EnvironmentProfile(description.id,description.name,variables,description.agentAllowed,description.expiresAtMs,description.purpose,if(description.purpose in setOf(EnvironmentPurpose.WEB_LOGIN,EnvironmentPurpose.API)) description.serviceBinding.trimEnd('/') else description.serviceBinding)
        environment.validate()
        require(snapshot.profiles.none{it.environmentId==environment.id} || environment.purpose in setOf(EnvironmentPurpose.CLOUD,EnvironmentPurpose.API)){ "Move cloud profiles before changing this environment's purpose" }
        commit(snapshot.copy(environments=snapshot.environments.filterNot{it.id==environment.id}+environment,observations=snapshot.observations.filterNot{observation->snapshot.profiles.any{it.id==observation.profileId && it.environmentId==environment.id}}))
    }}}
    suspend fun removeEnvironment(id:String){ready.await();withContext(Dispatchers.IO){mutex.withLock{
        require(snapshot.profiles.none{it.environmentId==id}) { "Remove or reconfigure cloud profiles referencing this environment first" }
        commit(snapshot.copy(environments=snapshot.environments.filterNot{it.id==id}))
    }}}
    suspend fun descriptions(actor:CloudActor):CloudState {ready.await();return mutex.withLock{
        if(actor==CloudActor.HUMAN) mutable.value else CloudState(snapshot.profiles.filter{it.agentEnabled},snapshot.environments.filter{it.agentAllowed}.map{it.publicView()},emptyList())
    }}
    /** Internal service consumer. No public tool returns this map. SSH/web credentials require exact service binding. */
    suspend fun serviceCredentials(id:String,purpose:EnvironmentPurpose,service:String,actor:CloudActor):Map<String,String>{ready.await();return mutex.withLock{
        val env=snapshot.environments.single{it.id==id};env.validate()
        require(env.purpose==purpose && env.serviceBinding==service) { "Credential purpose or service binding differs" }
        env.expiresAtMs?.let{require(it>System.currentTimeMillis()) { "Credential environment expired" }}
        require(actor!=CloudActor.AGENT || env.agentAllowed) { "Agent use of this credential environment is disabled" }
        env.variables.toMap()
    }}
    suspend fun execute(id:String,action:String,actor:CloudActor,page:Int=1):CloudObservation = operations.run(com.meshlit.core.common.control.ManagedFeature.CLOUD,actor==CloudActor.AGENT) {
        ready.await();val (profile,environment)=withContext(Dispatchers.IO){mutex.withLock{
            val p=snapshot.profiles.single{it.id==id};val env=snapshot.environments.single{it.id==p.environmentId};val now=System.currentTimeMillis()
            p.authorize(actor,action,env,now)
            val allowance=(snapshot.allowances[id] ?: CloudReadAllowance(now/86_400_000)).admit(p,actor,action,now)
            commit(snapshot.copy(allowances=snapshot.allowances+(id to allowance)))
            p to env
        }}
        val result=client.execute(profile,environment,actor,action,page)
        withContext(Dispatchers.IO){mutex.withLock{
            val latest=snapshot.profiles.single{it.id==id};val env=snapshot.environments.single{it.id==latest.environmentId}
            latest.authorize(actor,action,env,System.currentTimeMillis())
            require(latest==profile && env==environment) { "Cloud configuration changed during request; refresh explicitly" }
            commit(snapshot.copy(observations=(snapshot.observations.filterNot{it.profileId==id && it.action==action}+result).takeLast(32)));result
        }}
    }
}

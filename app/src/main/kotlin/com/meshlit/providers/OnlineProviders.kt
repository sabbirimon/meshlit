package com.meshlit.providers

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrl
import com.meshlit.core.inference.models.*
import com.meshlit.core.trust.EncryptedCredentialStore
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Profile configuration and keys use encrypted app storage. Keys never enter chat history or tool results. */
class OnlineProviders(context:Context,private val vault:com.meshlit.cloud.CloudManagement) {
    private val operations = com.meshlit.operations.OperationsControl.get(context).gate
    private val store=EncryptedCredentialStore(context,"online-provider-profiles")
    private val json=Json{ignoreUnknownKeys=true}
    private val _profiles=MutableStateFlow(runCatching{store.get("profiles")?.let{json.decodeFromString<List<OnlineProfile>>(it)}}.getOrNull().orEmpty())
    val profiles:StateFlow<List<OnlineProfile>> = _profiles.asStateFlow()
    val client=OnlineModelClient()
    fun token(id:String)=store.get("key-$id").orEmpty()
    suspend fun resolveToken(profile:OnlineProfile,agent:Boolean=false):String {
        require(!agent || profile.agentAllowed) { "Online provider is not delegated" }
        if(!profile.requiresApiKey) return ""
        val envId=profile.credentialEnvironmentId ?: return token(profile.id)
        val url=profile.endpoint.toHttpUrl()
        require(url.port==443) { "Vault API credentials require an HTTPS origin on port 443" }
        val values=vault.serviceCredentials(envId,com.meshlit.core.cloudmcp.management.EnvironmentPurpose.API,"https://${url.host}",if(agent) com.meshlit.core.cloudmcp.management.CloudActor.AGENT else com.meshlit.core.cloudmcp.management.CloudActor.HUMAN)
        return values[profile.credentialVariable] ?: error("Credential variable is missing from the bound environment")
    }
    @Synchronized fun save(profile:OnlineProfile,key:String?=null) {
        profile.validate();require(_profiles.value.size<20 || _profiles.value.any{it.id==profile.id}) { "At most 20 provider profiles" }
        key?.let{require(it.length<=4096 && it.none{c->c.isWhitespace()});if(it.isBlank()) store.remove("key-${profile.id}") else store.put("key-${profile.id}",it)}
        val updated=_profiles.value.filterNot{it.id==profile.id}+profile
        store.put("profiles",json.encodeToString(updated));_profiles.value=updated
    }
    @Synchronized fun remove(id:String) { val updated=_profiles.value.filterNot{it.id==id};store.put("profiles",json.encodeToString(updated));store.remove("key-$id");_profiles.value=updated }
    suspend fun generate(id:String,messages:List<OnlineMessage>,system:String="",maxTokens:Int=1024,temperature:Float=0.7f,agent:Boolean=false):Pair<OnlineProfile,OnlineReply> = operations.run(com.meshlit.core.common.control.ManagedFeature.CLOUD,agent) { generateManaged(id,messages,system,maxTokens,temperature,agent) }
    private suspend fun generateManaged(id:String,messages:List<OnlineMessage>,system:String="",maxTokens:Int=1024,temperature:Float=0.7f,agent:Boolean=false):Pair<OnlineProfile,OnlineReply> {
        val profile=_profiles.value.firstOrNull{it.id==id} ?: error("Online profile no longer exists")
        require(!agent || profile.agentAllowed) { "This profile does not allow agent cloud requests" }
        return profile to client.generate(profile,resolveToken(profile,agent),messages,system,maxTokens,temperature)
    }
}

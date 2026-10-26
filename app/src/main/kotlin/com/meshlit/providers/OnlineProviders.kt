package com.meshlit.providers

import android.content.Context
import com.meshlit.workspace.*
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import com.meshlit.core.inference.models.*
import com.meshlit.core.trust.EncryptedCredentialStore
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Profile configuration and keys use encrypted app storage. Keys never enter chat history or tool results. */
class OnlineProviders(context:Context,private val vault:com.meshlit.cloud.CloudManagement) {
    private val appContext = context.applicationContext
    private val operations = com.meshlit.operations.OperationsControl.get(context).gate
    private val store=EncryptedCredentialStore(context,"online-provider-profiles")
    private val json=Json{ignoreUnknownKeys=true}
    private val _profiles=MutableStateFlow(runCatching{store.get("profiles")?.let{json.decodeFromString<List<OnlineProfile>>(it)}}.getOrNull().orEmpty())
    val profiles:StateFlow<List<OnlineProfile>> = _profiles.asStateFlow()
    val client=OnlineModelClient()
    @Volatile private var storageFailed = false
    private fun commit(changes: Map<String, String?>) {
        check(!storageFailed) { "Provider storage failed; restart and review saved settings" }
        try { store.updateCommitted(changes) } catch(e: Exception) { storageFailed = true; throw e }
    }
    @Synchronized fun token(id:String): String { check(!storageFailed) { "Provider storage is unavailable" }; return store.get("key-$id").orEmpty() }
    @Synchronized fun tokenFor(profile: OnlineProfile): String {
        val saved = _profiles.value.firstOrNull { it.id == profile.id }
        return if(saved?.endpoint == profile.endpoint && saved.protocol == profile.protocol) token(profile.id) else ""
    }
    suspend fun resolveToken(profile:OnlineProfile,agent:Boolean=false):String {
        require(!agent || profile.agentAllowed) { "Online provider is not delegated" }
        if(!profile.requiresApiKey) return ""
        val envId=profile.credentialEnvironmentId ?: return tokenFor(profile)
        val url=profile.endpoint.toHttpUrl()
        require(url.port==443) { "Vault API credentials require an HTTPS origin on port 443" }
        val values=vault.serviceCredentials(envId,com.meshlit.core.cloudmcp.management.EnvironmentPurpose.API,"https://${url.host}",if(agent) com.meshlit.core.cloudmcp.management.CloudActor.AGENT else com.meshlit.core.cloudmcp.management.CloudActor.HUMAN)
        return values[profile.credentialVariable] ?: error("Credential variable is missing from the bound environment")
    }
    @Synchronized fun save(profile:OnlineProfile,key:String?=null) {
        profile.validate();require(_profiles.value.size<20 || _profiles.value.any{it.id==profile.id}) { "At most 20 provider profiles" }
        val previous = _profiles.value.firstOrNull { it.id == profile.id }
        val changes = mutableMapOf<String, String?>()
        if(previous?.endpoint != profile.endpoint || previous.protocol != profile.protocol) changes["key-${profile.id}"] = null
        key?.let { require(it.length <= 4096 && it.none(Char::isWhitespace)); changes["key-${profile.id}"] = it.takeIf(String::isNotBlank) }
        val updated = _profiles.value.filterNot { it.id == profile.id } + profile
        changes["profiles"] = json.encodeToString(updated)
        commit(changes)
        _profiles.value = updated
    }
    @Synchronized fun remove(id:String) {
        val updated = _profiles.value.filterNot { it.id == id }
        commit(mapOf("profiles" to json.encodeToString(updated), "key-$id" to null))
        _profiles.value = updated
    }
    @Synchronized private fun requireCurrent(profile: OnlineProfile, credential: String) {
        check(!storageFailed) { "Provider storage is unavailable" }
        require(_profiles.value.firstOrNull { it.id == profile.id } == profile) { "Provider changed or was revoked before dispatch" }
        if(profile.requiresApiKey && profile.credentialEnvironmentId == null) require(tokenFor(profile) == credential) { "Provider credential changed before dispatch" }
    }
    suspend fun generate(id:String,messages:List<OnlineMessage>,system:String="",maxTokens:Int=1024,temperature:Float=0.7f,agent:Boolean=false):Pair<OnlineProfile,OnlineReply> = operations.run(com.meshlit.core.common.control.ManagedFeature.CLOUD,agent) { generateManaged(id,messages,system,maxTokens,temperature,agent) }
    private suspend fun generateManaged(id:String,messages:List<OnlineMessage>,system:String="",maxTokens:Int=1024,temperature:Float=0.7f,agent:Boolean=false):Pair<OnlineProfile,OnlineReply> {
        val profile=_profiles.value.firstOrNull{it.id==id} ?: error("Online profile no longer exists")
        require(!agent || profile.agentAllowed) { "This profile does not allow agent cloud requests" }
        profile.validate()
        require(profile.enabled)
        val credential = resolveToken(profile, agent)
        client.validateGeneration(profile, credential, messages, system, maxTokens, temperature)
        val ledger = withContext(Dispatchers.IO) { UsageHistory.get(appContext) }
        val price = UsagePrice(profile.currency,
            profile.inputPricePerMillion?.let { UsageMoney.parse(java.math.BigDecimal.valueOf(it).setScale(9, java.math.RoundingMode.CEILING).stripTrailingZeros().toPlainString()) },
            profile.outputPricePerMillion?.let { UsageMoney.parse(java.math.BigDecimal.valueOf(it).setScale(9, java.math.RoundingMode.CEILING).stripTrailingZeros().toPlainString()) },
            profile.pricingSource ?: "User configured rates", profile.priceCheckedAtMs ?: 0, profile.gateway != OnlineGateway.DIRECT)
        var attempt: String? = null
        try {
            withContext(Dispatchers.IO) {
                attempt = ledger.begin(UsageLedger.route(profile.endpoint + "/" + profile.protocol, profile.model), profile.model, price,
                    (messages.sumOf { it.text.length.toLong() } + system.length) * 4 + 1024, maxTokens.toLong())
            }
            requireCurrent(profile, credential)
            currentCoroutineContext().ensureActive()
            val reply = client.generate(profile, credential, messages, system, maxTokens, temperature)
            withContext(NonCancellable + Dispatchers.IO) { ledger.finish(checkNotNull(attempt), "complete", reply.inputTokens, reply.outputTokens) }
            return profile to reply
        } catch (e: Exception) {
            withContext(NonCancellable + Dispatchers.IO) { attempt?.let { ledger.finish(it, if (e is CancellationException) "cancelled" else "failed") } }
            throw e
        }
    }
}

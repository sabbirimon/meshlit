package com.meshlit.core.mcp.gateway

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@Serializable enum class GatewayScope { MODELS, CHAT, MCP, A2A }
@Serializable data class GatewayClient(
    val id:String,val name:String,val keyHash:String,val expiresAtMs:Long,
    val scopes:Set<GatewayScope>,val modelIds:Set<String>,val toolNames:Set<String> = emptySet(),
    val maxOutputTokens:Int=512,val requestsPerMinute:Int=30,val revoked:Boolean=false,
) {
    fun validate() {
        require(id.matches(Regex("[a-f0-9-]{36}")) && name.isNotBlank() && name.length<=80)
        require(keyHash.matches(Regex("[a-f0-9]{64}")) && expiresAtMs>0)
        require(scopes.isNotEmpty() && modelIds.size<=32 && toolNames.size<=64)
        require(modelIds.all{it.isNotBlank() && it.length<=160} && toolNames.all{it.matches(Regex("[A-Za-z0-9_-]{1,160}"))})
        require(maxOutputTokens in 16..1024 && requestsPerMinute in 1..120)
        require(GatewayScope.CHAT !in scopes && GatewayScope.A2A !in scopes || modelIds.isNotEmpty())
    }
}
class IssuedGatewayKey(val client:GatewayClient,val key:String) {
    override fun toString()="IssuedGatewayKey(client=${client.id}, key=REDACTED)"
}
class GatewayPrincipal(val id:String,val scopes:Set<GatewayScope>,val modelIds:Set<String>,val toolNames:Set<String>,
    val maxOutputTokens:Int,val expiresAtMs:Long,val owner:Boolean=false):AbstractCoroutineContextElement(Key) {
    companion object Key:CoroutineContext.Key<GatewayPrincipal>
    fun allows(scope:GatewayScope)=owner || scope in scopes
    fun allowsModel(id:String)=owner || id in modelIds
    fun allowsTool(name:String)=owner || name in toolNames
}
suspend fun currentGatewayClient()=checkNotNull(currentCoroutineContext()[GatewayPrincipal]){"Client context is missing"}

/** High-entropy API keys, hashed verifier records and bounded per-key admission.
 * Opaque bearer keys are not JWTs or an OAuth authorization server. */
class GatewayClientAccess(private val records:()->List<GatewayClient>,private val clock:()->Long={System.currentTimeMillis()}) {
    private data class Window(var start:Long,var count:Int=0,var active:Int=0)
    private val windows=mutableMapOf<String,Window>()
    fun authenticate(key:String):GatewayPrincipal? {
        if(key.length !in 32..256) return null
        val digest=hash(key)
        val client=records().take(64).firstOrNull{!it.revoked && it.expiresAtMs>clock() && MessageDigest.isEqual(it.keyHash.toByteArray(),digest.toByteArray())} ?: return null
        return GatewayPrincipal(client.id,client.scopes,client.modelIds,client.toolNames,client.maxOutputTokens,client.expiresAtMs)
    }
    @Synchronized fun acquire(principal:GatewayPrincipal):Boolean {
        val now=clock()
        val current=records().firstOrNull{it.id==principal.id && !it.revoked && it.expiresAtMs>now} ?: return false
        windows.keys.retainAll(records().map{it.id}.toSet())
        val window=windows.getOrPut(principal.id){Window(now)}
        if(now<window.start || now-window.start>=60_000){window.start=now;window.count=0}
        if(window.count>=current.requestsPerMinute || window.active>=1) return false
        window.count++;window.active++;return true
    }
    @Synchronized fun release(principal:GatewayPrincipal){windows[principal.id]?.let{it.active=(it.active-1).coerceAtLeast(0)}}
    fun active(principal:GatewayPrincipal)=records().any{it.id==principal.id && !it.revoked && it.expiresAtMs>clock()}
    companion object {
        fun hash(key:String)=MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
        fun issue(name:String,modelIds:Set<String>,scopes:Set<GatewayScope> = setOf(GatewayScope.MODELS,GatewayScope.CHAT),
            toolNames:Set<String> = emptySet(),lifetimeMs:Long=3_600_000,maxOutputTokens:Int=512,requestsPerMinute:Int=30,now:Long=System.currentTimeMillis()):IssuedGatewayKey {
            require(lifetimeMs in 60_000..2_592_000_000 && now>0 && now<=Long.MAX_VALUE-lifetimeMs)
            val bytes=ByteArray(32).also{SecureRandom().nextBytes(it)}
            val key="mlc_"+bytes.joinToString(""){"%02x".format(it)}
            val client=GatewayClient(UUID.randomUUID().toString(),name.trim(),hash(key),now+lifetimeMs,scopes,modelIds,toolNames,maxOutputTokens,requestsPerMinute)
            client.validate();return IssuedGatewayKey(client,key)
        }
    }
}

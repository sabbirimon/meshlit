package com.meshlit.core.mcp.control

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable enum class ConnectionTransport { SSH_STATUS, PAIRED_WEB, MODEL_API, MCP, NEARBY_PEER }
@Serializable enum class ConnectionPhase { CONNECTING, RETRYING, VERIFIED, FAILED, TIMED_OUT, REVOKED, CANCELLED, INTERRUPTED, SETUP_REQUIRED }

/** binding is private configuration identity, never serialized into a tool response or prompt. */
data class DeviceConnectionSource(
    val id:String,val name:String,val transport:ConnectionTransport,val binding:String,
    val supported:Boolean,val setupReason:String="",val identityLabel:String="Saved identity",
)
@Serializable data class DeviceConnectionPolicy(val enabled:Boolean=false,val grants:Map<String,String> = emptyMap())
@Serializable data class DeviceConnectionChoice(
    val id:String,val name:String,val transport:ConnectionTransport,val canConnect:Boolean,
    val reason:String,val identityLabel:String,
)
@Serializable data class DeviceConnectionOffer(
    val id:String,val expiresAtMs:Long,val enabled:Boolean,val choices:List<DeviceConnectionChoice>,
    val setupOptions:List<String> = listOf("paired_web","ssh","model_api","mcp","nearby_pairing"),
)
@Serializable data class DeviceConnectionReceipt(
    val id:String,val owner:String,val sourceId:String,val name:String,val transport:ConnectionTransport,
    val phase:ConnectionPhase,val startedAtMs:Long,val updatedAtMs:Long,val attempts:Int=0,
    val detail:String="",val itemCount:Int?=null,
)
@Serializable data class DeviceConnectionSnapshot(
    val policy:DeviceConnectionPolicy=DeviceConnectionPolicy(),val receipts:List<DeviceConnectionReceipt> = emptyList(),
)
interface DeviceConnectionStore { fun load():DeviceConnectionSnapshot;fun save(value:DeviceConnectionSnapshot) }
data class DeviceConnectionProbe(val verified:Boolean,val detail:String,val retryable:Boolean=false,val itemCount:Int?=null)

/** Contact checks only. This grants no enrollment, model use, tool execution or native worker role.
 * Offers are ephemeral and bound to a trusted host-provided conversation/agent owner.
 * Each read-only adapter must honor cancellation and recheck its original identity and grants. */
class DeviceConnectionCoordinator(
    private val store:DeviceConnectionStore,
    private val catalog:()->List<DeviceConnectionSource>,
    private val checkOperation:()->Unit,
    private val probe:suspend(DeviceConnectionSource,()->Unit)->DeviceConnectionProbe,
    private val clock:()->Long=System::currentTimeMillis,
    private val attemptTimeoutMs:Long=10_000,
    private val retryDelayMs:Long=1_000,
) {
    private data class Offer(val owner:String,val response:DeviceConnectionOffer,val bindings:Map<String,String>,var selected:String?=null,var receiptId:String?=null)
    private class Revoked:CancellationException("Connection approval changed")
    private val offers=linkedMapOf<String,Offer>()
    private val active=mutableMapOf<String,Job>()
    private var failedClosed=false
    private var snapshot=store.load().also { value ->
        require(value.receipts.size<=32 && value.policy.grants.size<=128)
    }.let { value->value.copy(receipts=value.receipts.map {
        if(it.phase in setOf(ConnectionPhase.CONNECTING,ConnectionPhase.RETRYING))it.copy(phase=ConnectionPhase.INTERRUPTED,detail="Process restarted; no connection was retried",updatedAtMs=clock()) else it
    }) }
    private val mutable=MutableStateFlow(snapshot)
    val state=mutable.asStateFlow()
    init { require(attemptTimeoutMs in 1..30_000 && retryDelayMs in 0..5_000);store.save(snapshot) }
    @Synchronized private fun commit(next:DeviceConnectionSnapshot) {
        try { store.save(next);snapshot=next;mutable.value=next }
        catch(e:Exception){failedClosed=true;active.values.toList().forEach{it.cancel(Revoked())};throw e}
    }
    private fun sources():List<DeviceConnectionSource> = catalog().also { entries ->
        require(entries.size<=128 && entries.map{it.id}.distinct().size==entries.size)
        entries.forEach { require(it.id.length in 1..160 && it.name.length in 1..100 && it.name.none(Char::isISOControl) && it.binding.length in 1..128) }
    }
    /** Owner UI only. Never expose policy changes as a model/MCP operation. */
    @Synchronized fun setEnabled(enabled:Boolean) {
        check(!failedClosed){"Connection approval storage is unavailable"}
        if(!enabled) { snapshot=snapshot.copy(policy=snapshot.policy.copy(enabled=false));active.values.toList().forEach{it.cancel(Revoked())};offers.clear() }
        commit(snapshot.copy(policy=snapshot.policy.copy(enabled=enabled)))
    }
    /** A changed endpoint, host pin, executable or source action scope invalidates this grant. */
    @Synchronized fun setSourceAllowed(id:String,allowed:Boolean,expectedBinding:String?=null) {
        check(!failedClosed){"Connection approval storage is unavailable"}
        val source=sources().singleOrNull{it.id==id} ?: error("Saved source changed; refresh devices")
        require(!allowed || source.supported){source.setupReason.ifBlank{"Complete owner setup first"}}
        require(!allowed || expectedBinding==source.binding){"Saved identity changed; review the source again before approving"}
        val grants=snapshot.policy.grants.toMutableMap().apply { if(allowed)put(id,source.binding) else remove(id) }
        if(!allowed) {
            snapshot=snapshot.copy(policy=snapshot.policy.copy(grants=grants))
            snapshot.receipts.filter{it.sourceId==id}.forEach{active[it.id]?.cancel(Revoked())}
        }
        commit(snapshot.copy(policy=snapshot.policy.copy(grants=grants)))
    }
    @Synchronized fun ownerChoices():List<DeviceConnectionChoice> = sources().map(::choice)
    @Synchronized private fun choice(source:DeviceConnectionSource)=DeviceConnectionChoice(
        source.id,source.name,source.transport,
        source.supported && snapshot.policy.grants[source.id]==source.binding,
        if(!source.supported)source.setupReason else if(snapshot.policy.grants[source.id]!=source.binding)"Owner connection approval required" else "Approved contact check; other action scopes stay separate",
        source.identityLabel,
    )
    private fun validateOwner(owner:String){require(owner.length in 1..120 && owner.none(Char::isISOControl))}
    @Synchronized fun offer(owner:String,query:String=""):DeviceConnectionOffer {
        validateOwner(owner);check(!failedClosed);checkOperation();require(query.length<=160 && query.none(Char::isISOControl))
        val now=clock();offers.entries.removeAll{it.value.response.expiresAtMs<=now}
        while(offers.size>=16)offers.remove(offers.keys.first())
        val candidates=if(snapshot.policy.enabled)sources().filter { query.isBlank() || it.name.contains(query.trim(),true) || it.id==query.trim() } else emptyList()
        val answer=DeviceConnectionOffer(UUID.randomUUID().toString(),now+120_000,snapshot.policy.enabled,candidates.map(::choice))
        offers[answer.id]=Offer(owner,answer,candidates.associate{it.id to it.binding})
        return answer
    }
    @Synchronized private fun authorize(owner:String,source:DeviceConnectionSource) {
        validateOwner(owner);check(!failedClosed);checkOperation()
        if(!snapshot.policy.enabled || snapshot.policy.grants[source.id]!=source.binding || sources().none{it==source && it.supported})throw Revoked()
    }
    @Synchronized private fun update(id:String,phase:ConnectionPhase,attempts:Int,detail:String,itemCount:Int?=null):DeviceConnectionReceipt {
        val old=snapshot.receipts.single{it.id==id}
        val next=old.copy(phase=phase,attempts=attempts,updatedAtMs=clock(),detail=detail.take(300).filterNot(Char::isISOControl),itemCount=itemCount)
        commit(snapshot.copy(receipts=snapshot.receipts.map{if(it.id==id)next else it}));return next
    }
    @Synchronized fun receipts(owner:String):List<DeviceConnectionReceipt> {validateOwner(owner);return snapshot.receipts.filter{it.owner==owner}}
    @Synchronized fun cancel(owner:String,id:String) {
        validateOwner(owner);require(snapshot.receipts.any{it.id==id && it.owner==owner}){"Connection request is outside this conversation"}
        active[id]?.cancel(CancellationException("Stopped by owner"))
    }
    @Synchronized fun cancelOwner(owner:String) {
        offers.entries.removeAll{it.value.owner==owner}
        snapshot.receipts.filter{it.owner==owner}.forEach{active[it.id]?.cancel(CancellationException("Conversation closed or tools disabled"))}
    }
    @Synchronized fun close() {
        offers.clear();active.values.toList().forEach{it.cancel(CancellationException("Connection owner session closed"))}
    }
    suspend fun connect(owner:String,offerId:String,sourceId:String):DeviceConnectionReceipt {
        val parent=currentCoroutineContext().job
        val (source,id)=synchronized(this) {
            validateOwner(owner);check(!failedClosed);checkOperation()
            val offered=offers[offerId] ?: error("Device choices expired; request fresh choices")
            require(offered.owner==owner && offered.response.expiresAtMs>clock()){"Device choices expired or belong to another conversation"}
            val found=sources().singleOrNull{it.id==sourceId && offered.bindings[it.id]==it.binding} ?: error("Device identity or configuration changed; request fresh choices")
            require(found.supported && snapshot.policy.enabled && snapshot.policy.grants[found.id]==found.binding){"Owner connection approval and the saved transport scope are required"}
            authorize(owner,found)
            offered.receiptId?.let { existing ->
                require(offered.selected==sourceId){"This choice was already used; request fresh choices"}
                val result=snapshot.receipts.single{it.id==existing}
                require(result.phase !in setOf(ConnectionPhase.CONNECTING,ConnectionPhase.RETRYING)){"Connection check already running"}
                return result
            }
            require(active.size<2 && snapshot.receipts.none{it.owner==owner && it.id in active}){"Connection check capacity reached"}
            val requestId=UUID.randomUUID().toString();val now=clock()
            val receipt=DeviceConnectionReceipt(requestId,owner,found.id,found.name,found.transport,ConnectionPhase.CONNECTING,now,now)
            val kept=snapshot.receipts.filter{it.id in active}.toSet()
            val history=snapshot.receipts.filter{it !in kept}.takeLast(29)
            commit(snapshot.copy(receipts=(history+kept+receipt).sortedBy{it.startedAtMs}))
            offered.selected=sourceId;offered.receiptId=requestId;active[requestId]=parent
            found to requestId
        }
        var attempts=0
        try {
            return coroutineScope {
                val operation=this
                val monitor=launch {
                    while(isActive){delay(200);try{authorize(owner,source)}catch(_:Exception){operation.cancel(Revoked());break}}
                }
                try {
                    repeat(2) {
                        authorize(owner,source);attempts++
                        update(id,ConnectionPhase.CONNECTING,attempts,"Checking saved identity and approved transport")
                        val result=withTimeoutOrNull(attemptTimeoutMs){probe(source){authorize(owner,source)}}
                        authorize(owner,source)
                        if(result?.verified==true) {
                            require(result.itemCount==null || result.itemCount in 0..10000)
                            return@coroutineScope update(id,ConnectionPhase.VERIFIED,attempts,result.detail,result.itemCount)
                        }
                        if(attempts<2 && (result==null || result.retryable)) {
                            update(id,ConnectionPhase.RETRYING,attempts,"Contact check interrupted; retrying the same approved identity once")
                            delay(retryDelayMs)
                        } else return@coroutineScope update(id,if(result==null)ConnectionPhase.TIMED_OUT else ConnectionPhase.FAILED,attempts,result?.detail ?: "Contact check timed out; no device action was replayed")
                    }
                    error("Unreachable connection state")
                } finally { monitor.cancel() }
            }
        } catch(_:Revoked) {
            return update(id,ConnectionPhase.REVOKED,attempts,"Connection approval, identity or source scope changed")
        } catch(e:CancellationException) {
            update(id,ConnectionPhase.CANCELLED,attempts,"Contact check stopped; existing shared sessions and completed work were not reversed")
            throw e
        } catch(_:Exception) {
            return update(id,ConnectionPhase.FAILED,attempts,"Contact check failed; review the saved transport, identity and action scope")
        } finally { synchronized(this){active.remove(id)} }
    }
}

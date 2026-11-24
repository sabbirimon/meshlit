package com.meshlit.bots

import android.content.Context
import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.core.inference.models.*
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.operations.OperationsControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Saved routes are private to this installation. Secrets are bound to the exact route. */
internal class BotConnections(context:Context) {
    private val gate=OperationsControl.get(context).gate
    private val store=EncryptedCredentialStore(context,"bot-connections-v1")
    private val client=BotClient()
    private val initial=runCatching{store.get("profiles")?.let{Json.decodeFromString<List<BotConnection>>(it)}.orEmpty().also{profiles->
        require(profiles.size<=12 && profiles.map{it.id}.distinct().size==profiles.size)
        profiles.forEach{it.destination()}
    }}
    private val mutable=MutableStateFlow(initial.getOrDefault(emptyList()))
    val profiles=mutable.asStateFlow()

    @Synchronized fun save(profile:BotConnection,key:String?) {
        check(initial.isSuccess){"Cannot read saved bot connections"}
        val previous=mutable.value.firstOrNull{it.id==profile.id}
        val destination=profile.destination()
        val same=previous?.let{it.kind==profile.kind && it.destination()==destination && it.configId==profile.configId}==true
        val keyName="key-${profile.id}"
        val effective=key ?: (store.get(keyName).takeIf{same})
        require(effective==null || effective.length in 8..4096 && effective.all{it.code in 33..126}){"Use a valid bearer key"}
        require(!profile.enabled || effective!=null){"Enter a key for this exact connection before enabling it"}
        require(previous!=null || mutable.value.size<12){"At most 12 bot connections are supported"}
        val next=mutable.value.filterNot{it.id==profile.id}+profile
        store.updateCommitted(mapOf("profiles" to Json.encodeToString(next),keyName to effective))
        mutable.value=next
    }

    @Synchronized fun remove(id:String) {
        check(initial.isSuccess){"Cannot read saved bot connections"}
        val next=mutable.value.filterNot{it.id==id}
        store.updateCommitted(mapOf("profiles" to Json.encodeToString(next),"key-$id" to null));mutable.value=next
    }

    @Synchronized private fun currentKey(profile:BotConnection):String {
        require(profile.enabled && mutable.value.firstOrNull{it.id==profile.id}==profile){"Bot connection changed or was disabled"}
        return store.get("key-${profile.id}") ?: error("This bot connection needs a key")
    }

    suspend fun send(profile:BotConnection,input:String,sessionId:String):BotResult = gate.run(ManagedFeature.AUTOMATION,true) {
        gate.run(ManagedFeature.CLOUD,true) {
            check(!com.meshlit.BuildProfile.coreCandidate && !com.meshlit.BuildConfig.PLAY_REVIEW)
            val key=withContext(Dispatchers.IO){currentKey(profile)}
            val result=client.send(profile,key,input,sessionId) {
                gate.requireAllowed(ManagedFeature.AUTOMATION,true);gate.requireAllowed(ManagedFeature.CLOUD,true)
                require(currentKey(profile)==key){"Bot key changed before dispatch"}
            }
            withContext(Dispatchers.IO){require(currentKey(profile)==key){"Bot connection changed during the request"}}
            result
        }
    }
}

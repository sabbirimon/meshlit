package com.meshlit.ssh

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.core.ssh.*
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.sandbox.RuntimeHost
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.NetworkInterface
import java.security.*
import java.security.spec.*
import java.util.Base64

@Serializable private data class StoredSshKey(val publicPart:String,val privatePart:String)
data class NodeSshState(val phase:String="Stopped",val address:String?=null,val port:Int?=null,val fingerprint:String?=null,val error:String?=null)

/** Human-started lease; no boot restart, no key/permission changes through SSH or agents. */
@RequiresApi(26)
class NodeSshHost(private val context:Context,private val runtime:RuntimeHost,private val appScope:CoroutineScope) {
    private val gate=com.meshlit.operations.OperationsControl.get(context).gate
    private val store=EncryptedCredentialStore(context,"node-ssh")
    private val mutable=MutableStateFlow(NodeSshState());val state=mutable.asStateFlow()
    private val saved=MutableStateFlow(runCatching { store.get("grants")?.let { Json.decodeFromString<List<NodeSshGrant>>(it) } }.getOrNull().orEmpty())
    val grants=saved.asStateFlow()
    @Volatile private var enabled=false
    @Volatile private var lease:Job?=null
    @Volatile private var sessionId:String?=null
    private val server=NodeSshServer(::authorize,::execute)

    private fun authorize(grant:NodeSshGrant) {
        check(enabled && saved.value.any { it==grant }) { "SSH listener or key grant revoked" }
        gate.requireAllowed(ManagedFeature.SSH,grant.agent)
    }
    @Synchronized fun save(grant:NodeSshGrant) {
        check(lease==null) { "Stop SSH before editing keys" };gate.requireAllowed(ManagedFeature.SSH)
        grant.validate();NodeSshServer.publicKey(grant.publicKey)
        val next=saved.value.filterNot { it.id==grant.id }+grant
        require(next.size<=32)
        val keyIds=next.map { it.username to NodeSshServer.fingerprint(NodeSshServer.publicKey(it.publicKey)) }
        require(keyIds.distinct().size==keyIds.size) { "This username/key is already enrolled" }
        store.putCommitted("grants",Json.encodeToString(next));saved.value=next
    }
    @Synchronized fun remove(id:String) {
        check(lease==null) { "Stop SSH before changing keys" }
        val next=saved.value.filterNot { it.id==id }
        store.putCommitted("grants",Json.encodeToString(next));saved.value=next
    }
    fun addresses():List<String> = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        .flatMap { it.inetAddresses.toList() }.map { it.hostAddress.orEmpty() }
        .filter { runCatching { NodeSshBind(it).validate() }.isSuccess }.distinct().sorted()

    suspend fun start(bind:NodeSshBind) {
        // SSHD's default authorized-keys implementation initializes its desktop
        // home-directory path even though Meshlit replaces that authenticator.
        // Android has no user.home, so provide an app-private path first.
        NodeSshServer.setAndroidHome(context.filesDir.toPath())
        check(com.meshlit.legal.LegalAgreementStore(context).accepted()) { "Read and accept the app notices first" }
        gate.requireAllowed(ManagedFeature.SSH);bind.validate()
        require(bind.address in addresses()) { "Select an address currently owned by this node" }
        val approved=saved.value.toList();require(approved.isNotEmpty()) { "Enroll a public key first" }
        val started=CompletableDeferred<Unit>()
        val session=java.util.UUID.randomUUID().toString()
        synchronized(this) {
            check(lease==null) { "SSH is running or stopping" }
            sessionId=session
            enabled=true;mutable.value=NodeSshState("Starting")
            val listenerJob=appScope.launch(start=CoroutineStart.LAZY) {
                try {
                    gate.run(ManagedFeature.SSH) {
                        ContextCompat.startForegroundService(context,Intent(context,NodeSshService::class.java).putExtra("session",session))
                        withContext(Dispatchers.IO) { server.start(bind,hostKey(),approved) }
                        ensureActive();check(enabled)
                        mutable.value=NodeSshState("Running",bind.address,server.boundPort,server.fingerprint)
                        started.complete(Unit)
                        val began=android.os.SystemClock.elapsedRealtime()
                        while(isActive && enabled) {
                            delay(1000)
                            gate.requireAllowed(ManagedFeature.SSH)
                            val memory=ActivityManager.MemoryInfo()
                            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
                            val thermal=if(Build.VERSION.SDK_INT>=29) (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).currentThermalStatus else 0
                            check(!memory.lowMemory && thermal<android.os.PowerManager.THERMAL_STATUS_SEVERE &&
                                android.os.SystemClock.elapsedRealtime()-began<30*60*1000) { "SSH stopped: resource pressure or 30-minute lease" }
                        }
                    }
                } catch(e:CancellationException) { started.completeExceptionally(e) }
                catch(e:Exception) { mutable.value=mutable.value.copy(phase="Failed",error="SSH failed: ${e.javaClass.simpleName}");started.completeExceptionally(e) }
                catch(e:LinkageError) { val failure=IllegalStateException("SSH runtime unavailable: ${e.javaClass.simpleName}",e)
                    mutable.value=mutable.value.copy(phase="Failed",error=failure.message);started.completeExceptionally(failure) }
                finally {
                    enabled=false
                    withContext(NonCancellable+Dispatchers.IO) { runCatching { server.stop() };context.stopService(Intent(context,NodeSshService::class.java)) }
                    synchronized(this@NodeSshHost) { lease=null;sessionId=null;if(mutable.value.phase!="Failed") mutable.value=NodeSshState() }
                }
            }
            // Publish before starting. Even cancellation before the coroutine enters its
            // body must release admission and finish the caller's startup wait.
            lease=listenerJob
            listenerJob.invokeOnCompletion { cause ->
                started.completeExceptionally(cause ?: CancellationException("SSH session ended"))
                synchronized(this@NodeSshHost) {
                    if(lease===listenerJob) {
                        lease=null;sessionId=null;enabled=false
                        if(mutable.value.phase!="Failed") mutable.value=NodeSshState()
                    }
                }
            }
            listenerJob.start()
        }
        try { started.await() } catch(e:CancellationException) { stop(session);throw e }
    }
    @Synchronized fun stop(expectedSession:String?=null) {
        // A destroyed old Service must not cancel a subsequent owner-started session.
        if(expectedSession!=null && sessionId!=expectedSession) return
        enabled=false;lease?.cancel();if(lease!=null) mutable.value=mutable.value.copy(phase="Stopping")
    }

    private fun hostKey():KeyPair {
        store.get("identity")?.let { value ->
            val key=Json.decodeFromString<StoredSshKey>(value);val factory=KeyFactory.getInstance("EC")
            return KeyPair(factory.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(key.publicPart))),
                factory.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(key.privatePart))))
        }
        val pair=KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        store.putCommitted("identity",Json.encodeToString(StoredSshKey(Base64.getEncoder().encodeToString(pair.public.encoded),Base64.getEncoder().encodeToString(pair.private.encoded))))
        return pair
    }
    private suspend fun execute(grant:NodeSshGrant,request:NodeSshRequest):NodeSshReply = gate.run(ManagedFeature.SSH,grant.agent) {
        authorize(grant);grant.requireRequest(request)
        when(request.action) {
            NodeSshAction.STATUS -> NodeSshReply(true,"Meshlit ${com.meshlit.BuildConfig.VERSION_NAME} · ${com.meshlit.BuildProfile.name} · SSH node commands · app UID only")
            NodeSshAction.VM_STATUS -> NodeSshReply(true,"VM ${runtime.vm.state} · agent permission ${runtime.allowAgentVm()} · real runtime artifacts required")
            NodeSshAction.VM_START -> NodeSshReply(true,"VM ${runtime.startVm(grant.agent)} · authentication not yet verified")
            NodeSshAction.VM_WAIT -> NodeSshReply(true,"SSH banner ready: ${runtime.waitForGuest(grant.agent)} · guest command authentication is separate")
            NodeSshAction.VM_STOP -> { runtime.stopVm(grant.agent);NodeSshReply(true,"VM stopped; persistent disk effects remain") }
            NodeSshAction.VM_EXEC -> runtime.executeGuest(request.argv,agentRequested=grant.agent).let {
                NodeSshReply(!it.timedOut && it.exitCode==0,"${it.stdout}\n${it.stderr}",it.exitCode,it.truncated)
            }
            NodeSshAction.APP_EXEC -> {
                check(!grant.agent);request.requireAppDiagnostics()
                runtime.executeApp(request.argv).let { NodeSshReply(!it.timedOut && it.exitCode==0,"${it.stdout}\n${it.stderr}",it.exitCode,it.truncated) }
            }
        }
    }
}

package com.meshlit.security

import android.content.Context
import com.meshlit.core.mcp.security.LabGate
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.sandbox.RuntimeHost
import com.meshlit.core.sandbox.shellQuote
import kotlinx.coroutines.*
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable data class PackageDelegation(val names:List<String> = emptyList(),val expiresAtMs:Long=0,val environmentId:String="")
/** Automatic agent install/uninstall is limited to owner-listed pip packages in the ready VM.
 * Distro root operations and arbitrary source edits remain human-only.
 */
class LabPackages(private val context:Context,private val runtime:RuntimeHost) {
    private val operations = com.meshlit.operations.OperationsControl.get(context).gate
    private val store=EncryptedCredentialStore(context,"lab-package-grants")
    private val json=Json {ignoreUnknownKeys=true}
    fun delegation():PackageDelegation=store.get("delegation")?.let {runCatching {json.decodeFromString<PackageDelegation>(it)}.getOrNull()} ?: PackageDelegation()
    @Synchronized fun delegate(names:List<String>) {require(names.size<=16 && names.all {it.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.+-]{0,99}"))});store.putCommitted("delegation",json.encodeToString(PackageDelegation(names,System.currentTimeMillis()+3600000,if(names.isEmpty()) "" else runtime.labIdentity())))}
    /** Human-only setup; this method is deliberately absent from agent/MCP tools. */
    suspend fun provisionCompanions(guestRootConsent:Boolean):String = withTimeout(120000) {
        operations.requireAllowed(com.meshlit.core.common.control.ManagedFeature.CYBER)
        require(guestRootConsent) { "Approve guest-root setup first" }
        LabGate.requireReady(runtime.config().mode.name,runtime.vm.state.name)
        val session=runtime.labIdentity()
        val identity=runtime.executeGuest(listOf("id","-u"))
        require(identity.exitCode==0 && !identity.timedOut && !identity.truncated && identity.stdout.trim()=="0") { "Configure a guest root SSH account; no sudo is invoked" }
        for ((asset,name) in listOf("meshlit_cyber.py" to "meshlit-cyber","meshlit_packages.py" to "meshlit-packages")) {
            val data=withContext(Dispatchers.IO) {context.assets.open("lab/$asset").use {it.readBytes()}}
            require(data.size in 1..131072)
            val expected=MessageDigest.getInstance("SHA-256").digest(data).joinToString(""){"%02x".format(it.toInt() and 255)}
            val stage="/usr/local/bin/.meshlit-setup-${UUID.randomUUID()}"
            fun checkSession() {require(session==runtime.labIdentity()) {"VM changed during setup"}}
            try {
                checkSession()
                val init=runtime.executeGuest(listOf("sh","-c","umask 077; : > "+shellQuote(stage)))
                require(init.exitCode==0 && !init.timedOut)
                for(chunk in data.asList().chunked(18000)) {
                    ensureActive();checkSession()
                    val encoded=android.util.Base64.encodeToString(chunk.toByteArray(),android.util.Base64.NO_WRAP)
                    val write=runtime.executeGuest(listOf("sh","-c","printf %s "+shellQuote(encoded)+" | base64 -d >> "+shellQuote(stage)))
                    require(write.exitCode==0 && !write.timedOut)
                }
                val verify=runtime.executeGuest(listOf("sha256sum",stage))
                require(verify.exitCode==0 && !verify.timedOut && !verify.truncated && verify.stdout.trim().split(Regex("\\s+")).firstOrNull()==expected)
                checkSession()
                val install=runtime.executeGuest(listOf("sh","-c","chmod 755 "+shellQuote(stage)+" && mv -f "+shellQuote(stage)+" "+shellQuote("/usr/local/bin/$name")))
                require(install.exitCode==0 && !install.timedOut)
            } finally {withContext(NonCancellable) {runCatching {if(runtime.labIdentity()==session) withTimeout(10000) {runtime.executeGuest(listOf("rm","-f",stage),timeoutMs=10000)}}}}
        }
        require(session==runtime.labIdentity())
        val marker=runtime.executeGuest(listOf("touch","/etc/meshlit-lab-owned"))
        require(marker.exitCode==0 && !marker.timedOut && session==runtime.labIdentity())
        "Bundled companion bytes verified and installed in the owned guest. Python/venv/timeout/base64/tool dependencies must already be available."
    }
    suspend fun run(action:String,manager:String,kind:String,source:String,sha256:String,guestRoot:Boolean=false,agent:Boolean=false):JsonObject = operations.run(com.meshlit.core.common.control.ManagedFeature.CYBER,agent) { runManaged(action,manager,kind,source,sha256,guestRoot,agent) }
    private suspend fun runManaged(action:String,manager:String,kind:String,source:String,sha256:String,guestRoot:Boolean=false,agent:Boolean=false):JsonObject {
        LabGate.requireReady(runtime.config().mode.name,runtime.vm.state.name,agent,runtime.allowAgentVm())
        require(action in setOf("list","install","uninstall") && manager in setOf("pip","apt","apk","dnf","pacman") && kind in setOf("repo","file","web"))
        require(source.length<=2000 && sha256.length<=64 && source.none {it=='\u0000' || it=='\n' || it=='\r'})
        val grant=delegation()
        if(agent) require(grant.names.isNotEmpty() && grant.environmentId==runtime.labIdentity() && !guestRoot && manager=="pip" && kind=="repo" && grant.expiresAtMs>System.currentTimeMillis() && (action=="list" || source in grant.names)) {"Agent package grant missing or expired"}
        val args=mutableListOf("timeout","-k","5","50","/usr/local/bin/meshlit-packages",action,"--manager",manager,"--kind",kind,"--source",source,"--sha256",sha256)
        if(guestRoot) args.add("--allow-guest-root")
        val result=runtime.executeGuest(args)
        require(!result.timedOut && !result.truncated && result.exitCode==0) {"Package operation failed or outcome uncertain"}
        if(agent) require(delegation()==grant && grant.environmentId==runtime.labIdentity() && grant.expiresAtMs>System.currentTimeMillis()) {"Package delegation revoked; inspect guest state"}
        val report=json.parseToJsonElement(result.stdout).jsonObject
        require(report["status"]?.jsonPrimitive?.content=="completed")
        store.putCommitted("last-result",report.toString());return report
    }
}

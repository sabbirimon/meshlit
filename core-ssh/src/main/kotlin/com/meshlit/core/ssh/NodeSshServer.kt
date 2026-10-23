package com.meshlit.core.ssh

import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.sshd.common.AttributeRepository.AttributeKey
import org.apache.sshd.common.channel.ChannelListener
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.PublicKeyEntry
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.common.session.Session
import org.apache.sshd.common.session.SessionListener
import org.apache.sshd.common.util.security.SecurityUtils
import org.apache.sshd.core.CoreModuleProperties
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.pubkey.UserAuthPublicKeyFactory
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.channel.ChannelSessionFactory
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.forward.RejectAllForwardingFilter
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyPair
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/** Real SSH transport. JSON exec only: no login shell, SFTP, forwarding, root or runtime configuration. */
@RequiresApi(26)
class NodeSshServer(private val authorize:(NodeSshGrant)->Unit,
    private val execute:suspend (NodeSshGrant,NodeSshRequest)->NodeSshReply) {
    @Volatile private var server:SshServer?=null
    @Volatile private var scope:CoroutineScope?=null
    val running get()=server?.isStarted==true
    val boundPort get()=server?.port
    val fingerprint get()=server?.keyPairProvider?.loadKeys(null)?.firstOrNull()?.public?.let(::fingerprint)

    @Synchronized fun start(bind:NodeSshBind,hostKey:KeyPair,grants:List<NodeSshGrant>) {
        SshAndroidCrypto.ensureCurveRegistry()
        check(server==null) { "Stop SSH before restarting" };bind.validate()
        require(grants.size in 1..32 && grants.map { it.id }.distinct().size==grants.size)
        val keys=grants.map { it.validate();it to publicKey(it.publicKey) }
        require(keys.map { it.first.username to fingerprint(it.second) }.distinct().size==keys.size) { "Duplicate username/key grants" }
        val jobs=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val slots=Semaphore(4)
        val sessions=ConcurrentHashMap.newKeySet<Session>()
        val identity=AttributeKey<NodeSshGrant>()
        val ssh=SshServer.setUpDefaultServer()
        ssh.host=bind.address;ssh.port=bind.port;ssh.keyPairProvider=KeyPairProvider.wrap(hostKey)
        ssh.userAuthFactories=listOf(UserAuthPublicKeyFactory.INSTANCE)
        ssh.publickeyAuthenticator=org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator { username,key,session ->
            val grant=keys.firstOrNull { it.first.username==username && KeyUtils.compareKeys(it.second,key) }?.first
            if(grant==null || runCatching { authorize(grant) }.isFailure) false
            else { session.setAttribute(identity,grant);true }
        }
        ssh.passwordAuthenticator=null;ssh.keyboardInteractiveAuthenticator=null;ssh.shellFactory=null
        ssh.subsystemFactories=emptyList();ssh.forwardingFilter=RejectAllForwardingFilter.INSTANCE
        ssh.channelFactories=listOf(ChannelSessionFactory.INSTANCE)
        ssh.signatureFactories=ssh.signatureFactories.filter { it.name in setOf("ecdsa-sha2-nistp256","rsa-sha2-256","rsa-sha2-512") }
        ssh.keyExchangeFactories=ssh.keyExchangeFactories.filter { it.name in setOf("ecdh-sha2-nistp256","diffie-hellman-group14-sha256","diffie-hellman-group16-sha512") }
        ssh.cipherFactories=ssh.cipherFactories.filter { it.name in setOf("aes128-ctr","aes256-ctr","aes128-gcm@openssh.com","aes256-gcm@openssh.com") }
        ssh.macFactories=ssh.macFactories.filter { it.name in setOf("hmac-sha2-256-etm@openssh.com","hmac-sha2-512-etm@openssh.com") }
        CoreModuleProperties.AUTH_TIMEOUT.set(ssh,Duration.ofSeconds(15))
        CoreModuleProperties.IDLE_TIMEOUT.set(ssh,Duration.ofSeconds(60))
        CoreModuleProperties.MAX_AUTH_REQUESTS.set(ssh,5)
        CoreModuleProperties.MAX_CONCURRENT_SESSIONS.set(ssh,4)
        CoreModuleProperties.MAX_CONCURRENT_CHANNELS.set(ssh,2)
        CoreModuleProperties.NIO_WORKERS.set(ssh,2)
        CoreModuleProperties.WINDOW_TIMEOUT.set(ssh,Duration.ofSeconds(5))
        CoreModuleProperties.WAIT_FOR_SPACE_TIMEOUT.set(ssh,Duration.ofSeconds(5))
        CoreModuleProperties.STOP_WAIT_TIME.set(ssh,Duration.ofSeconds(3))
        ssh.addSessionListener(object:SessionListener {
            override fun sessionCreated(session:Session) { sessions.add(session);if(sessions.size>4) session.close(true) }
            override fun sessionClosed(session:Session) { sessions.remove(session) }
        })
        ssh.commandFactory=org.apache.sshd.server.command.CommandFactory { channel,command ->
            val grant=channel.session.getAttribute(identity) ?: error("Unauthenticated command")
            val request=NodeSshRequest.parse(command);grant.requireRequest(request);authorize(grant)
            NodeCommand(jobs,slots,grant,request,authorize,execute)
        }
        try { ssh.start();server=ssh;scope=jobs }
        catch(t:Throwable) { jobs.cancel();runCatching { ssh.stop(true) };throw t }
    }
    @Synchronized fun stop() {
        val previous=server;server=null;scope?.cancel();scope=null
        previous?.stop(true)
    }
    companion object {
        fun setAndroidHome(path:java.nio.file.Path) {
            org.apache.sshd.common.util.io.PathUtils.setUserHomeFolderResolver { path }
        }
        fun publicKey(line:String):PublicKey {
            SshAndroidCrypto.ensureCurveRegistry()
            require(line.length in 1..4096 && '\n' !in line && '\r' !in line)
            val entry=PublicKeyEntry.parsePublicKeyEntry(line)
            require(entry.keyType in setOf("ecdsa-sha2-nistp256","ssh-rsa")) { "Use an ECDSA P-256 or RSA 3072+ public key" }
            return entry.resolvePublicKey(null,emptyMap(),null).also { key ->
                require(key !is RSAPublicKey || key.modulus.bitLength()>=3072) { "RSA keys need at least 3072 bits" }
            }
        }
        fun fingerprint(key:PublicKey):String=KeyUtils.getFingerPrint(org.apache.sshd.common.digest.BuiltinDigests.sha256,key)
    }
    private class NodeCommand(val scope:CoroutineScope,val slots:Semaphore,val grant:NodeSshGrant,val request:NodeSshRequest,
        val authorize:(NodeSshGrant)->Unit,val execute:suspend(NodeSshGrant,NodeSshRequest)->NodeSshReply):Command {
        private var out:OutputStream?=null;private var err:OutputStream?=null;private var exit:ExitCallback?=null
        private var job:Job?=null
        override fun setInputStream(input:InputStream?)=Unit
        override fun setOutputStream(output:OutputStream?) { out=output }
        override fun setErrorStream(error:OutputStream?) { err=error }
        override fun setExitCallback(callback:ExitCallback?) { exit=callback }
        override fun start(channel:ChannelSession,env:Environment) {
            job=scope.launch {
                var acquired=false
                val result=try {
                    check(slots.tryAcquire()) { "SSH command capacity reached" };acquired=true
                    withTimeout(60000) { authorize(grant);execute(grant,request).also { authorize(grant);ensureActive() } }
                } catch(e:CancellationException) { NodeSshReply(false,"Command stopped; completed effects are not undone") }
                catch(e:Exception) { NodeSshReply(false,"Command failed or denied: ${e.javaClass.simpleName}") }
                finally { if(acquired) slots.release() }
                try {
                    val bytes=(Json.encodeToString(result)+"\n").toByteArray()
                    check(bytes.size<=131072) { "Reply exceeds SSH output bound" }
                    out?.write(bytes);out?.flush();exit?.onExit(if(result.ok) 0 else 1)
                } catch(_:Exception) { exit?.onExit(1) }
            }
        }
        override fun destroy(channel:ChannelSession) { job?.cancel() }
    }
}

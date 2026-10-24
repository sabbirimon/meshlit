package com.meshlit.core.ssh

import com.jcraft.jsch.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

@Serializable data class SshConnection(val id:String,val name:String,val host:String,val port:Int=22,val username:String,val hostKeySha256:String,val agentAllowed:Boolean=false,val credentialEnvironmentId:String?=null,val agentNodeActions:Set<NodeSshAction> = emptySet()) {
    fun validate(){require(NodeSshAction.APP_EXEC !in agentNodeActions){"Agent app execution is blocked"};require(credentialEnvironmentId==null || credentialEnvironmentId.matches(Regex("[A-Za-z0-9_-]{1,80}")));require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")) && name.length in 1..100);require(host.length in 1..253 && host.none{it.isWhitespace() || it in "/@?#"});require(port in 1..65535);require(username.matches(Regex("[a-zA-Z0-9_.-]{1,64}")));require(hostKeySha256.matches(Regex("SHA256:[A-Za-z0-9+/]{43}"))) { "Enter the independently verified SHA256 SSH host-key fingerprint" }}
}
@Serializable data class SshCommandResult(val exitCode:Int,val stdout:String,val stderr:String,val truncated:Boolean)
/** Host-key pinning is mandatory. No accept-new, shell agent forwarding or public SSH listener. */
class SshClient {
    private class Output:ByteArrayOutputStream(){var truncated=false;private set
        @Synchronized override fun write(b:Int){if(count<65536) super.write(b) else truncated=true}
        @Synchronized override fun write(b:ByteArray,off:Int,len:Int){val length=minOf(len,65536-count);if(length<len) truncated=true;if(length>0) super.write(b,off,length)}
        @Synchronized fun text()=toByteArray().toString(Charsets.UTF_8)
    }
    suspend fun execute(config:SshConnection,password:String?,privateKey:String?,command:String):SshCommandResult=withTimeout(60000){withContext(Dispatchers.IO){coroutineScope {
        config.validate();require(command.isNotBlank() && command.length<=8000 && '\u0000' !in command)
        require(!password.isNullOrBlank() || !privateKey.isNullOrBlank()){ "Save a password or unencrypted private key" }
        val jsch=JSch()
        if(!privateKey.isNullOrBlank()){require(privateKey.length<=65536);jsch.addIdentity(config.id,privateKey.toByteArray(),null,null)}
        jsch.hostKeyRepository=object:HostKeyRepository {
            override fun check(host:String?,key:ByteArray?):Int {
                if(key==null) return HostKeyRepository.NOT_INCLUDED
                val actual="SHA256:"+Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(key))
                return if(MessageDigest.isEqual(actual.toByteArray(),config.hostKeySha256.toByteArray())) HostKeyRepository.OK else HostKeyRepository.CHANGED
            }
            override fun add(hostkey:HostKey?,ui:UserInfo?)=Unit
            override fun remove(host:String?,type:String?)=Unit
            override fun remove(host:String?,type:String?,key:ByteArray?)=Unit
            override fun getKnownHostsRepositoryID()="meshlit-pinned-${config.id}"
            override fun getHostKey():Array<HostKey> = emptyArray()
            override fun getHostKey(host:String?,type:String?):Array<HostKey> = emptyArray()
        }
        val session=jsch.getSession(config.username,config.host,config.port)
        password?.takeIf{it.isNotBlank()}?.let{session.setPassword(it.toByteArray())}
        session.setConfig("StrictHostKeyChecking","yes");session.setConfig("PreferredAuthentications",if(privateKey.isNullOrBlank()) "password" else "publickey")
        session.timeout=15000
        var channel:ChannelExec?=null
        val cancellation=launch(Dispatchers.IO){try{awaitCancellation()}finally{channel?.disconnect();session.disconnect()}}
        try {
            session.connect(15000)
            channel=session.openChannel("exec") as ChannelExec
            channel.setCommand(command);channel.setInputStream(null)
            val stdout=Output();val stderr=Output();channel.setOutputStream(stdout);channel.setErrStream(stderr)
            channel.connect(15000)
            while(!channel.isClosed){ensureActive();delay(20)}
            SshCommandResult(channel.exitStatus,stdout.text(),stderr.text(),stdout.truncated || stderr.truncated)
        } finally {channel?.disconnect();session.disconnect();cancellation.cancel()}
    }}}
}

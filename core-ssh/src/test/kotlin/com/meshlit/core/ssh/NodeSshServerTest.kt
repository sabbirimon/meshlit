package com.meshlit.core.ssh

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.sshd.common.config.keys.PublicKeyEntry

/** Real TCP, SSH handshake, public-key auth and pinned client; not a mocked transport. */
class NodeSshServerTest {
    private fun key()=KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private fun clientKey():Pair<String,String> {
        val key=com.jcraft.jsch.KeyPair.genKeyPair(com.jcraft.jsch.JSch(),com.jcraft.jsch.KeyPair.ECDSA,256)
        return try {
            val bytes=java.io.ByteArrayOutputStream();key.writePrivateKey(bytes)
            ("ecdsa-sha2-nistp256 "+Base64.getEncoder().encodeToString(key.publicKeyBlob)) to bytes.toString("UTF-8")
        } finally { key.dispose() }
    }
    @Test fun genuineServerPinAuthenticationScopesAndRevocation()=runBlocking {
        val host=key();val client=clientKey();val revoked=AtomicBoolean(false)
        val grant=NodeSshGrant("client","Test","meshlit",client.first,agent=true)
        var executions=0
        val server=NodeSshServer({ check(!revoked.get()) { "Grant revoked" } }, { _,request ->
            executions++;NodeSshReply(true,"actual-${request.action}")
        })
        val port=ServerSocket(0).use { it.localPort }
        try {
            server.start(NodeSshBind(port=port),host,listOf(grant))
            val connection=SshConnection("test","Test","127.0.0.1",port,"meshlit",server.fingerprint!!)
            val reply=SshClient().execute(connection,null,client.second,"status")
            assertEquals(0,reply.exitCode);assertTrue(reply.stdout.contains("actual-STATUS"));assertEquals(1,executions)
            val changed="SHA256:"+Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32))
            assertTrue(runCatching { SshClient().execute(connection.copy(hostKeySha256=changed),null,client.second,"status") }.isFailure)
            assertTrue(runCatching { SshClient().execute(connection,null,clientKey().second,"status") }.isFailure)
            assertTrue(runCatching { SshClient().execute(connection,"password",null,"status") }.isFailure)
            val denied=runCatching { SshClient().execute(connection,null,client.second,"{\"action\":\"VM_START\"}") }
            assertTrue(denied.isFailure || denied.getOrThrow().exitCode!=0)
            assertEquals(1,executions)
            revoked.set(true)
            assertTrue(runCatching { SshClient().execute(connection,null,client.second,"status") }.isFailure)
            assertEquals(1,executions)
        } finally { server.stop() }
        assertFalse(server.running)
    }
    @Test fun stopCancelsAnActualAuthenticatedInFlightCommand()=runBlocking {
        val entered=CompletableDeferred<Unit>();val cancelled=CompletableDeferred<Unit>()
        val client=clientKey();val grant=NodeSshGrant("client","Test","meshlit",client.first,agent=true)
        val server=NodeSshServer({}, { _,_ ->
            entered.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        })
        val port=ServerSocket(0).use { it.localPort }
        try {
            server.start(NodeSshBind(port=port),key(),listOf(grant))
            val connection=SshConnection("test","Test","127.0.0.1",port,"meshlit",server.fingerprint!!)
            val request=async(Dispatchers.IO) { runCatching { SshClient().execute(connection,null,client.second,"status") } }
            withTimeout(15000) { entered.await() }
            server.stop()
            withTimeout(15000) { cancelled.await();request.await() }
            assertFalse(server.running)
        } finally { server.stop() }
    }
}

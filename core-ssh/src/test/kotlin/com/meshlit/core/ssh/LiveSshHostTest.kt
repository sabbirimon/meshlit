package com.meshlit.core.ssh

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/** Opt-in actual OpenSSH integration. Never requires a fixture server in ordinary CI. */
class LiveSshHostTest {
    @Test fun encryptedExecHostPinExitStatusOutputLimitAndCancellation()=runBlocking {
        val directory=System.getenv("MESHLIT_LIVE_SSH_DIR")
        assumeTrue("Set MESHLIT_LIVE_SSH_DIR to a disposable loopback OpenSSH fixture",directory!=null)
        val root=File(requireNotNull(directory))
        val public=File(root,"host.pub").readText().trim().split(Regex("\\s+"))[1]
        val hash="SHA256:"+Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(public)))
        val config=SshConnection("live-test","Disposable local OpenSSH","127.0.0.1",File(root,"port").readText().trim().toInt(),System.getProperty("user.name"),hash)
        val key=File(root,"client").readText()
        val client=SshClient()
        val result=client.execute(config,null,key,"printf meshlit-live-ssh; printf stderr-proof >&2; exit 7")
        assertEquals(7,result.exitCode);assertEquals("meshlit-live-ssh",result.stdout);assertEquals("stderr-proof",result.stderr);assertFalse(result.truncated)
        assertTrue("Changed host key was accepted",runCatching{client.execute(config.copy(hostKeySha256="SHA256:"+"A".repeat(43)),null,key,"true")}.isFailure)
        val large=client.execute(config,null,key,"/usr/bin/yes x | /usr/bin/head -c 70000")
        assertEquals(0,large.exitCode);assertEquals(65536,large.stdout.toByteArray().size);assertTrue(large.truncated)
        assertTrue(runCatching{withTimeout(300){client.execute(config,null,key,"/bin/sleep 2")}}.exceptionOrNull() is TimeoutCancellationException)
        println("MESHLIT_LIVE_SSH encryptedExec=true pinnedHostKey=true realExitStatus=true outputBound=true cancellation=true")
    }
}

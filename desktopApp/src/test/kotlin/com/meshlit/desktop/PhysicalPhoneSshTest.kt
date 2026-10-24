package com.meshlit.desktop

import com.meshlit.core.ssh.SshClient
import com.meshlit.core.ssh.SshConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in Studio transport acceptance against an owner-approved phone node. */
class PhysicalPhoneSshTest {
    @Test fun studioClientReadsPhoneStatusWithPinnedHostKey()=runBlocking {
        val port=System.getenv("MESHLIT_PHONE_SSH_PORT")
        val pin=System.getenv("MESHLIT_PHONE_SSH_PIN")
        val keyFile=System.getenv("MESHLIT_PHONE_SSH_KEY_FILE")
        assumeTrue("Set the physical phone SSH fixture environment",port!=null && pin!=null && keyFile!=null)
        val config=SshConnection("phone-test","Approved phone",System.getenv("MESHLIT_PHONE_SSH_HOST") ?: "127.0.0.1",requireNotNull(port).toInt(),"meshlit",requireNotNull(pin))
        val reply=SshClient().execute(config,null,File(requireNotNull(keyFile)).readText(),"status")
        assertEquals(0,reply.exitCode)
        assertTrue(reply.stdout.contains("\"ok\":true"))
        assertTrue(reply.stdout.contains("SSH node commands"))
    }
}

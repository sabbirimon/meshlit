package com.meshlit.core.ssh

import com.jcraft.jsch.JSch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SshDeviceKeysTest {
    @Test fun generatedKeyIsAcceptedByHostAndClient() {
        val identity = SshDeviceKeys.generate()
        assertTrue(identity.publicKey.startsWith("ecdsa-sha2-nistp256 "))
        NodeSshServer.publicKey(identity.publicKey)
        val client = JSch()
        client.addIdentity("device", identity.privateKey.toByteArray(), null, null)
        assertEquals(1, client.identityRepository.identities.size)
    }

    @Test fun opensshP256PublicKeyIsAccepted() {
        val key = "ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBFY+tfnSOIOjD4nFij7e+o87AZSv6tZ/8tdYBrtSc39R4v3nl8JChHkdRw4Y7RhiLzGeJuIFiHCS1JYv2nqcmec= meshlit-device-test"
        NodeSshServer.publicKey(key)
    }
}

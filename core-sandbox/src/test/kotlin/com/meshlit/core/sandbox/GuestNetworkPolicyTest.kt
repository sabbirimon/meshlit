package com.meshlit.core.sandbox

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class GuestNetworkPolicyTest {
    @Test fun guestActionsCannotUseAnUnrelatedSshPort() {
        RuntimeConfig(mode=RuntimeMode.VM_SSH,sshPort=2222).requireGuestBinding(2222)
        assertTrue(runCatching {RuntimeConfig(mode=RuntimeMode.VM_SSH,sshPort=3333).requireGuestBinding(2222)}.isFailure)
        assertTrue(runCatching {RuntimeConfig(mode=RuntimeMode.ROOT,sshPort=2222).requireGuestBinding(2222)}.isFailure)
    }
    @Test fun networkIsRestrictedUnlessOwnerOptsIn() {
        val directory=Files.createTempDirectory("vm-network-policy").toFile()
        try {
            val executable=java.io.File(directory,"qemu").apply {writeText("fixture");setExecutable(true)}
            val disk=java.io.File(directory,"owned.qcow2").apply {writeText("fixture")}
            val config=VmConfig(executable.absolutePath,disk.absolutePath,GuestArchitecture.X86_64)
            assertTrue(config.argv().any {it.contains("restrict=on")})
            assertTrue(config.argv().contains("-snapshot"))
            assertFalse(config.copy(persistDisk=true).argv().contains("-snapshot"))
            val opted=config.copy(allowOutboundNetwork=true).argv()
            assertTrue(opted.any {it.contains("restrict=off")})
            assertTrue(opted.any {it.contains("hostfwd=tcp:127.0.0.1:")})
            assertFalse(opted.any {it.contains("0.0.0.0")})
        } finally {directory.deleteRecursively()}
    }
}

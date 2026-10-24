package com.meshlit.core.ssh
import org.junit.Assert.*
import org.junit.Test
class SshConnectionTest {
    private val good=SshConnection("test","NAS","192.168.1.4",username="owner",hostKeySha256="SHA256:"+"A".repeat(43))
    @Test fun pinIsRequiredBeforeAnyNetworkConnection(){good.validate();assertThrows(IllegalArgumentException::class.java){good.copy(hostKeySha256="").validate()};assertThrows(IllegalArgumentException::class.java){good.copy(hostKeySha256="MD5:abcd").validate()}}
    @Test fun malformedTargetsAreRejected(){listOf("-bad host","user@host","host/path").forEach{host->assertThrows(IllegalArgumentException::class.java){good.copy(host=host).validate()}};assertThrows(IllegalArgumentException::class.java){good.copy(port=0).validate()}}
}

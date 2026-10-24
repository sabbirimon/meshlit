package com.meshlit.core.mcp.control
import org.junit.Assert.*
import org.junit.Test
class DeviceDirectoryTest {
    @Test fun consentCredentialsGroupsRevocation(){
        val store=object:DeviceDirectoryStore{var value=DeviceDirectorySnapshot();override fun load()=value;override fun save(snapshot:DeviceDirectorySnapshot){value=snapshot}}
        val directory=DeviceDirectory(store);val token="a".repeat(64)
        directory.request("desktop","Desk",DeviceKind.COMPUTER,token,setOf("compute"))
        assertNull(directory.authenticate(token))
        directory.approve("desktop",setOf(DeviceAccess.OBSERVE))
        assertEquals(setOf(DeviceAccess.OBSERVE),directory.authenticate(token)?.access)
        assertNull(directory.authenticate("b".repeat(64)))
        directory.group("group","My cluster",setOf("desktop"));directory.revoke("desktop")
        assertNull(directory.authenticate(token));assertTrue(directory.read().groups.single().memberIds.isEmpty())
        assertThrows(IllegalArgumentException::class.java){directory.request("desktop","Desk",DeviceKind.COMPUTER,"c".repeat(64),emptySet())}
    }
    @Test fun microcontrollerRolesDoNotGrantComputeOrActions(){
        val store=object:DeviceDirectoryStore{var value=DeviceDirectorySnapshot();override fun load()=value;override fun save(snapshot:DeviceDirectorySnapshot){value=snapshot}}
        val directory=DeviceDirectory(store);val token="d".repeat(64)
        val entry=directory.request("esp32","Sensor node",DeviceKind.MICROCONTROLLER,token,setOf("sensors","actuation","monitoring"))
        assertEquals(EnrollmentState.PENDING,entry.state);assertTrue(entry.access.isEmpty());assertNull(directory.authenticate(token))
        directory.approve(entry.id,setOf(DeviceAccess.OBSERVE));assertEquals(setOf(DeviceAccess.OBSERVE),directory.authenticate(token)?.access)
    }
    @Test fun rememberedClientExpiresAndNeedsFreshApproval() {
        val store=object:DeviceDirectoryStore{var value=DeviceDirectorySnapshot();override fun load()=value;override fun save(snapshot:DeviceDirectorySnapshot){value=snapshot}}
        var now=1_000_000L
        val directory=DeviceDirectory(store,clock={now})
        val token="e".repeat(64)
        val entry=directory.request("browser","Browser",DeviceKind.BROWSER,token,setOf("control"))
        directory.approve(entry.id,setOf(DeviceAccess.OBSERVE))
        assertNotNull(directory.authenticate(token))
        now=entry.expiresAtMs
        assertNull(directory.authenticate(token))
        val renewed=directory.request("browser","Browser",DeviceKind.BROWSER,token,setOf("control"))
        assertEquals(EnrollmentState.PENDING,renewed.state)
        assertTrue(renewed.access.isEmpty())
        assertNull(directory.authenticate(token))
        directory.approve(entry.id,setOf(DeviceAccess.OBSERVE))
        assertNotNull(directory.authenticate(token))
        directory.revoke(entry.id)
        assertNull(directory.authenticate(token))
    }
}

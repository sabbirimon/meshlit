package com.meshlit.core.mcp.control
import org.junit.Assert.*
import org.junit.Test
class ClusterCapacityTest {
    @Test fun agentCannotExpandOwnerBoundsOrExecutionCeiling(){
        assertThrows(IllegalArgumentException::class.java){ClusterCapacity().agentChange(500)}
        val policy=ClusterCapacity(agentMayManageCapacity=true,agentMaximum=1000)
        assertEquals(500,policy.agentChange(500).deviceLimit)
        assertThrows(IllegalArgumentException::class.java){policy.agentChange(1001)}
        assertThrows(IllegalArgumentException::class.java){policy.copy(pipelineWorkerLimit=1000).validate()}
    }
    @Test fun inventoryAdmissionUsesCurrentCapWithoutRevokingExistingDevices(){
        val store=object:DeviceDirectoryStore{var value=DeviceDirectorySnapshot();override fun load()=value;override fun save(snapshot:DeviceDirectorySnapshot){value=snapshot}}
        var limit=2;val directory=DeviceDirectory(store){limit}
        directory.request("first","First",DeviceKind.SERVER,"a".repeat(64),emptySet());directory.request("second","Second",DeviceKind.SERVER,"b".repeat(64),emptySet())
        limit=1;assertEquals("first",directory.request("first","First",DeviceKind.SERVER,"a".repeat(64),emptySet()).id)
        assertThrows(IllegalArgumentException::class.java){directory.request("third","Third",DeviceKind.SERVER,"c".repeat(64),emptySet())}
        assertEquals(2,directory.read().devices.size)
    }
}

package com.meshlit.core.gpu
import org.junit.Assert.*
import org.junit.Test
class MemoryTopologyTest {
    @Test fun unknownBusCannotBeReplacedByBandwidthClaim(){
        val memory=MemoryTopologyObservation("ram","uma",MemoryKind.UNIFIED_RAM,100,"OS memory report",allocatableBytes=1000)
        memory.validate();assertNull(memory.busWidthBits);assertNull(memory.measuredBandwidthBytesPerSecond)
        assertThrows(IllegalArgumentException::class.java){memory.copy(measuredBandwidthBytesPerSecond=1e9).validate()}
    }
}

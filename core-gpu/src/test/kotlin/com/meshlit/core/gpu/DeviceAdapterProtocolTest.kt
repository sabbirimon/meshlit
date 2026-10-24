package com.meshlit.core.gpu
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class DeviceAdapterProtocolTest {
    @Test fun absentAdapterAndUnknownCountersStayUnavailable()=runBlocking {
        assertTrue(runCatching{CommonDeviceProfiler(emptyList()).describe("cuda")}.isFailure)
        val reading=DeviceProfileResult(adapterId="owned",deviceId="device",observedAtMs=100,counters=mapOf("hbm.bandwidth" to null),units=mapOf("hbm.bandwidth" to "bytes/s"),source="unavailable vendor counter")
        reading.validate();assertNull(reading.counters["hbm.bandwidth"])
        assertThrows(IllegalArgumentException::class.java){reading.copy(counters=mapOf("hbm.bandwidth" to Double.NaN)).validate()};Unit
    }
}

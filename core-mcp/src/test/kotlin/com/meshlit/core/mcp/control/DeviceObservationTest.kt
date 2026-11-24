package com.meshlit.core.mcp.control
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
class DeviceObservationTest {
    private fun payload(at:Long,cores:Int,ram:Long)=buildJsonObject{put("observedAtMs",at);put("cpuCores",cores);put("availableRamBytes",ram);put("totalRamBytes",ram*2)}
    @Test fun typedReceiptsPreserveSourceAndRejectStaleOrInvalidCapacity() {
        val d=DeviceObservations.parse("one","Phone",payload(1000,8,100),1000)
        assertEquals("one",d.sourceId);assertTrue(d.fresh(2000));assertFalse(d.fresh(32000));assertEquals(64,d.payloadSha256.length)
        assertThrows(IllegalArgumentException::class.java){DeviceObservations.parse("one","Phone",payload(1000,0,100),1000)}
        assertThrows(IllegalArgumentException::class.java){DeviceObservations.parse("one","Phone",payload(1000,8,100),62000)}
    }
    @Test fun recommendationsExcludeStaleAndInsufficientNodes() {
        val a=DeviceObservations.parse("a","A",payload(1000,8,1000),1000)
        val b=DeviceObservations.parse("b","B",payload(1000,4,2000),1000)
        assertEquals(listOf("a","b"),DeviceObservations.rankForCpuTask(listOf(b,a),500,2000))
        assertEquals(listOf("b"),DeviceObservations.rankForCpuTask(listOf(a,b),1500,2000))
        assertTrue(DeviceObservations.rankForCpuTask(listOf(a,b),500,32000).isEmpty())
    }
    @Test fun realSensorEnvelopeAcceptsComponentUnitsAndRejectsNumericStrings() {
        val value=Json.parseToJsonElement("""{"observedAtMs":1000,"sharedLiveSensors":[{"type":1,"name":"motion","values":[1,2,3],"units":["m/s²","m/s²","m/s²"],"observedAtMs":1000}]}""")
        val sensor=DeviceObservations.parse("phone","Phone",value,1000).sensors.single()
        assertEquals(listOf("m/s²","m/s²","m/s²"),sensor.units)
        assertThrows(IllegalArgumentException::class.java){DeviceObservations.parse("phone","Phone",Json.parseToJsonElement("""{"observedAtMs":1000,"cpuCores":"8"}"""),1000)}
    }
}

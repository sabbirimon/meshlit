package com.meshlit.core.gpu
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
@Serializable enum class DeviceOperation { DESCRIBE, PROFILE, QUALIFY, EXECUTE_KERNEL, GENERATE, CANCEL }
@Serializable data class DeviceAdapterCapabilities(val schema:Int=1,val adapterId:String,val runtimeRevision:String,
    val device:AcceleratorAdvertisement,val operations:Set<DeviceOperation>,val precisions:Set<String>,val operators:Set<String>,
    val memory:List<MemoryTopologyObservation> = emptyList()) {
    fun validate(){require(schema==1 && adapterId.matches(Regex("[a-z][a-z0-9_.-]{0,63}")) && runtimeRevision.isNotBlank());require(operations.size<=DeviceOperation.entries.size);require(precisions.size<=32 && operators.size<=256 && (precisions+operators).all{it.matches(Regex("[A-Za-z0-9_.-]{1,64}"))});memory.forEach{it.validate()}}
}
@Serializable data class DeviceProfileResult(val schema:Int=1,val adapterId:String,val deviceId:String,val observedAtMs:Long,
    val counters:Map<String,Double?>,val units:Map<String,String>,val source:String) {
    fun validate(){require(schema==1 && adapterId.isNotBlank() && deviceId.isNotBlank() && observedAtMs>0 && source.isNotBlank());require(counters.size<=128 && counters.keys==units.keys && counters.values.all{it==null || it.isFinite()});require(counters.keys.all{it.matches(Regex("[a-z][a-z0-9_.-]{0,63}"))} && units.values.all{it.length in 1..32})}
}
/** The SDK-specific implementation is responsible for mapping genuine profiler readings.
 * No process spawning, profiling privilege or unknown SDK translation is granted here. */
interface DeviceAdapter {
    val id:String
    suspend fun describe():DeviceAdapterCapabilities
    suspend fun profile():DeviceProfileResult
}
class CommonDeviceProfiler(adapters:List<DeviceAdapter>) {
    private val registered=adapters.associateBy{it.id}.also{require(it.size==adapters.size)}
    suspend fun describe(id:String):DeviceAdapterCapabilities=withTimeout(10000){(registered[id] ?: error("Device adapter unavailable")).describe().also{it.validate();require(it.adapterId==id)}}
    suspend fun profile(id:String):DeviceProfileResult=withTimeout(10000){(registered[id] ?: error("Device profiler unavailable")).profile().also{it.validate();require(it.adapterId==id)}}
}

package com.meshlit.core.inference.models
import kotlinx.serialization.Serializable

@Serializable data class DeviceRuntimeSnapshot(val os:String,val api:Int,val abi:String,val totalRam:Long,
    val availableRam:Long,val freeStorage:Long,val lowRam:Boolean,val thermal:Int?,val cpuCores:Int,
    val nativeLocalInstalled:Boolean,val runAnywhereInstalled:Boolean,val manufacturer:String,val model:String,val soc:String,
    val powerSave:Boolean?=null,val batteryPercent:Int?=null,val charging:Boolean?=null,val memoryPressure:Boolean?=null)
@Serializable data class DeviceRuntimePlan(val category:String,val memoryBudget:Long,val recommendedContext:Int,
    val maxContext:Int,val cpuThreads:Int,val localInferenceSupported:Boolean,val nativeTuningSupported:Boolean,
    val allowHeavyWork:Boolean,val reasons:List<String>)
/** Conservative planning from observed resources. Neither chipset labels nor OS version prove acceleration. */
object DeviceRuntimePolicy {
    fun plan(device:DeviceRuntimeSnapshot):DeviceRuntimePlan {
        require(device.availableRam>=0 && device.totalRam>=0 && device.freeStorage>=0)
        val old=device.api<29
        val light=device.lowRam || device.totalRam<4L*1024*1024*1024 || old
        val high=!light && device.totalRam>=8L*1024*1024*1024 && device.api>=31
        val thermal=device.thermal?.takeIf{it>=0}
        val battery=device.batteryPercent?.takeIf{it in 0..100}
        val thermalBlocked=thermal?.let{it>=3} ?: false
        val criticalBattery=battery!=null && battery<=5 && device.charging!=true
        val blocked=thermalBlocked || criticalBattery || device.memoryPressure==true
        val budget=(device.availableRam*0.60).toLong().minus(256L*1024*1024).coerceAtLeast(0)
        val category=if(light) "Light" else if(high) "High capacity" else "Balanced"
        val cap=if(light) 2048 else if(high) 8192 else 4096
        val context=if(light || budget<512L*1024*1024) 512 else if(high && budget>2L*1024*1024*1024) 4096 else 2048
        val supported=device.os=="Android" && device.api>=24 && device.abi in setOf("arm64-v8a","x86_64") &&
            (device.runAnywhereInstalled || device.nativeLocalInstalled)
        val baseThreads=if(light) 1 else (device.cpuCores/2).coerceIn(1,4)
        // Admission is recalculated for a new load only; never rewrite a running
        // engine's threads or the owner's saved cap. Missing readings are unknown.
        var threads=baseThreads
        if(thermal==null || thermal>=2) threads=minOf(threads,2)
        else if(thermal==1) threads=minOf(threads,3)
        if(device.powerSave==true) threads=1
        else if(device.powerSave==null) threads=minOf(threads,2)
        if(battery==null || device.memoryPressure==null) threads=minOf(threads,2)
        if(battery!=null && battery<=15 && device.charging!=true) threads=1
        if(budget<1024L*1024*1024) threads=1
        else if(budget<2L*1024*1024*1024) threads=minOf(threads,2)
        if(blocked) threads=1
        val reasons=buildList {
            if(!supported) add("No supported local native engine for this OS/API/ABI")
            if(device.lowRam) add("Android reports a low-RAM device")
            if(old) add("Older Android version uses conservative limits")
            if(thermalBlocked) add("Severe thermal state: new heavy loads are blocked until the device cools")
            else if(thermal!=null && thermal>0) add("Thermal pressure reduces the CPU thread cap for the next load")
            if(thermal==null) add("Thermal reading unavailable; temperature is not assumed normal")
            if(device.powerSave==true) add("Android power saver limits the next load to one CPU thread")
            if(device.powerSave==null) add("Power-saver state unavailable; use a conservative CPU cap")
            if(criticalBattery) add("Battery is at or below 5% and charging is not confirmed: new heavy loads are blocked")
            else if(battery!=null && battery<=15 && device.charging!=true) add("Low battery without confirmed charging limits the next load to one CPU thread")
            if(battery==null) add("Battery level unavailable; use a conservative CPU cap")
            if(device.memoryPressure==true) add("Android reports low available memory: new heavy loads are blocked")
            if(device.memoryPressure==null) add("Live memory-pressure status unavailable; use a conservative CPU cap")
            if(budget<2L*1024*1024*1024) add("Current free-memory budget reduces the CPU cap for the next load")
            if(budget<=256L*1024*1024) add("Available RAM leaves only $budget bytes of estimated load budget; close unused apps or unload the existing model before a new load")
            add("RAM budget is an estimate, not a reservation; rechecked before load")
            add("GPU/NPU support requires a working backend probe; chipset names alone do not enable it")
        }
        return DeviceRuntimePlan(category,budget,context.coerceAtMost(cap),cap,
            threads,supported,device.nativeLocalInstalled && supported,
            supported && !blocked && budget>256L*1024*1024,reasons)
    }
    fun fits(plan:DeviceRuntimePlan,modelBytes:Long,kvBytes:Long?):Boolean =
        plan.allowHeavyWork && modelBytes>0 && modelBytes*1.15+(kvBytes ?: 256L*1024*1024)+128L*1024*1024 < plan.memoryBudget
}

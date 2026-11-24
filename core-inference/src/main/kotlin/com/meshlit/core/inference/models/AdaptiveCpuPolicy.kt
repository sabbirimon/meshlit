package com.meshlit.core.inference.models

enum class CpuMode { BALANCED, BATTERY_SAVER, PERFORMANCE, OVERDRIVE, OVERDRIVE_PLUS }
data class CpuOverrides(val heat:Boolean=false,val saver:Boolean=false,val battery:Boolean=false,val memory:Boolean=false,val background:Boolean=false)
data class CpuConditions(val cores:Int,val thermal:Int?,val saver:Boolean?,val battery:Int?,val charging:Boolean?,val lowMemory:Boolean?,val foreground:Boolean,val unknownAsPressure:Boolean=true)
data class CpuDecision(val threads:Int,val headroom:Int,val reason:String)
/** CPU scheduling is independent from model memory admission. No affinity or core ownership claim. */
object AdaptiveCpuPolicy {
    fun decide(c:CpuConditions,mode:CpuMode,ownerLimit:Int,overrides:CpuOverrides=CpuOverrides()):CpuDecision {
        require(c.cores>0 && ownerLimit in 1..128)
        val overdrive=mode in setOf(CpuMode.OVERDRIVE,CpuMode.OVERDRIVE_PLUS)
        val ignore=if(mode==CpuMode.OVERDRIVE_PLUS) overrides else CpuOverrides()
        val headroom=if(overdrive) 0 else if(c.cores>=4) 2 else if(c.cores>=2) 1 else 0
        var cap=if(overdrive) c.cores.coerceAtMost(128) else minOf(ownerLimit,(c.cores-headroom).coerceAtLeast(1),when(mode){CpuMode.BALANCED->4;CpuMode.BATTERY_SAVER->1;else->6})
        val reasons=mutableListOf("$headroom logical cores of scheduling headroom")
        if(!ignore.background && !c.foreground){cap=minOf(cap,2);reasons+="App in background"}
        if(!ignore.heat && ((c.thermal==null && c.unknownAsPressure) || (c.thermal!=null && c.thermal>=1))){cap=minOf(cap,2);reasons+="Thermal pressure or unknown reading"}
        if(!ignore.heat && c.thermal!=null && c.thermal>=2){cap=1;reasons+="Elevated thermal pressure"}
        if((!ignore.saver && (c.saver==true || c.saver==null && c.unknownAsPressure)) || (!ignore.memory && (c.lowMemory==true || c.lowMemory==null && c.unknownAsPressure)) || (!ignore.battery && (c.battery==null && c.unknownAsPressure || c.battery!=null && c.battery<=15 && c.charging!=true))) {
            cap=1;reasons+="Battery saver, low/unknown resources or low battery"
        }
        if(!c.unknownAsPressure && (c.thermal==null || c.saver==null))reasons+="Some host pressure signals unavailable"
        if(mode==CpuMode.OVERDRIVE_PLUS) reasons+="Overdrive+ CPU overrides: $ignore. Android protections and model admission still apply."
        return CpuDecision(cap,headroom,reasons.joinToString("; "))
    }
}
/** Reductions are immediate; increases wait for a stable recovery window. */
class CpuRecoveryWindow(private val recoveryMs:Long=30_000) {
    private var applied:Int?=null
    private var candidate:Int?=null
    private var since=0L
    @Synchronized fun reset(){applied=null;candidate=null;since=0}
    @Synchronized fun apply(target:Int,now:Long):Int {
        require(target in 1..128 && now>=0)
        val current=applied
        if(current==null || target<=current){applied=target;candidate=null;return target}
        if(candidate!=target){candidate=target;since=now}
        if(now-since>=recoveryMs){applied=target;candidate=null}
        return checkNotNull(applied)
    }
}

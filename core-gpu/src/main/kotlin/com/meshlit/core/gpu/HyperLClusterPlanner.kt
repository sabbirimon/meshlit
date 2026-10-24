package com.meshlit.core.gpu
import kotlinx.serialization.Serializable
@Serializable enum class HyperLClusterMode { STABILITY, TURBO_CANDIDATE }
@Serializable data class HyperLNodeExecutionProfile(val id:String,val osAbi:String,val instructionSet:String,
    val target:HyperLTarget,val runtimeRevision:String,val driverRevision:String,val kernelSha256:String,
    val precision:String,val observedAtMs:Long,val qualified:Boolean=false,val ownerEnabled:Boolean=false,
    val thermalHealthy:Boolean?=null,val powerHealthy:Boolean?=null,val measuredSpeedup:Double?=null,
    val turboAdapterImplemented:Boolean=false) {
    fun compatibilityKey()=listOf(osAbi,instructionSet,target.name,runtimeRevision,driverRevision,kernelSha256,precision)
    fun fresh(now:Long)=observedAtMs>0 && now>=observedAtMs && now-observedAtMs<=30000
}
@Serializable data class HyperLClusterPlan(val mode:HyperLClusterMode,val compatibleIslands:List<List<String>>,val reason:String,
    val executionActivated:Boolean=false)
/** Scheduling evidence only; no clock/voltage modification, root, vendor benchmark guess,
 * native activation or remote job dispatch. A cluster executor must independently admit it. */
object HyperLClusterPlanner {
    fun plan(nodes:List<HyperLNodeExecutionProfile>,turboOwnerOptIn:Boolean,nowMs:Long):HyperLClusterPlan {
        require(nodes.size in 1..10000 && nodes.map{it.id}.distinct().size==nodes.size)
        require(nodes.all{it.id.isNotBlank() && it.osAbi.isNotBlank() && it.instructionSet.isNotBlank() && it.runtimeRevision.isNotBlank() && it.driverRevision.isNotBlank() && it.precision.isNotBlank() && it.kernelSha256.matches(Regex("[a-f0-9]{64}"))})
        val admitted=nodes.filter{it.ownerEnabled && it.qualified && it.fresh(nowMs)}
        val islands=admitted.groupBy{it.compatibilityKey()}.values.map{it.map{it.id}}
        val turbo=turboOwnerOptIn && admitted.size==nodes.size && islands.size==1 && admitted.all{
            it.turboAdapterImplemented && it.thermalHealthy==true && it.powerHealthy==true &&
                it.measuredSpeedup?.let{speed->speed.isFinite() && speed>1.0}==true
        }
        return HyperLClusterPlan(if(turbo)HyperLClusterMode.TURBO_CANDIDATE else HyperLClusterMode.STABILITY,islands,
            if(turbo) "Compatible qualified island with current measured benefit; executor admission still required" else "Mixed, stale, unqualified or unmeasured devices use stability planning; no turbo activation")
    }
}

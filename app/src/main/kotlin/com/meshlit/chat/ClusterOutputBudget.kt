package com.meshlit.chat

import com.meshlit.core.inference.ModelInfo
import kotlinx.serialization.Serializable

@Serializable enum class OutputBudgetMode { MANUAL, AUTOMATIC }
/** Ephemeral evidence from one successful run of this exact loaded cluster session. */
data class ClusterOutputEvidence(val model:ModelInfo,val rate:Double,val measuredAtMs:Long)
data class OutputBudgetDecision(val limit:Int,val explanation:String,val automatic:Boolean=false)

fun clusterOutputBudget(options:ChatOptions,engineTag:String,model:ModelInfo?,evidence:ClusterOutputEvidence?,nowMs:Long):OutputBudgetDecision {
    options.validate()
    val ceiling=options.maxTokens
    fun manual(reason:String)=OutputBudgetDecision(ceiling,reason)
    if(options.outputBudgetMode==OutputBudgetMode.MANUAL) return manual("Manual ceiling: $ceiling tokens")
    if(options.colibriMode!="OFF" || options.nodeTools || options.onlineProfileId!=null || options.routeId!=null || options.webTools || options.phoneTools || options.memoryTools || options.localSearchTools)
        return manual("Automatic cluster sizing unavailable for providers, routes or tool loops; using manual ceiling $ceiling")
    if(engineTag!="llama-rpc-layer" || model==null) return manual("No active layer cluster; using manual ceiling $ceiling")
    if(model.contextSize<=0) return manual("Cluster context is unreported; using manual ceiling $ceiling")
    // The native cluster adapter retains one descriptor instance per load.
    // Equality of path/metadata is insufficient when a host is reloaded.
    if(evidence==null || evidence.model!==model || !evidence.rate.isFinite() || evidence.rate<=0 || nowMs<evidence.measuredAtMs || nowMs-evidence.measuredAtMs>600_000)
        return manual("Run this cluster once to measure its throughput; using manual ceiling $ceiling")
    // A quarter of verified capacity is a conservative output allocation, not remaining-context accounting.
    val limit=minOf(ceiling,(evidence.rate*options.outputTargetSeconds).coerceIn(1.0,2048.0).toInt(),maxOf(1,model.contextSize/4))
    return OutputBudgetDecision(limit,"Automatic: $limit tokens · measured cluster throughput × ${options.outputTargetSeconds}s, capped by your ceiling and ¼ of loaded context",true)
}

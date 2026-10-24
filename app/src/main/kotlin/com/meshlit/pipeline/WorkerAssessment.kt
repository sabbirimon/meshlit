package com.meshlit.pipeline

import com.meshlit.core.inference.pipeline.ClusterNegotiation
import com.meshlit.core.inference.pipeline.NodeOffer

/** Admission risk from a live capability reply. No benchmark or bandwidth is inferred. */
internal fun assessWorker(peer:PipelinePeer,offer:NodeOffer,localId:String,latencyMs:Long):WorkerAssessment {
    val blocked=runCatching { requirePairableWorker(offer,localId) }.exceptionOrNull()?.message
        ?: when {
            offer.backend!="CPU" -> "Unsupported layer backend"
            offer.thermalStatus>=3 -> "Thermal state blocks admission"
            ClusterNegotiation.budget(offer)==0L -> "Insufficient available memory"
            else -> null
        }
    if(blocked!=null) return WorkerAssessment(peer,offer,latencyMs,100,blocked)
    var score=0
    val reasons=mutableListOf<String>()
    val budget=ClusterNegotiation.budget(offer)
    when {
        budget<512L*1024*1024 -> {score+=40;reasons+="low memory headroom"}
        budget<1024L*1024*1024 -> {score+=20;reasons+="limited memory headroom"}
    }
    when(offer.thermalStatus) {
        2 -> {score+=30;reasons+="thermal pressure"}
        1 -> {score+=10;reasons+="warming"}
    }
    when {
        latencyMs>300 -> {score+=30;reasons+="high control latency"}
        latencyMs>100 -> {score+=15;reasons+="elevated control latency"}
    }
    return WorkerAssessment(peer,offer,latencyMs,score.coerceAtMost(99),reasons.joinToString(", ").ifEmpty{"No admission warning"})
}

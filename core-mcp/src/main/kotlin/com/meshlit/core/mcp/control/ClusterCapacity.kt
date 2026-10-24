package com.meshlit.core.mcp.control
import kotlinx.serialization.Serializable
@Serializable data class ClusterCapacity(val deviceLimit:Int=10000, val pipelineWorkerLimit:Int=8,
    val agentMayManageCapacity:Boolean=false, val agentMinimum:Int=1, val agentMaximum:Int=64) {
    fun validate(){ require(deviceLimit in 1..10000 && pipelineWorkerLimit in 2..8 && agentMinimum in 1..10000 && agentMaximum in agentMinimum..10000) }
    fun agentChange(requested:Int):ClusterCapacity { validate(); require(agentMayManageCapacity && requested in agentMinimum..agentMaximum); return copy(deviceLimit=requested).also{it.validate()} }
}

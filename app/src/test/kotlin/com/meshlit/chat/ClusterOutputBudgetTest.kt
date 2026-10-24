package com.meshlit.chat

import com.meshlit.core.inference.ModelInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class ClusterOutputBudgetTest {
    private val model=ModelInfo("/model.gguf","model",4096,0,"Q4",0,100,20)
    private val options=ChatOptions(maxTokens=2048,outputBudgetMode=OutputBudgetMode.AUTOMATIC,outputTargetSeconds=30)
    @Test fun boundsMeasuredWholeClusterRateByDurationCeilingAndContextAllocation() {
        assertEquals(300,clusterOutputBudget(options,"llama-rpc-layer",model,ClusterOutputEvidence(model,10.0,100),200).limit)
        assertEquals(1024,clusterOutputBudget(options,"llama-rpc-layer",model,ClusterOutputEvidence(model,100.0,100),200).limit)
        assertEquals(256,clusterOutputBudget(options.copy(maxTokens=256),"llama-rpc-layer",model,ClusterOutputEvidence(model,100.0,100),200).limit)
        assertEquals(1,clusterOutputBudget(options,"llama-rpc-layer",model,ClusterOutputEvidence(model,0.001,100),200).limit)
    }
    @Test fun staleReplacedUnknownAndInvalidEvidenceNeverChangesManualCeiling() {
        val evidence=ClusterOutputEvidence(model,10.0,100)
        for(bad in listOf(null,evidence.copy(model=model.copy()),evidence.copy(model=model.copy(loadedAtMs=21)),evidence.copy(rate=Double.NaN),evidence.copy(rate=Double.POSITIVE_INFINITY),evidence.copy(rate=0.0),evidence.copy(measuredAtMs=201),evidence.copy(measuredAtMs=-600001))) {
            val result=clusterOutputBudget(options,"llama-rpc-layer",model,bad,200)
            assertEquals(2048,result.limit);assertFalse(result.automatic)
        }
        assertFalse(clusterOutputBudget(options,"llama-rpc-layer",model.copy(contextSize=0),evidence,200).automatic)
        assertFalse(clusterOutputBudget(options,"llama-rpc-layer",null,evidence,200).automatic)
    }
    @Test fun localProviderRouteAndToolUsageCannotBecomeClusterPower() {
        val evidence=ClusterOutputEvidence(model,10.0,100)
        for(tag in listOf("llama-native-local","runanywhere","onnx-ort")) assertFalse(clusterOutputBudget(options,tag,model,evidence,200).automatic)
        for(config in listOf(options.copy(onlineProfileId="provider"),options.copy(routeId="route",maxTokens=1024),options.copy(webTools=true),options.copy(localSearchTools=true)))
            assertFalse(clusterOutputBudget(config,"llama-rpc-layer",model,evidence,200).automatic)
        assertEquals(2048,clusterOutputBudget(options.copy(outputBudgetMode=OutputBudgetMode.MANUAL),"llama-rpc-layer",model,evidence,200).limit)
    }
    @Test fun oldOptionsRetainManualModeAndOutputTargetIsBounded() {
        assertEquals(OutputBudgetMode.MANUAL,Json.decodeFromString<ChatOptions>("{}").outputBudgetMode)
        assertFalse(Json.decodeFromString<ChatOptions>("{}").localSearchTools)
        for(seconds in listOf(4,121)) assertTrue(runCatching{options.copy(outputTargetSeconds=seconds).validate()}.isFailure)
    }
    @Test fun localSearchAloneUsesTheToolLoopAndCannotRunWithProviderOrRoute() {
        assertFalse(ChatOptions().usesLocalTools)
        for(config in listOf(ChatOptions(localSearchTools=true),ChatOptions(webTools=true),ChatOptions(memoryTools=true),ChatOptions(phoneTools=true))) {
            assertTrue(config.usesLocalTools)
            assertTrue(runCatching{config.copy(onlineProfileId="provider").validate()}.isFailure)
            assertTrue(runCatching{config.copy(routeId="route").validate()}.isFailure)
        }
        assertFalse(Json.encodeToString(ChatOptions()).contains("usesLocalTools"))
    }
}

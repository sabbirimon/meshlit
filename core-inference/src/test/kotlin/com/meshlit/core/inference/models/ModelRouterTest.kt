package com.meshlit.core.inference.models
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ModelRouterTest {
    private fun route(id:String="a",mode:ModelRouteMode=ModelRouteMode.SINGLE)=ModelRoute(id,id,"coding",enabled=true,
        mode=mode,steps=if(mode==ModelRouteMode.SINGLE) listOf(ModelRouteStep("local-a")) else listOf(ModelRouteStep("local-a"),ModelRouteStep("cloud:b")))
    @Test fun automaticSelectionUsesScenarioKeywordsPriorityAndStableTieBreak(){
        val a=route();val b=route("b").copy(priority=10,keywords=listOf("kotlin"))
        assertEquals("b",ModelRouter.select(listOf(a,b),null,"coding","Kotlin coroutine bug").id)
        assertEquals("a",ModelRouter.select(listOf(b,a),null,"coding","Python bug").id)
        assertThrows(IllegalStateException::class.java){ModelRouter.select(listOf(a,b),null,"vision","look")}
    }
    @Test fun disabledExplicitRouteNeverFallsBack(){
        assertThrows(IllegalStateException::class.java){ModelRouter.select(listOf(route().copy(enabled=false),route("b")),"a","coding","help")}
    }
    @Test fun allParticipantsPreflightBeforeAnyExecution()=runBlocking {
        var calls=0
        try {
            ModelRouter.execute(route(mode=ModelRouteMode.CHAIN),"task",preflight={if(it.modelId.startsWith("cloud:")) error("disabled")},run={_,_,_->calls++;RouteStepReply("text")})
            fail("Expected preflight failure")
        } catch(_:IllegalStateException) {assertEquals(0,calls)}
    }
    @Test fun chainCarriesPreviousOutputAndCompareKeepsInputsIndependent()=runBlocking {
        for(mode in listOf(ModelRouteMode.CHAIN,ModelRouteMode.COMPARE)) {
            val inputs=mutableListOf<String>()
            val r=ModelRouter.execute(route(mode=mode),"original",preflight={},run={_,input,_->inputs+=input;RouteStepReply("actual-test-reply-${inputs.size}")})
            assertTrue(r.success);assertEquals(2,r.steps.size)
            if(mode==ModelRouteMode.CHAIN) assertTrue(inputs[1].contains("actual-test-reply-1"))
            else {assertEquals(listOf("original","original"),inputs);assertTrue(r.text.contains("Model 2"))}
        }
    }
    @Test fun failedSecondStepRetainsEvidenceWithoutSuccessOrRetry()=runBlocking {
        var calls=0
        val r=ModelRouter.execute(route(mode=ModelRouteMode.CHAIN),"task",preflight={},run={_,_,_->if(++calls==2) error("offline");RouteStepReply("first")})
        assertFalse(r.success);assertEquals(1,r.steps.size);assertEquals(1,r.failedStep);assertEquals(2,calls);assertEquals("",r.text)
    }
    @Test fun cancellationIsNeverConvertedToFailedOrSuccessfulOutput()=runBlocking {
        try {ModelRouter.execute(route(),"task",preflight={},run={_,_,_->throw CancellationException("stop")});fail("Expected cancellation")}
        catch(_:CancellationException) {Unit}
    }
}

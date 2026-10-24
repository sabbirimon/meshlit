package com.meshlit.core.gpu

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class HyperLLibraryTest {
    @Test fun twelveRecipesRunRealCpuAndRoundTrip()=runBlocking {
        val expected=mapOf("add" to floatArrayOf(3f,-1f,4f),"multiply" to floatArrayOf(-2f,6f,12f),"relu" to floatArrayOf(0f,2f,3f),
            "weighted_relu" to floatArrayOf(0f,6f,12f),"residual_relu" to floatArrayOf(0f,1f,3f),"affine" to floatArrayOf(-1f,7f,13f),
            "affine_relu" to floatArrayOf(0f,7f,13f),"residual_affine_relu" to floatArrayOf(0f,6f,13f),"dot" to floatArrayOf(16f),
            "sum" to floatArrayOf(4f),"positive_sum" to floatArrayOf(5f),"squared_norm" to floatArrayOf(14f))
        assertEquals(expected.keys,HyperLLibrary.ids.toSet())
        for(id in HyperLLibrary.ids){val r=HyperLLibrary.recipe(id)
            val p=HyperLCodec.program(HyperLCodec.json.encodeToString(r.program));val input=HyperLCodec.inputs(HyperLCodec.json.encodeToString(r.example))
            assertArrayEquals(id,expected.getValue(id),HyperLRuntime().execute(HyperLTarget.CPU_REFERENCE,p,input),0f)
        }
        HyperLLibrary.recipe("relu").example.getValue("x")[0]=99f
        assertEquals(-1f,HyperLLibrary.recipe("relu").example.getValue("x")[0],0f)
    }
    @Test fun invalidJsonAndCompleteGraphMemoryReject(){
        assertThrows(IllegalArgumentException::class.java){HyperLCodec.program("""{"inputs":["x","x"],"instructions":[{"output":"y","operation":"relu","inputs":["x"]}],"output":"y"}""")}
        assertThrows(IllegalArgumentException::class.java){HyperLCodec.inputs("""{"x":["2"]}""")}
        val r=HyperLLibrary.recipe("residual_affine_relu");val big=r.example.mapValues{FloatArray(262144)}
        val plan=HyperLAdmission.plan(r.program,big,heapLimitBytes=512L*1024*1024,heapUsedBytes=0)
        assertFalse(plan.admitted)
        assertThrows(IllegalArgumentException::class.java){HyperLAdmission.requireAdmission(plan)}
    }
    @Test fun capacityCancellationAndDeadlineReleaseAdmission()=runBlocking {
        // Coordination fixture tests admission; it never qualifies a compute backend.
        val entered=CompletableDeferred<Unit>()
        val fixture=object:HyperLBackend{override val target=HyperLTarget.CPU_REFERENCE;override val runtimeRevision="TEST COORDINATION ONLY"
            override suspend fun execute(program:HyperLProgram,inputs:Map<String,FloatArray>):FloatArray {entered.complete(Unit);awaitCancellation()}}
        val r=HyperLLibrary.recipe("relu");val runtime=HyperLRuntime(listOf(fixture),1,100)
        val running=async{runCatching{runtime.execute(HyperLTarget.CPU_REFERENCE,r.program,r.example)}}
        entered.await()
        try{runtime.execute(HyperLTarget.CPU_REFERENCE,r.program,r.example);fail("Capacity must reject")}catch(e:IllegalStateException){assertTrue(e.message.orEmpty().contains("capacity"))}
        assertTrue(running.await().exceptionOrNull() is TimeoutCancellationException)
        val next=async{runCatching{runtime.execute(HyperLTarget.CPU_REFERENCE,r.program,r.example)}}
        yield();next.cancelAndJoin()
        try{runtime.execute(HyperLTarget.CPU_REFERENCE,r.program,r.example);fail("Deadline required")}catch(_:TimeoutCancellationException){}
    }
    @Test fun realCpuCancellationBudgetAndOverflowReject()=runBlocking {
        val r=HyperLLibrary.recipe("sum")
        try{HyperLCpuBackend().execute(r.program,mapOf("x" to floatArrayOf(Float.MAX_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE)));fail("Ordered overflow required")}catch(e:IllegalArgumentException){assertTrue(e.message.orEmpty().contains("nonfinite"))}
        try{HyperLCpuBackend(1).execute(r.program,r.example);fail("Budget required")}catch(_:IllegalArgumentException){}
        var rejected=false
        val job=launch(start=CoroutineStart.UNDISPATCHED){
            currentCoroutineContext().cancel()
            try{HyperLCpuBackend().execute(r.program,r.example);fail("Cancelled CPU invocation must reject")}
            catch(_:CancellationException){rejected=true}
        };job.join();assertTrue(job.isCancelled);assertTrue(rejected)
    }
}

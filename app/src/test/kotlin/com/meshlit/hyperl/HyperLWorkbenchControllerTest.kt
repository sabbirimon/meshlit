package com.meshlit.hyperl

import com.meshlit.core.common.control.*
import com.meshlit.core.hyperl.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class HyperLWorkbenchControllerTest {
    @Test fun measuredRunsHaveStableOutputsAndRespectBounds()=runBlocking {
        val gate=OperationGate();val controller=HyperLWorkbenchController(gate);val recipe=HyperLLibrary.recipe("weighted_relu")
        val program=HyperLCodec.json.encodeToString(recipe.program);val inputs=HyperLCodec.json.encodeToString(recipe.example)
        val measured=controller.benchmark(program,inputs,16L*1024*1024,HyperLCpuChoice.REFERENCE,3)
        assertEquals(3,measured.samplesMs.size);assertTrue(measured.samplesMs.all{it.isFinite() && it>=0})
        assertEquals(3,measured.outputCount);assertEquals(64,measured.outputSha256.length)
        assertTrue(measured.p95Ms>=measured.medianMs)
        val normal=controller.plan(program,inputs,16L*1024*1024)
        val native=controller.plan(program,inputs,16L*1024*1024,HyperLCpuChoice.NATIVE)
        assertEquals(normal.estimatedBytes+normal.inputBytes+12,native.estimatedBytes)
        try{controller.benchmark(program,inputs,16L*1024*1024,HyperLCpuChoice.REFERENCE,11);fail("Unbounded benchmark")}catch(_:IllegalArgumentException){}
        gate.emergencyStop()
        try{controller.benchmark(program,inputs,16L*1024*1024,HyperLCpuChoice.REFERENCE);fail("Emergency stop")}catch(_:IllegalStateException){}
    }
    @Test fun preciseIsExplicitAndRespectsHumanGate()=runBlocking {
        val gate=OperationGate();val c=HyperLWorkbenchController(gate)
        assertEquals(1f,c.precise("{\"x\":[16777216,1,-16777216]}",16L*1024*1024,HyperLCpuChoice.REFERENCE),0f)
        try{c.precise("{\"x\":[1],\"y\":[2]}",16L*1024*1024,HyperLCpuChoice.REFERENCE);fail("Multiple vectors")}catch(_:IllegalArgumentException){}
        gate.setFeature(ManagedFeature.HYPERL,false)
        try{c.precise("{\"x\":[1]}",16L*1024*1024,HyperLCpuChoice.REFERENCE);fail("Gate")}catch(_:IllegalStateException){}
    }
    @Test fun realCpuRecipeAndSourceBoundaries()=runBlocking {
        val c=HyperLWorkbenchController(OperationGate());val r=HyperLLibrary.recipe("weighted_relu")
        val program=HyperLCodec.json.encodeToString(r.program);val inputs=HyperLCodec.json.encodeToString(r.example)
        val result=c.execute(program,inputs,16L*1024*1024)
        assertArrayEquals(floatArrayOf(0f,6f,12f),result.values,0f);assertTrue(result.plan.admitted);assertEquals("CPU_REFERENCE",result.backend)
        assertTrue(c.emit(program,HyperLTarget.METAL).contains("kernel"))
        try{c.execute(program,inputs,1);fail("Budget must reject")}catch(_:IllegalArgumentException){}
        val sum=HyperLCodec.json.encodeToString(HyperLLibrary.recipe("sum").program)
        try{c.emit(sum,HyperLTarget.METAL);fail("Reduction source unavailable")}catch(_:IllegalArgumentException){}
    }
    @Test fun humanGateBlocksExecutionAndSourceAndResumeWorks()=runBlocking {
        val gate=OperationGate();val c=HyperLWorkbenchController(gate);val r=HyperLLibrary.recipe("relu")
        val program=HyperLCodec.json.encodeToString(r.program);val inputs=HyperLCodec.json.encodeToString(r.example)
        gate.setFeature(ManagedFeature.HYPERL,false)
        try{c.execute(program,inputs,16L*1024*1024);fail("Disabled feature")}catch(_:IllegalStateException){}
        gate.setFeature(ManagedFeature.HYPERL,true);gate.emergencyStop()
        try{c.emit(program,HyperLTarget.METAL);fail("Emergency stop")}catch(_:IllegalStateException){}
        gate.resumeHuman();assertArrayEquals(floatArrayOf(0f,2f,3f),c.execute(program,inputs,16L*1024*1024).values,0f)
    }
    @Test fun malformedAndOversizedEditorsReject(){
        val c=HyperLWorkbenchController(OperationGate());val r=HyperLLibrary.recipe("relu")
        val program=HyperLCodec.json.encodeToString(r.program)
        assertThrows(IllegalArgumentException::class.java){c.plan(program,"{\"x\":[\"3\"]}",16L*1024*1024)}
        assertThrows(IllegalArgumentException::class.java){c.plan(program," ".repeat(65537),16L*1024*1024)}
    }
}

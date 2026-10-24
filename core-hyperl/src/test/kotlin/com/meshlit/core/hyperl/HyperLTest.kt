package com.meshlit.core.hyperl
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class HyperLTest {
    private val program=HyperLProgram(inputs=setOf("x","w"),instructions=listOf(HyperLInstruction("product","multiply",listOf("x","w")),HyperLInstruction("positive","relu",listOf("product")),HyperLInstruction("total","sum",listOf("positive"))),output="total")
    @Test fun cpuRunsRealKernelAndDoesNotModifyInput()=runBlocking {
        val x=floatArrayOf(-1f,2f,3f);val result=HyperLRuntime().execute(HyperLTarget.CPU_REFERENCE,program,mapOf("x" to x,"w" to floatArrayOf(2f,3f,4f)))
        assertArrayEquals(floatArrayOf(18f),result,0f);assertArrayEquals(floatArrayOf(-1f,2f,3f),x,0f)
    }
    @Test fun unsupportedTargetAndVersionCannotSilentlyFallback()=runBlocking {
        assertTrue(runCatching{HyperLRuntime().execute(HyperLTarget.CUDA,program,emptyMap())}.isFailure)
        assertTrue(runCatching{program.copy(format="hyperl/99").validate()}.isFailure)
        assertTrue(runCatching{program.copy(instructions=listOf(HyperLInstruction("x","add",listOf("x","w")))).validate()}.isFailure)
    }
    @Test fun shapeNonfiniteAndMemoryFailuresAreExplicit()=runBlocking {
        val runtime=HyperLRuntime()
        assertTrue(runCatching{runtime.execute(HyperLTarget.CPU_REFERENCE,program,mapOf("x" to floatArrayOf(1f),"w" to floatArrayOf(1f,2f)))}.isFailure)
        assertTrue(runCatching{runtime.execute(HyperLTarget.CPU_REFERENCE,program,mapOf("x" to floatArrayOf(Float.NaN),"w" to floatArrayOf(1f)))}.isFailure)
        val many=(1..8).associate{ "input$it" to FloatArray(262144){1f} }
        val inputBudget=HyperLProgram(inputs=many.keys,instructions=listOf(HyperLInstruction("out","relu",listOf("input1"))),output="out")
        assertTrue(runCatching{runtime.execute(HyperLTarget.CPU_REFERENCE,inputBudget,many)}.isFailure)
        val large=FloatArray(262145){1f};assertTrue(runCatching{runtime.execute(HyperLTarget.CPU_REFERENCE,program,mapOf("x" to large,"w" to large))}.isFailure)
    }
}

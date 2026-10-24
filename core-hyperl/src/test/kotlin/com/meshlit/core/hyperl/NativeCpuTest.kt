package com.meshlit.core.hyperl

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real JNI execution, only when an explicitly built host library is supplied. */
class NativeCpuTest {
    @Test fun sameAbiRecipesPreciseAndFailureContracts()=runBlocking {
        assumeTrue(System.getProperty("hyperl.host.test")=="true")
        for(id in HyperLLibrary.ids){val r=HyperLLibrary.recipe(id)
            assertArrayEquals(id,HyperLCpuBackend().execute(r.program,r.example),NativeCpu.execute(r.program,r.example,16L*1024*1024),0f)
        }
        assertEquals(1f,NativeCpu.precise(floatArrayOf(16777216f,1f,-16777216f),16L*1024*1024),0f)
        try{NativeCpu.precise(floatArrayOf(Float.NaN),16L*1024*1024);fail("Nonfinite")}catch(_:IllegalArgumentException){}
        val r=HyperLLibrary.recipe("relu")
        try{NativeCpu.execute(r.program,r.example,1);fail("Budget")}catch(_:IllegalArgumentException){}
        try{NativeBridge.execute(arrayOf(floatArrayOf(1f)),intArrayOf(99),intArrayOf(0),intArrayOf(0),1,NativeCancellation{false});fail("Opcode")}catch(_:IllegalArgumentException){}
        try{NativeBridge.execute(arrayOf(floatArrayOf(1f)),intArrayOf(3),intArrayOf(1),intArrayOf(0),1,NativeCancellation{false});fail("Dependency")}catch(_:IllegalArgumentException){}
        try{NativeBridge.execute(arrayOf(FloatArray(262144){1f}),intArrayOf(4),intArrayOf(0),intArrayOf(0),1,NativeCancellation{true});fail("Stop")}catch(_:CancellationException){}
        try{NativeBridge.precise(floatArrayOf(1f),NativeCancellation{true});fail("Precise stop")}catch(_:CancellationException){}
    }
}

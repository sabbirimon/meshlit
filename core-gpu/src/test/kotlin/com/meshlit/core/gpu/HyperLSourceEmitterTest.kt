package com.meshlit.core.gpu
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
class HyperLSourceEmitterTest {
    private val program=HyperLProgram(inputs=setOf("x","w"),instructions=listOf(HyperLInstruction("value","multiply",listOf("x","w")),HyperLInstruction("positive","relu",listOf("value"))),output="positive")
    @Test fun generatedSourcesPreserveSafeBindingsAndDoNotClaimDeviceExecution(){
        for(target in listOf(HyperLTarget.LLVM_CPU,HyperLTarget.CUDA,HyperLTarget.ROCM_HIP,HyperLTarget.OPENCL_SPIRV)){
            val source=HyperLSourceEmitter.emit(program,target);assertFalse(source.deviceQualified);assertTrue(source.source.contains("input_0"));assertFalse(source.source.contains("malloc"))
        }
        assertThrows(IllegalArgumentException::class.java){HyperLSourceEmitter.emit(program.copy(instructions=program.instructions+HyperLInstruction("total","sum",listOf("positive")),output="total"),HyperLTarget.CUDA)}
    }
    @Test fun emittedCpuCodeCompilesAndRunsOnActualHostWhenCompilerInstalled(){
        val compiler=File("/usr/bin/clang");assumeTrue("Host Clang unavailable; this is not Android hardware qualification",compiler.canExecute())
        val directory=kotlin.io.path.createTempDirectory("hyperl-cpu-").toFile()
        try{
            val source=File(directory,"kernel.c");val binary=File(directory,"kernel")
            source.writeText(HyperLSourceEmitter.emit(program,HyperLTarget.LLVM_CPU).source+"\nint main(void){float w[3]={2,3,4},x[3]={-1,2,3},out[3]={0};hyperl_kernel(w,x,out,3);if (!(out[0]==0 && out[1]==6 && out[2]==12)) return 1;float huge[1]={-3.402823466e+38f},two[1]={2};hyperl_kernel(two,huge,out,1);return (out[0]<=3.402823466e+38f && out[0]>=-3.402823466e+38f);}\n")
            val compile=ProcessBuilder(compiler.absolutePath,"-std=c99","-O2","-ffp-contract=off",source.absolutePath,"-o",binary.absolutePath).redirectErrorStream(true).redirectOutput(File(directory,"compile.log")).start()
            if(!compile.waitFor(20,TimeUnit.SECONDS)){compile.destroyForcibly();error("Host compiler timeout")};assertEquals(File(directory,"compile.log").readText().take(4096),0,compile.exitValue())
            val run=ProcessBuilder(binary.absolutePath).start();if(!run.waitFor(5,TimeUnit.SECONDS)){run.destroyForcibly();error("Kernel timeout")};assertEquals(0,run.exitValue())
        }finally{directory.deleteRecursively()}
    }
}

package com.meshlit.qualification

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.gpu.*
import com.meshlit.core.inference.*
import com.meshlit.core.inference.models.*
import com.meshlit.hyperl.HyperLWorkbenchController
import com.meshlit.legal.LegalAgreementStore
import com.meshlit.models.ModelLibrary
import com.meshlit.operations.OperationsControl
import com.meshlit.pipeline.PipelineHost
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Real app-UID CPU execution and packaged model engines. No substitute engines,
 * remote provider, synthetic usage counters, or changes to saved operation policy. */
@RunWith(AndroidJUnit4::class)
class DeviceCoreBenchmarkTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun snapshot():JsonObject=buildJsonObject {
        val memory=ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        val process=Debug.MemoryInfo().also{Debug.getMemoryInfo(it)}
        val runtime=Runtime.getRuntime()
        put("appProcessPssKiB",process.totalPss)
        put("javaUsedBytes",runtime.totalMemory()-runtime.freeMemory())
        put("nativeHeapAllocatedBytes",Debug.getNativeHeapAllocatedSize())
        put("deviceAvailableRamBytes",memory.availMem)
        put("lowMemory",memory.lowMemory)
        if(Build.VERSION.SDK_INT>=29) put("thermalStatus",context.getSystemService(PowerManager::class.java).currentThermalStatus)
        context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
            val scale=it.getIntExtra(BatteryManager.EXTRA_SCALE,-1)
            val level=it.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)
            if(scale>0 && level>=0) put("batteryPercent",100.0*level/scale)
            it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Int.MIN_VALUE).takeIf{v->v!=Int.MIN_VALUE}?.let{v->put("batteryTemperatureC",v/10.0)}
            put("plugged",it.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0)
        }
    }
    private fun report(name:String,data:JsonObject) {
        val file=File(context.filesDir,"qualification/$name.json")
        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
        file.writeText(data.toString())
        println("MESHLIT_QUALIFICATION report=qualification/$name.json status=passed")
    }
    private fun metadata()=buildJsonObject {
        put("format","meshlit-device-qualification/1")
        put("package",context.packageName)
        put("model",Build.MODEL);put("androidApi",Build.VERSION.SDK_INT)
        put("androidRelease",Build.VERSION.RELEASE)
        put("abi",Build.SUPPORTED_ABIS.first())
        put("timestampEpochMs",System.currentTimeMillis())
        put("debugBuild",true)
    }
    @Test fun allRecipesAndBoundedAppUidCpuBenchmarks()=runBlocking {
        assertTrue("Owner must accept Terms and Privacy in the app before device tests",LegalAgreementStore(context).accepted())
        val controller=HyperLWorkbenchController(GlobalContext.get().get<OperationsControl>().gate)
        val expected=mapOf(
            "add" to floatArrayOf(3f,-1f,4f),"multiply" to floatArrayOf(-2f,6f,12f),
            "relu" to floatArrayOf(0f,2f,3f),"weighted_relu" to floatArrayOf(0f,6f,12f),
            "residual_relu" to floatArrayOf(0f,1f,3f),"affine" to floatArrayOf(-1f,7f,13f),
            "affine_relu" to floatArrayOf(0f,7f,13f),"residual_affine_relu" to floatArrayOf(0f,6f,13f),
            "dot" to floatArrayOf(16f),"sum" to floatArrayOf(4f),"positive_sum" to floatArrayOf(5f),"squared_norm" to floatArrayOf(14f))
        val verified=mutableListOf<String>()
        HyperLLibrary.ids.forEach{id->
            val recipe=HyperLLibrary.recipe(id)
            val result=controller.execute(HyperLCodec.json.encodeToString(recipe.program),HyperLCodec.json.encodeToString(recipe.example),16L*1024*1024)
            assertEquals("CPU_REFERENCE",result.backend)
            assertArrayEquals("Actual recipe $id",expected.getValue(id),result.values,0f)
            verified+=id
        }
        val samples=mutableListOf<JsonObject>()
        val program=HyperLCodec.json.encodeToString(HyperLLibrary.recipe("weighted_relu").program)
        for(size in listOf(3,257,4096)) {
            // Compact input stays inside the actual mobile editor's 65,536-character bound.
            val inputs="{\"x\":["+List(size){if(it%2==0) "-1" else "2"}.joinToString(",")+"],\"w\":["+List(size){"3"}.joinToString(",")+"]}"
            controller.execute(program,inputs,16L*1024*1024) // one unrecorded warmup for each size
            repeat(5){index->
                val before=snapshot();val result=controller.execute(program,inputs,16L*1024*1024)
                assertEquals(size,result.values.size)
                result.values.forEachIndexed{i,v->assertEquals(if(i%2==0) 0f else 6f,v,0f)}
                samples+=buildJsonObject{put("size",size);put("sample",index);put("parsePlanExecuteWallMs",result.wallMs)
                    put("estimatedArrayBytes",result.plan.estimatedBytes);put("before",before);put("after",snapshot())}
            }
        }
        report("hyperl-cpu",buildJsonObject{put("metadata",metadata());put("backend","CPU_REFERENCE")
            put("verifiedRecipes",JsonArray(verified.map(::JsonPrimitive)));put("warmupsPerSize",1);put("samplesPerSize",5)
            put("samples",JsonArray(samples));put("phoneGpuExecuted",false)})
    }
    @Test fun actualSdkAndOwnedNativeGenerationBenchmarks()=runBlocking {
        assertTrue("Owner must accept Terms and Privacy in the app before device tests",LegalAgreementStore(context).accepted())
        val koin=GlobalContext.get();val library=koin.get<ModelLibrary>();val inference=koin.get<InferenceCoordinator>();val host=koin.get<PipelineHost>()
        withTimeout(180_000){library.ready.await()}
        val model=library.models.value.single{it.bundled && it.installed}
        assertEquals(model.sha256,ModelFiles.sha256(File(model.path)))
        // Let boot finish, then unload before admission. Reloading an already resident
        // model first would test the wrong available-memory state on a 3 GB phone.
        withTimeout(180_000){library.startupStatus.first{it!="Waiting for model library" && !it.startsWith("Loading ")}}
        withTimeout(180_000){inference.state.first{it !is CoordinatorState.Starting && it !is CoordinatorState.Loading}}
        val original=inference.loadedModel()
        val originalEntry=original?.let{loaded->library.models.value.singleOrNull{it.installed && it.path==loaded.modelPath}}
        val loads=mutableListOf<JsonObject>();val samples=mutableListOf<JsonObject>();val cancellation=mutableListOf<JsonObject>()
        try {
            withTimeout(600_000) {
                for(engine in listOf("runanywhere","llama-native-local")) {
                    host.stopAll();inference.unloadModel()
                    val plan=library.devicePlan()
                    check(plan.allowHeavyWork){"New load blocked by actual device admission: ${plan.memoryBudget} available budget bytes"}
                    val before=snapshot();val loadStart=System.nanoTime()
                    if(engine=="runanywhere") {
                        inference.runAnywhereEngine().initialize(context)
                        val load=inference.loadModel(model.path,contextSize=512)
                        assertTrue("Real SDK load failed: $load",load is MeshlitResult.Success)
                    } else {
                        assertTrue("Owned native runtime is not packaged for this ABI",host.available())
                        host.startLocal(model.path,ModelRuntimeOptions(LocalModelBackend.NATIVE_LOCAL,512,"q8_0"))
                    }
                    assertEquals(engine,inference.engineTag)
                    println("MESHLIT_BENCHMARK_PHASE engine=$engine loaded=true")
                    loads+=buildJsonObject{put("engine",engine);put("loadWallMs",(System.nanoTime()-loadStart)/1_000_000.0)
                        put("contextRequested",512);put("before",before);put("after",snapshot())}
                    suspend fun generate(sample:Int):JsonObject {
                        val first=AtomicLong(0);val chunks=AtomicLong(0);val start=System.nanoTime();val memory=snapshot()
                        val result=inference.infer(InferenceRequest("Say hello.",maxTokens=16,temperature=0f,seed=42,
                            expectedModelPath=model.path,onDeviceOnly=true,publishEvents=false,onToken={text->if(text.isNotEmpty()){first.compareAndSet(0,System.nanoTime());chunks.incrementAndGet()}}))
                        val wall=(System.nanoTime()-start)/1_000_000.0
                        assertTrue("Real $engine inference failed: $result",result is MeshlitResult.Success)
                        val value=(result as MeshlitResult.Success).value
                        assertTrue("Real output is empty",value.finalText.isNotBlank())
                        assertTrue("Native usage outside generation bound",value.generatedTokens==null || value.generatedTokens!! in 1..16)
                        return buildJsonObject{put("engine",engine);put("sample",sample);put("requestWallMs",wall)
                            put("firstTextCallbackMs",first.get().takeIf{it>0}?.let{(it-start)/1_000_000.0}?.let(::JsonPrimitive) ?: JsonNull)
                            put("textCallbackCount",chunks.get());put("generatedTokens",value.generatedTokens?.let(::JsonPrimitive) ?: JsonNull)
                            put("engineTokensPerSecond",value.tokensPerSecond?.let(::JsonPrimitive) ?: JsonNull)
                            put("engineDurationMs",value.totalDurationMs);put("finishReason",value.finishReason.tag)
                            put("outputCharacters",value.finalText.length);put("before",memory);put("after",snapshot())}
                    }
                    generate(-1) // one warmup excluded from recorded samples
                    repeat(3){samples+=generate(it);println("MESHLIT_BENCHMARK_PHASE engine=$engine sample=$it completed=true")}
                    // Cancellation after a real text callback, followed by a real recovery request.
                    val first=CompletableDeferred<Unit>()
                    val work=async {inference.infer(InferenceRequest("Write a detailed story about a small boat.",maxTokens=256,temperature=0f,
                        onDeviceOnly=true,publishEvents=false,expectedModelPath=model.path,onToken={if(it.isNotEmpty()) first.complete(Unit)}))}
                    withTimeout(90_000){first.await()}
                    val cancelStart=System.nanoTime();inference.cancel()
                    val outcome=withTimeout(10_000){try{work.await()}catch(cancelled:CancellationException){if(!work.isCancelled) throw cancelled;null}}
                    assertTrue("Cancel returned successful completion",outcome==null || outcome is MeshlitResult.Success && outcome.value.finishReason==FinishReason.CANCELLED)
                    cancellation+=buildJsonObject{put("engine",engine);put("cancelToJoinMs",(System.nanoTime()-cancelStart)/1_000_000.0)
                        put("actualTextObserved",true);put("recoveryRequest",generate(3))}
                }
            }
        } finally {
            withContext(NonCancellable){host.stopAll();inference.unloadModel()
                // Restore the owner's actual original library entry. A failed restore
                // must fail instrumentation before a successful report is written.
                if(originalEntry!=null) withTimeout(180_000){library.load(originalEntry.id)}
            }
        }
        report("local-engines",buildJsonObject{put("metadata",metadata());put("modelSha256",model.sha256)
            put("modelBytes",model.sizeBytes);put("maxGeneratedTokens",16);put("warmupsPerEngine",1);put("samplesPerEngine",3)
            put("loads",JsonArray(loads));put("samples",JsonArray(samples));put("cancellation",JsonArray(cancellation))
            put("pssScope","App process only; owned native child process is excluded")
            put("gpuInferenceQualified",false);put("onDeviceOnly",true);put("chatEventsPublished",false)})
    }
}

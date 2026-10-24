package com.meshlit.models
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.inference.*
import com.meshlit.core.inference.models.ModelFiles
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File

/** Requires a real operator-supplied GGUF copied with adb run-as. Skips when
 * absent; a skipped test never counts as model-execution evidence. */
@RunWith(AndroidJUnit4::class)
class InstalledModelSmokeTest {
    @Test fun configuredRouterRunsTwoRealLocalModels()=runBlocking {
        val library=GlobalContext.get().get<ModelLibrary>();library.ready.await()
        val starter=library.models.value.single{it.bundled && it.installed}
        val tiny=library.models.value.firstOrNull{it.installed && File(it.path).name=="smoke-test.gguf"}
        assumeTrue("Requires the real operator-supplied TinyStories GGUF",tiny!=null)
        assertEquals("66967fbece6dbe97886593fdbb73589584927e29119ec31f08090732d1861739",ModelFiles.sha256(File(tiny!!.path)))
        val router=GlobalContext.get().get<com.meshlit.routing.ModelRoutes>()
        val recipe=com.meshlit.core.inference.models.ModelRoute("instrumented-two-models","Real local chain",enabled=true,
            mode=com.meshlit.core.inference.models.ModelRouteMode.CHAIN,steps=listOf(
                com.meshlit.core.inference.models.ModelRouteStep(tiny.id),
                com.meshlit.core.inference.models.ModelRouteStep(starter.id,"Summarize the previous model output in one sentence.")))
        router.save(recipe)
        try {
            val result=withTimeout(240000){router.execute(recipe.id,"general","Once upon a time",16)}
            assertTrue("Real routed generation failed: ${result.error}",result.success)
            assertEquals(2,result.steps.size);assertNotEquals(result.steps[0].modelId,result.steps[1].modelId)
            assertTrue(result.steps.all{it.reply.text.isNotBlank()})
            println("MESHLIT_REAL_MODEL_CHAIN models=${result.steps.map{it.modelId}} output=${result.text}")
        } finally {router.remove(recipe.id);GlobalContext.get().get<InferenceCoordinator>().unloadModel()}
    }
    @Test fun bundledModelLoadsAndGeneratesRealTokens()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val library=GlobalContext.get().get<ModelLibrary>()
        withTimeout(180000) {
            println("MESHLIT_BUNDLED_PHASE awaiting-library")
            library.ready.await()
            println("MESHLIT_BUNDLED_PHASE library-ready")
            val model=library.models.value.single{it.bundled}
            assertTrue("Bundled install failed: ${model.error}",model.installed)
            val manifest=BundledModelInstaller().manifest(context)
            assertEquals(manifest.sha256,ModelFiles.sha256(File(model.path)))
            // Wait for any startup load to finish through the shared library lock.
            println("MESHLIT_BUNDLED_PHASE checksum-verified")
            library.load(model.id)
            println("MESHLIT_BUNDLED_PHASE model-loaded")
            val coordinator=GlobalContext.get().get<InferenceCoordinator>()
            assertEquals(model.path,coordinator.loadedModel()?.modelPath)
            val output=StringBuilder()
            val result=coordinator.infer(InferenceRequest(prompt="Say hello in one short sentence.",maxTokens=16,
                temperature=0f,seed=42,onToken={output.append(it)}))
            assertTrue("Real bundled generation failed: $result",result is MeshlitResult.Success)
            assertTrue("Bundled generation produced no text",output.isNotBlank())
            val usage=(result as MeshlitResult.Success).value
            assertTrue("SDK exceeded its native generation bound",usage.generatedTokens==null || usage.generatedTokens!! in 1..16)
            println("MESHLIT_REAL_BUNDLED_MODEL sha256=${manifest.sha256} promptTokens=${usage.promptTokens} generatedTokens=${usage.generatedTokens} tokensPerSecond=${usage.tokensPerSecond} output=$output")
            coordinator.unloadModel();assertNull(coordinator.loadedModel())
        }
    }
    @Test fun managedGgufLoadsGeneratesAndUnloads()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val model=File(context.filesDir,"imported-models/smoke-test.gguf")
        assumeTrue("Copy a real smoke-test.gguf into managed storage before running",model.isFile)
        ModelFiles.validateGguf(model)
        val expected=InstrumentationRegistry.getArguments().getString("model_sha256")
        if(expected!=null) assertEquals(expected,ModelFiles.sha256(model))
        val coordinator=GlobalContext.get().get<InferenceCoordinator>()
        withTimeout(180000) {
            coordinator.runAnywhereEngine().initialize(context)
            assertTrue("SDK initialization failed",coordinator.runAnywhereEngine().isInitialized())
            val loaded=coordinator.loadModel(model.absolutePath,contextSize=512)
            assertTrue("Actual SDK load failed: $loaded",loaded is MeshlitResult.Success)
            val output=StringBuilder()
            val result=coordinator.infer(InferenceRequest(prompt="Once upon a time",maxTokens=16,temperature=0f,seed=42,
                onToken={output.append(it)}))
            assertTrue("Actual generation failed: $result",result is MeshlitResult.Success)
            assertTrue("Native generation produced no text",output.isNotBlank())
            coordinator.unloadModel();assertNull(coordinator.loadedModel())
        }
    }
}

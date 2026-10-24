package com.meshlit.core.inference
import com.meshlit.core.common.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
/** Test-only failing backend: verify dispatch boundaries, never fabricate generated text. */
class OnDeviceDispatchTest {
    private class RecordingEngine(override val engineTag:String):InferenceEngine {
        var calls=0;private var model:ModelInfo?=null
        override fun isReady()=model!=null
        override fun loadedModel()=model
        override suspend fun loadModel(request:ModelLoadRequest):MeshlitResult<ModelInfo>{model=ModelInfo(request.modelPath,"test",512,1,"Q4",1,24,0);return MeshlitResult.Success(model!!)}
        override suspend fun unloadModel(){model=null}
        override suspend fun infer(request:InferenceRequest):MeshlitResult<InferenceResult>{calls++;return MeshlitResult.Failure(MeshlitError.Invalid("test_backend_does_not_generate"))}
    }
    @Test fun browserContentNeverDispatchesToNetworkClusterOrUnknownEngine()=runBlocking<Unit> {
        for(tag in listOf("llama-rpc-layer","remote-http","unknown-plugin")) {
            val engine=RecordingEngine(tag);val coordinator=InferenceCoordinator()
            coordinator.loadExternalEngine("test.gguf",512){engine}
            val result=coordinator.infer(InferenceRequest("private page",onToken={},onDeviceOnly=true)) as MeshlitResult.Failure
            assertEquals("coord.inference.on_device_required",result.error.tag);assertEquals(0,engine.calls)
            coordinator.unloadModel()
        }
    }
    @Test fun explicitlyLocalNativeEngineReceivesRequest()=runBlocking<Unit> {
        val engine=RecordingEngine("llama-native-local");val coordinator=InferenceCoordinator()
        coordinator.loadExternalEngine("test.gguf",512){engine}
        val events=mutableListOf<InferenceEvent>();val listener=launch(start=CoroutineStart.UNDISPATCHED){coordinator.events.collect{events+=it}}
        val result=coordinator.infer(InferenceRequest("private page",onToken={},onDeviceOnly=true,publishEvents=false)) as MeshlitResult.Failure
        assertEquals("test_backend_does_not_generate",result.error.tag);assertEquals(1,engine.calls)
        yield();assertTrue(events.isEmpty())
        coordinator.infer(InferenceRequest("normal chat",onToken={}))
        withTimeout(2000){while(events.size<2)yield()}
        assertEquals("normal chat",(events.first() as InferenceEvent.GenerationStarted).prompt)
        assertTrue(events.last() is InferenceEvent.GenerationFinished)
        listener.cancelAndJoin();coordinator.unloadModel()
    }
}

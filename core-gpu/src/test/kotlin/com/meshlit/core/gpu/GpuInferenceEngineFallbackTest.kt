package com.meshlit.core.gpu

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.inference.BackendHints
import com.meshlit.core.inference.FinishReason
import com.meshlit.core.inference.GpuBackend as InferenceGpuBackend
import com.meshlit.core.inference.InferenceEngine
import com.meshlit.core.inference.InferenceRequest
import com.meshlit.core.inference.InferenceResult
import com.meshlit.core.inference.ModelInfo
import com.meshlit.core.inference.ModelLoadRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuInferenceEngineFallbackTest {
    @Test
    fun `falls back to CPU when no Vulkan detected`() = runBlocking {
        val recorder = RecordingEngine()
        val wrapper = GpuInferenceEngine(recorder, probeProvider = { GpuProbe.None })

        val result = wrapper.loadModel(ModelLoadRequest(modelPath = "/tmp/model.gguf"))

        assertTrue(result is MeshlitResult.Success)
        assertEquals(0, recorder.lastRequest!!.backendHints.gpuLayers)
        assertEquals(InferenceGpuBackend.NONE, recorder.lastRequest!!.backendHints.gpuBackend)
    }

    @Test
    fun `upgrades hints to Vulkan when probe says so`() = runBlocking {
        val recorder = RecordingEngine()
        val probe = GpuProbe(backend = GpuBackend.VULKAN, devices = emptyList())
        val wrapper = GpuInferenceEngine(recorder, probeProvider = { probe })

        wrapper.loadModel(ModelLoadRequest(modelPath = "/tmp/model.gguf"))

        assertEquals(InferenceGpuBackend.VULKAN, recorder.lastRequest!!.backendHints.gpuBackend)
    }

    @Test
    fun `infer delegates to inner engine unchanged`() = runBlocking {
        val recorder = RecordingEngine()
        val wrapper = GpuInferenceEngine(recorder, probeProvider = { GpuProbe.None })
        val request = InferenceRequest(prompt = "hi", onToken = {})

        val result = wrapper.infer(request)

        assertTrue(result is MeshlitResult.Success)
        assertEquals(1, recorder.inferCalls)
    }

    @Test
    fun `engineTag is inner tag plus gpu marker`() {
        val recorder = RecordingEngine()
        val wrapper = GpuInferenceEngine(recorder, probeProvider = { GpuProbe.None })

        assertTrue(wrapper.engineTag.endsWith("+gpu"))
        assertEquals(recorder.engineTag + "+gpu", wrapper.engineTag)
    }

    @Test
    fun `probe snapshot is read on every loadModel call`() = runBlocking {
        val recorder = RecordingEngine()
        var snapshot = GpuProbe.None
        val wrapper = GpuInferenceEngine(recorder) { snapshot }

        wrapper.loadModel(ModelLoadRequest(modelPath = "/tmp/model.gguf"))
        val firstHints = recorder.lastRequest!!.backendHints
        snapshot = GpuProbe(backend = GpuBackend.VULKAN, devices = emptyList())
        wrapper.loadModel(ModelLoadRequest(modelPath = "/tmp/model.gguf"))
        val secondHints = recorder.lastRequest!!.backendHints

        assertEquals(InferenceGpuBackend.NONE, firstHints.gpuBackend)
        assertEquals(InferenceGpuBackend.VULKAN, secondHints.gpuBackend)
    }

    @Test
    fun `loadModel failure propagates`() = runBlocking {
        val failing = object : RecordingEngine() {
            override suspend fun loadModel(request: ModelLoadRequest): MeshlitResult<ModelInfo> =
                MeshlitResult.Failure(com.meshlit.core.common.MeshlitError.Native("boom"))
        }
        val wrapper = GpuInferenceEngine(failing, probeProvider = { GpuProbe.None })

        val result = wrapper.loadModel(ModelLoadRequest(modelPath = "/tmp/model.gguf"))

        assertTrue(result is MeshlitResult.Failure)
    }

    @Test
    fun `unloadModel delegates to inner engine`() = runBlocking {
        val recorder = RecordingEngine()
        val wrapper = GpuInferenceEngine(recorder, probeProvider = { GpuProbe.None })

        wrapper.unloadModel()

        assertEquals(1, recorder.unloadCalls)
    }

    @Test
    fun `loadedModel delegates to inner engine`() {
        val recorder = RecordingEngine()
        val info = ModelInfo(
            modelPath = "/tmp/m.gguf",
            modelName = "m",
            contextSize = 4096,
            parameterCount = 1_000_000_000L,
            quantization = "Q4_K_M",
            embeddingDim = 4096,
            sizeBytes = 1_000_000_000L,
            loadedAtMs = 0L,
        )
        recorder.loadedModel = info
        val wrapper = GpuInferenceEngine(recorder, probeProvider = { GpuProbe.None })

        assertSame(info, wrapper.loadedModel())
    }
}

open class RecordingEngine : InferenceEngine {
    var lastRequest: ModelLoadRequest? = null
    var inferCalls: Int = 0
    var unloadCalls: Int = 0
    var loadedModel: ModelInfo? = null

    override val engineTag: String = "recording"

    override fun isReady(): Boolean = true

    override suspend fun loadModel(request: ModelLoadRequest): MeshlitResult<ModelInfo> {
        lastRequest = request
        return MeshlitResult.Success(
            ModelInfo(
                modelPath = request.modelPath,
                modelName = "stub",
                contextSize = request.contextSize,
                parameterCount = 0L,
                quantization = "stub",
                embeddingDim = 0,
                sizeBytes = 0L,
                loadedAtMs = 0L,
            ),
        )
    }

    override suspend fun unloadModel() {
        unloadCalls++
    }

    override fun loadedModel(): ModelInfo? = loadedModel

    override suspend fun infer(request: InferenceRequest): MeshlitResult<InferenceResult> {
        inferCalls++
        return MeshlitResult.Success(
            InferenceResult(
                promptTokens = 0,
                generatedTokens = 0,
                totalDurationMs = 0,
                tokensPerSecond = 0f,
                finishReason = FinishReason.NATURAL_STOP,
                finalText = "",
            ),
        )
    }
}
package com.meshlit.core.inference

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunAnywherePipelineAdmissionTest {
    @Test fun `catalog and file slices are rejected before any SDK loading`() = runBlocking {
        val engine = RunAnywhereInferenceEngine()
        for (path in listOf("runanywhere:test-model", "/missing/test-model.gguf")) {
            for (request in listOf(
                ModelLoadRequest(path, layerStart = 1),
                ModelLoadRequest(path, layerEnd = 4),
            )) {
                val result = engine.loadModel(request)
                assertTrue(result is MeshlitResult.Failure)
                val error = (result as MeshlitResult.Failure).error
                assertTrue(error is MeshlitError.Invalid)
                assertTrue(error.tag.startsWith("runanywhere.pipeline_unsupported:"))
                assertFalse(engine.isReady())
                assertNull(engine.loadedModel())
            }
        }
    }
}

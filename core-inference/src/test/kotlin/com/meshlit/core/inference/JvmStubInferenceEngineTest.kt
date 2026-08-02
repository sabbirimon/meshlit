package com.meshlit.core.inference

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JvmStubInferenceEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `stub produces a unique non-echo reply`() = runBlocking {
        val file = tmp.newFile("fake-model.gguf")
        file.writeBytes(ByteArray(1024))
        val engine = JvmStubInferenceEngine()
        val load = engine.loadModel(ModelLoadRequest(modelPath = file.absolutePath))
        assertTrue("stub should load", load is com.meshlit.core.common.MeshlitResult.Success)

        val prompt = "Why is the sky blue?"
        var streamed = ""
        val result = engine.infer(
            InferenceRequest(
                prompt = prompt,
                maxTokens = 64,
                onToken = { token -> streamed += token },
                onComplete = { /* not used here */ },
            ),
        )
        assertTrue(result is com.meshlit.core.common.MeshlitResult.Success)
        val final = (result as com.meshlit.core.common.MeshlitResult.Success).value
        // The stub must not echo the prompt verbatim. We assert:
        //   1. final text differs from the prompt
        //   2. final text contains the synthesized "(stub)" prefix
        //   3. the streamed text was populated by the onToken callback
        assertNotEquals("final must differ from prompt", prompt, final.finalText)
        assertTrue(
            "final should look like a stub reply: ${final.finalText}",
            final.finalText.startsWith("(stub)"),
        )
        assertTrue("onToken should have fired", streamed.isNotEmpty())
    }
}

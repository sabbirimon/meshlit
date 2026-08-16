package com.meshlit.core.advanced.engines

import kotlinx.serialization.Serializable

// STUB: real impl pending Silero VAD ONNX model.

@Serializable
data class VadFrame(val audioPath: String, val windowMs: Int = 30)

@Serializable
data class VadResult(val isSpeech: Boolean, val probability: Float)

class SileroVadEngine : StubEngine<VadFrame, VadResult>(
    id = "silero_vad",
    category = EngineCategory.VAD,
) {
    override fun placeholderFor(request: VadFrame): VadResult =
        VadResult(isSpeech = false, probability = 0.05f)
}

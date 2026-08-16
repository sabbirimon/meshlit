package com.meshlit.core.advanced.engines

import kotlinx.serialization.Serializable

// STUB: real impl pending Kokoro ONNX model + sherpa-onnx runtime.

@Serializable
data class KokoroRequest(
    val text: String,
    val voice: String = "af_sarah",
    val speed: Float = 1.0f,
)

@Serializable
data class KokoroResponse(val audioPath: String, val durationMs: Long)

class KokoroEngine : StubEngine<KokoroRequest, KokoroResponse>(
    id = "kokoro_en",
    category = EngineCategory.TTS,
) {
    override fun placeholderFor(request: KokoroRequest): KokoroResponse =
        KokoroResponse(
            audioPath = "[stub] synthesized ${request.text.length} chars via ${request.voice}",
            durationMs = request.text.length * 60L,
        )
}

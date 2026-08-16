package com.meshlit.core.advanced.engines

import kotlinx.serialization.Serializable

// STUB: real impl pending whisper.cpp JNI + bundled GGUF model.

@Serializable
data class WhisperRequest(val audioPath: String, val language: String? = null)

@Serializable
data class WhisperResponse(val text: String, val language: String, val confidence: Float)

class WhisperEngine : StubEngine<WhisperRequest, WhisperResponse>(
    id = "whisper_base",
    category = EngineCategory.STT,
) {
    override fun placeholderFor(request: WhisperRequest): WhisperResponse =
        WhisperResponse(
            text = "[stub] transcribed audio from ${request.audioPath}",
            language = request.language ?: "en",
            confidence = 0.0f,
        )
}

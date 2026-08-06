package com.meshlit.core.advanced.engines

/**
 * The eight engine categories the Advanced hub exposes. New categories
 * should be appended (not reordered) so persisted state survives.
 *
 * The category drives both which UI section the engine lives under and
 * which YAML [SolutionRunner] stages can reference it.
 */
enum class EngineCategory(val displayName: String) {
    LLM("Language model"),
    STT("Speech to text"),
    TTS("Text to speech"),
    VAD("Voice activity"),
    EMBED("Embeddings"),
    OCR("Document OCR"),
    VISION("Vision / VLM"),
    DIARIZATION("Speaker diarization"),
    IMAGE_GEN("Image generation"),
}

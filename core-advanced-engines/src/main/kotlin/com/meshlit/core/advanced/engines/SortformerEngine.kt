package com.meshlit.core.advanced.engines

import kotlinx.serialization.Serializable

// STUB: real impl pending NVIDIA Sortformer ONNX model.

@Serializable
data class DiarizationRequest(val audioPath: String, val speakerHint: Int? = null)

@Serializable
data class DiarizationSegment(val speakerId: Int, val startMs: Long, val endMs: Long)

@Serializable
data class DiarizationResponse(val segments: List<DiarizationSegment>)

class SortformerEngine : StubEngine<DiarizationRequest, DiarizationResponse>(
    id = "sortformer",
    category = EngineCategory.DIARIZATION,
) {
    override fun placeholderFor(request: DiarizationRequest): DiarizationResponse =
        DiarizationResponse(
            segments = listOf(
                DiarizationSegment(speakerId = 0, startMs = 0, endMs = 1500),
                DiarizationSegment(speakerId = 1, startMs = 1500, endMs = 3000),
            ),
        )
}

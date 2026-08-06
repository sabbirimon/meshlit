package com.meshlit.core.advanced.engines

import kotlinx.serialization.Serializable

// STUB: real impl pending SegFormer ONNX model.

@Serializable
data class SegmentationRequest(val imagePath: String, val classes: List<String> = emptyList())

@Serializable
data class SegmentationMask(val className: String, val pixelCount: Int)

@Serializable
data class SegmentationResponse(val masks: List<SegmentationMask>)

class SegFormerEngine : StubEngine<SegmentationRequest, SegmentationResponse>(
    id = "segformer",
    category = EngineCategory.VISION,
) {
    override fun placeholderFor(request: SegmentationRequest): SegmentationResponse =
        SegmentationResponse(
            masks = listOf(
                SegmentationMask("background", 800_000),
                SegmentationMask("object", 200_000),
            ),
        )
}

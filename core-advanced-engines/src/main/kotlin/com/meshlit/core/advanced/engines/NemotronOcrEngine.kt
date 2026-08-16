package com.meshlit.core.advanced.engines

import kotlinx.serialization.Serializable

// STUB: real impl pending Nemotron OCR ONNX model.

@Serializable
data class OcrRequest(val imagePath: String, val language: String = "en")

@Serializable
data class OcrResponse(val text: String, val pageCount: Int)

class NemotronOcrEngine : StubEngine<OcrRequest, OcrResponse>(
    id = "nemotron_ocr",
    category = EngineCategory.OCR,
) {
    override fun placeholderFor(request: OcrRequest): OcrResponse =
        OcrResponse(text = "[stub] OCR extracted text from ${request.imagePath}", pageCount = 1)
}

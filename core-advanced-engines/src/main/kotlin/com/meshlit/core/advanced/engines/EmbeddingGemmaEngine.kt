package com.meshlit.core.advanced.engines

import kotlinx.serialization.Serializable

// STUB: real impl pending EmbeddingGemma ONNX model.

@Serializable
data class EmbeddingRequest(val text: String)

@Serializable
data class EmbeddingResponse(val vector: FloatArray, val dim: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingResponse) return false
        return dim == other.dim && vector.contentEquals(other.vector)
    }
    override fun hashCode(): Int = vector.contentHashCode() * 31 + dim
}

class EmbeddingGemmaEngine : StubEngine<EmbeddingRequest, EmbeddingResponse>(
    id = "embeddinggemma_300m",
    category = EngineCategory.EMBED,
) {
    override fun placeholderFor(request: EmbeddingRequest): EmbeddingResponse =
        EmbeddingResponse(
            vector = FloatArray(8) { (request.text.hashCode() shr it).toFloat() / 1000f },
            dim = 8,
        )
}

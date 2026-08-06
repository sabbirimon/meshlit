package com.meshlit.core.advanced.engines

import kotlinx.serialization.Serializable

// STUB: real impl pending Cosmos3-Edge diffusion model.

@Serializable
data class ImageGenRequest(val prompt: String, val width: Int = 512, val height: Int = 512)

@Serializable
data class ImageGenResponse(val imagePath: String, val seed: Long)

class CosmosEngine : StubEngine<ImageGenRequest, ImageGenResponse>(
    id = "cosmos3_edge",
    category = EngineCategory.IMAGE_GEN,
) {
    override fun placeholderFor(request: ImageGenRequest): ImageGenResponse =
        ImageGenResponse(
            imagePath = "[stub] generated ${request.width}x${request.height} from \"${request.prompt.take(40)}\"",
            seed = request.prompt.hashCode().toLong(),
        )
}

package com.meshlit.core.cloudmcp.llm

/**
 * NaraRouter model slugs. NaraRouter (`https://router.bynara.id/`)
 * is an OpenAI-compatible AI gateway exposing 34+ models. The
 * slugs below are the most-used frontier picks; the full catalog
 * is discoverable through the NaraRouter dashboard.
 *
 * Format: `<provider>/<model-name>` — mirrors the upstream
 * NaraRouter API convention so we don't have to translate slugs.
 *
 * The default model for the Agent Terminal is
 * [Default]; the user can switch at runtime.
 */
enum class NaraRouterModel(val slug: String) {
    Claude45Sonnet("anthropic/claude-4.5-sonnet"),
    DeepSeekV4Flash("deepseek/deepseek-v4-flash"),
    Qwen37Max("qwen/qwen-3.7-max"),
    MistralLarge("mistral/mistral-large"),
    Llama3_70B("meta/llama-3-70b-instruct");

    companion object {
        /** Default model for the Agent Terminal. */
        val Default: NaraRouterModel = DeepSeekV4Flash
    }
}
package com.meshlit.core.cloudmcp.llm

/**
 * OpenAI-compatible model slug presets. The user can paste any
 * free-form slug in addition to these — the list is just the
 * most-used picks that the form dropdown surfaces by default.
 *
 * Format: `<provider>/<model-name>` for hosted gateways
 * (OpenRouter / NaraRouter), or `<model-name>` for self-hosted
 * endpoints (Ollama / LM Studio / vLLM, which don't namespace).
 *
 * [Default] is the slug used when no model has been chosen yet —
 * matches the existing NaraRouter default so existing installs
 * keep working unchanged.
 */
enum class OpenAiCompatibleModel(val slug: String, val displayName: String, val endpointHint: String? = null) {
    // NaraRouter (default)
    DeepSeekV4Flash("nara/deepseek-v4-flash", "DeepSeek V4 Flash (NaraRouter)"),
    Claude45Sonnet("anthropic/claude-4.5-sonnet", "Claude 4.5 Sonnet"),
    Qwen37Max("qwen/qwen-3.7-max", "Qwen 3.7 Max"),
    MistralLarge("mistral/mistral-large", "Mistral Large"),
    Llama3_70B("meta/llama-3-70b-instruct", "Llama 3 70B"),

    // OpenRouter
    OpenRouterClaudeSonnet("anthropic/claude-4.5-sonnet", "Claude 4.5 Sonnet (OpenRouter)",
        endpointHint = "https://openrouter.ai/api"),
    OpenRouterGpt4o("openai/gpt-4o", "GPT-4o (OpenRouter)",
        endpointHint = "https://openrouter.ai/api"),
    OpenRouterLlama("meta-llama/llama-3.1-70b-instruct", "Llama 3.1 70B (OpenRouter)",
        endpointHint = "https://openrouter.ai/api"),

    // Together
    TogetherLlama("meta-llama/Llama-3-70b-chat-hf", "Llama 3 70B (Together)",
        endpointHint = "https://api.together.xyz"),
    TogetherMixtral("mistralai/Mixtral-8x7B-Instruct-v0.1", "Mixtral 8x7B (Together)",
        endpointHint = "https://api.together.xyz"),

    // Groq
    GroqLlama("llama3-70b-8192", "Llama 3 70B (Groq)",
        endpointHint = "https://api.groq.com/openai"),
    GroqMixtral("mixtral-8x7b-32768", "Mixtral 8x7B (Groq)",
        endpointHint = "https://api.groq.com/openai"),

    // Ollama (self-hosted; default local URL)
    OllamaLlama3("llama3", "Llama 3 (Ollama)",
        endpointHint = "http://10.0.2.2:11434"),
    OllamaMistral("mistral", "Mistral (Ollama)",
        endpointHint = "http://10.0.2.2:11434"),

    // LM Studio (self-hosted)
    LmStudioDefault("local-model", "Local model (LM Studio)",
        endpointHint = "http://10.0.2.2:1234"),

    // vLLM (self-hosted)
    VllmDefault("vllm-model", "Local model (vLLM)",
        endpointHint = "http://10.0.2.2:8000");

    companion object {
        /** Default slug for the Agent Terminal. Matches the
         *  existing NaraRouter default so existing installs keep
         *  working unchanged. */
        val Default: OpenAiCompatibleModel = DeepSeekV4Flash

        /**
         * Default base URL — NaraRouter. Matches the existing
         * NaraRouterClient default so existing installs keep
         * working unchanged.
         */
        const val DEFAULT_BASE_URL: String = "https://router.bynara.id"

        /** ProviderId used in CloudCredentialStore. */
        const val DEFAULT_PROVIDER_ID: String = "user-llm"
    }
}

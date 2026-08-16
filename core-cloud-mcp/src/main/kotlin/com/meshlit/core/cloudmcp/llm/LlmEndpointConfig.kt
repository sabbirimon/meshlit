package com.meshlit.core.cloudmcp.llm

/**
 * User's active LLM endpoint configuration. The agent loop
 * reads this triple on every prompt and constructs an
 * [OpenAiCompatibleLlmClient] against the resolved endpoint.
 *
 * The API key itself lives in
 * [com.meshlit.core.trust.CloudCredentialStore] under
 * `cloud-mcp/<credentialProviderId>/token`. The
 * [credentialProviderId] is user-overridable so a user with
 * multiple endpoint credentials (e.g. one for OpenRouter, one
 * for a private vLLM) can swap them without re-saving keys.
 *
 * @property baseUrl OpenAI-compatible `/v1/chat/completions`
 *   endpoint, e.g. `https://openrouter.ai/api`.
 * @property apiKey Decrypted API key (or empty string when
 *   nothing is configured).
 * @property model Slug the agent loop sends on every request.
 * @property credentialProviderId ProviderId under which the key
 *   is stored in [com.meshlit.core.trust.CloudCredentialStore].
 */
data class LlmEndpointConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val credentialProviderId: String,
) {
    /**
     * Build an [OpenAiCompatibleLlmClient] ready to stream a
     * chat completion. The caller owns the returned client.
     */
    fun buildClient(
        httpClient: okhttp3.OkHttpClient,
    ): OpenAiCompatibleLlmClient = OpenAiCompatibleLlmClient(
        httpClient = httpClient,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
    )

    companion object {
        /** Default — NaraRouter with no credential. */
        fun defaults(): LlmEndpointConfig = LlmEndpointConfig(
            baseUrl = OpenAiCompatibleModel.DEFAULT_BASE_URL,
            apiKey = "",
            model = OpenAiCompatibleModel.Default.slug,
            credentialProviderId = OpenAiCompatibleModel.DEFAULT_PROVIDER_ID,
        )
    }
}
package com.meshlit.core.cloudmcp.web

/**
 * One user's "active web-search" configuration. The Cloud Hub
 * UI lets the user pick one of [WebSearchVendor] and the
 * encrypted API key lives under
 * `cloud-mcp/web-search-<vendor>/token` in
 * [com.meshlit.core.trust.CloudCredentialStore].
 *
 * For [WebSearchVendor.DuckDuckGo], search is completely free with
 * zero setup — no API key or token is required.
 *
 * `cx` is Google-CSE only; `customEndpoint` and `customHeaderName`
 * apply to Custom / universal provider.
 *
 * `kDefault` is the default number of results the
 * `web_search` tool returns when the LLM doesn't override.
 */
data class WebSearchSettings(
    val vendor: WebSearchVendor = WebSearchVendor.DuckDuckGo,
    val apiKeyRef: String = "",
    val cx: String? = null,
    val customEndpoint: String? = null,
    val customHeaderName: String? = null,
    val kDefault: Int = 5,
) {
    val isConfigured: Boolean
        get() = when (vendor) {
            WebSearchVendor.DuckDuckGo -> true
            WebSearchVendor.GoogleCse -> !cx.isNullOrBlank()
            WebSearchVendor.Custom -> !customEndpoint.isNullOrBlank()
            WebSearchVendor.SearXNG -> true
            else -> apiKeyRef.isNotBlank()
        }

    companion object {
        const val DEFAULT_PROVIDER_ID = "web-search"
    }
}

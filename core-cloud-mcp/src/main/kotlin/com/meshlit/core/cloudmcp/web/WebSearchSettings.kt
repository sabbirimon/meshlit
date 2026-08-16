package com.meshlit.core.cloudmcp.web

/**
 * One user's "active web-search" configuration. The Cloud Hub
 * UI lets the user pick one of [WebSearchVendor] and the
 * encrypted API key lives under
 * `cloud-mcp/web-search-<vendor>/token` in
 * [com.meshlit.core.trust.CloudCredentialStore].
 *
 * `cx` is Google-CSE only; the dispatcher ignores it for every
 * other vendor. Keeping it here (rather than on the vendor
 * sealed class) means swapping the active vendor doesn't
 * require a round-trip through Settings.
 *
 * `kDefault` is the default number of results the
 * `web_search` tool returns when the LLM doesn't override.
 */
data class WebSearchSettings(
    val vendor: WebSearchVendor,
    val apiKeyRef: String,
    val cx: String? = null,
    val kDefault: Int = 5,
) {
    val isConfigured: Boolean
        get() = vendor != WebSearchVendor.GoogleCse || cx != null

    companion object {
        const val DEFAULT_PROVIDER_ID = "web-search"
    }
}

package com.meshlit.core.cloudmcp.web

/**
 * One result row from any web-search provider. The shape is
 * the unified contract every provider normalises to before
 * returning to the agent loop — the LLM never sees a
 * provider-specific field.
 *
 * @property title Page / article title.
 * @property url Canonical URL (used by the agent to follow up
 * with `http_tool` / `browser_open` calls).
 * @property snippet Plain-text excerpt; typically ~300 chars.
 * @property score Optional provider-side relevance (0.0..1.0).
 * Not all providers expose a score; missing scores surface as
 * `null` and the agent loop falls back to result ordering.
 */
data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val score: Float? = null,
)

/**
 * Vendor kind for the [WebSearchProvider] union. The agent-loop
 * dispatch table maps this to the right [WebSearchProvider]
 * implementation; the UI uses it to render the provider chip
 * in the Cloud Hub Tools row.
 */
enum class WebSearchVendor(val displayName: String) {
    DuckDuckGo("DuckDuckGo (Free / Zero-Setup)"),
    Bing("Bing Web Search"),
    Brave("Brave Search"),
    Serper("Serper.dev"),
    Tavily("Tavily"),
    GoogleCse("Google Programmable Search"),
    Perplexity("Perplexity Search"),
    SearXNG("SearXNG"),
    Custom("Custom Search Provider"),
}

/**
 * Sealed class of every supported web-search provider. Each
 * variant carries the bits it needs to make one
 * `search(query, k)` round-trip — an HTTP client, the
 * credentials, and any vendor-specific knobs (Google CSE
 * needs a `cx`; Bing needs an `endpoint` path; DuckDuckGo
 * needs zero setup / no API key).
 *
 * Wire shapes:
 *
 *  - **DuckDuckGo** — `POST https://html.duckduckgo.com/html/` with
 *    zero setup and no API key required; parses instant titles, links and snippets.
 *  - **Bing** — `GET {endpoint}/v7.0/search?q=...&count=k` with
 *    `Ocp-Apim-Subscription-Key` header.
 *  - **Brave** — `GET https://api.search.brave.com/res/v1/web/search?q=...&count=k`
 *    with `X-Subscription-Token` header.
 *  - **Serper** — `POST https://google.serper.dev/search` with
 *    `X-API-KEY` header and JSON body `{"q": "...", "n": k}`.
 *  - **Tavily** — `POST https://api.tavily.com/search` with
 *    `Authorization: Bearer ...` and JSON body
 *    `{"query": "...", "max_results": k}`.
 *  - **GoogleCse** — `GET https://www.googleapis.com/customsearch/v1?q=...&key=...&cx=...&num=k`.
 *  - **Perplexity** — `POST https://api.perplexity.ai/chat/completions` with
 *    `Authorization: Bearer ...` and Sonar search model.
 *  - **SearXNG** — `GET {endpoint}/search?q=...&format=json`.
 *  - **Custom** — User-configured endpoint (GET or POST) with arbitrary API key,
 *    custom auth header, and flexible JSON response parsing.
 *
 * The [search] contract is identical across vendors — the LLM
 * sees a single `web_search(query, k)` tool regardless of which
 * provider is configured.
 */
sealed class WebSearchProvider {
    abstract val providerId: String
    abstract val vendor: WebSearchVendor

    /**
     * DuckDuckGo search provider. Zero setup, no registration or API key required.
     */
    data class DuckDuckGo(
        override val providerId: String = "duckduckgo",
        val endpoint: String = "https://html.duckduckgo.com/html/",
    ) : WebSearchProvider() {
        override val vendor = WebSearchVendor.DuckDuckGo
    }

    data class Bing(
        override val providerId: String,
        val apiKey: String,
        val endpoint: String = "https://api.bing.microsoft.com",
    ) : WebSearchProvider() {
        override val vendor = WebSearchVendor.Bing
    }

    data class Brave(
        override val providerId: String,
        val apiKey: String,
        val endpoint: String = "https://api.search.brave.com",
    ) : WebSearchProvider() {
        override val vendor = WebSearchVendor.Brave
    }

    data class Serper(
        override val providerId: String,
        val apiKey: String,
        val endpoint: String = "https://google.serper.dev",
    ) : WebSearchProvider() {
        override val vendor = WebSearchVendor.Serper
    }

    data class Tavily(
        override val providerId: String,
        val apiKey: String,
        val endpoint: String = "https://api.tavily.com",
    ) : WebSearchProvider() {
        override val vendor = WebSearchVendor.Tavily
    }

    data class GoogleCse(
        override val providerId: String,
        val apiKey: String,
        val cx: String,
        val endpoint: String = "https://www.googleapis.com",
    ) : WebSearchProvider() {
        override val vendor = WebSearchVendor.GoogleCse
    }

    data class Perplexity(
        override val providerId: String,
        val apiKey: String,
        val endpoint: String = "https://api.perplexity.ai",
    ) : WebSearchProvider() {
        override val vendor = WebSearchVendor.Perplexity
    }

    data class SearXNG(
        override val providerId: String,
        val endpoint: String = "https://searx.be",
        val apiKey: String? = null,
    ) : WebSearchProvider() {
        override val vendor = WebSearchVendor.SearXNG
    }

    /**
     * Universal custom search provider allowing any API key, custom endpoint,
     * custom headers, and request methods.
     */
    data class Custom(
        override val providerId: String,
        val endpoint: String,
        val apiKey: String? = null,
        val headerName: String = "Authorization",
        val authPrefix: String = "Bearer ",
        val queryParam: String = "q",
        val isPost: Boolean = false,
    ) : WebSearchProvider() {
        override val vendor = WebSearchVendor.Custom
    }
}

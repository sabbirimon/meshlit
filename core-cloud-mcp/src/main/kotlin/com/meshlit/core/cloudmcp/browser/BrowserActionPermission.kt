package com.meshlit.core.cloudmcp.browser

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Per-action permission policy for browser automation. Every
 * `browser_*` tool call goes through [evaluate] before
 * [BrowserAutomationClient.invoke] fires. The policy returns a
 * [Decision]:
 *   - [Decision.Allow] — dispatch immediately.
 *   - [Decision.Ask] — surface a confirmation dialog before
 *     dispatching.
 *   - [Decision.Deny] — refuse with `user-denied`.
 *
 * Two safety nets:
 *   1. **Form-submit heuristic** — clicks / navigations that
 *      look like they submit a form (URL contains `?submit`,
 *      selector matches `button[type=submit]`, or the URL has
 *      query parameters with a `q=` style search) escalate to
 *      [Decision.Ask] even if the domain is allowlisted.
 *   2. **Domain allowlist** — the user can mark a domain
 *      (e.g. `github.com`) as "always allow" via
 *      [SettingsRepository.cloudBrowserAllowlist]. Hits skip the
 *      banner unless the form-submit heuristic escalates.
 *
 * The agent loop receives [Decision.Ask] as
 * [McpEvent.PermissionRequest] and shows a Material 3 dialog;
 * the user accepts or denies, and the loop continues with
 * `Decision.Allow` or surfaces `ToolResult(ok = false, body =
 * "user-denied")`.
 */
class BrowserActionPermission(
    private val allowlistedDomains: Set<String> = emptySet(),
) {

    /**
     * Evaluate [toolName] / [args] / [targetUrl] against the
     * policy. [targetUrl] is the post-resolution URL the action
     * is going to touch — for `browser_navigate` it's the URL
     * itself; for `browser_click` it's the page's current URL.
     */
    fun evaluate(
        toolName: String,
        args: JsonObject,
        targetUrl: String?,
    ): Decision {
        // Form-submit heuristic — escalate before allowlist check.
        if (toolName == BrowserAutomationClient.TOOL_CLICK && isFormSubmit(args, targetUrl)) {
            return Decision.Ask
        }
        if (toolName == BrowserAutomationClient.TOOL_NAVIGATE && targetUrl != null) {
            if (isLikelyFormPost(targetUrl)) return Decision.Ask
        }
        // Domain allowlist hit.
        if (targetUrl != null && allowlistedDomains.any { targetUrl.matchesDomain(it) }) {
            return Decision.Allow
        }
        return Decision.Ask
    }

    private fun isFormSubmit(args: JsonObject, targetUrl: String?): Boolean {
        val selector = (args["selector"] as? JsonPrimitive)?.content.orEmpty()
        if (selector.contains("type=submit", ignoreCase = true)) return true
        if (selector.contains("[type='submit']", ignoreCase = true)) return true
        if (selector.contains("button[", ignoreCase = true) &&
            selector.contains("submit", ignoreCase = true)) return true
        // Form-submit URL heuristic — POST-style query strings.
        if (targetUrl != null && isLikelyFormPost(targetUrl)) return true
        return false
    }

    private fun isLikelyFormPost(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("?submit") ||
            lower.contains("&submit") ||
            lower.contains("action=submit") ||
            (lower.contains("?q=") && lower.contains("&submit="))
    }

    enum class Decision { Allow, Ask, Deny }
}

private fun String.matchesDomain(domain: String): Boolean {
    val lower = this.lowercase()
    val d = domain.lowercase().removePrefix("*.")
    return lower.startsWith("https://$d/") ||
        lower.startsWith("http://$d/") ||
        lower == "https://$d" ||
        lower == "http://$d"
}

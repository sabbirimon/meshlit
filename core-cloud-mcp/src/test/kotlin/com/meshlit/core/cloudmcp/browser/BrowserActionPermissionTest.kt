package com.meshlit.core.cloudmcp.browser

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the [BrowserActionPermission] policy. Verifies
 * default-deny, domain allowlist, and the form-submit heuristic.
 */
class BrowserActionPermissionTest {

    @Test
    fun unknown_domain_returns_ask() {
        val policy = BrowserActionPermission()
        val decision = policy.evaluate(
            toolName = BrowserAutomationClient.TOOL_NAVIGATE,
            args = buildJsonObject { put("url", "https://example.com/") },
            targetUrl = "https://example.com/",
        )
        assertEquals(BrowserActionPermission.Decision.Ask, decision)
    }

    @Test
    fun allowlisted_domain_returns_allow() {
        val policy = BrowserActionPermission(allowlistedDomains = setOf("github.com"))
        val decision = policy.evaluate(
            toolName = BrowserAutomationClient.TOOL_NAVIGATE,
            args = buildJsonObject { put("url", "https://github.com/foo/bar") },
            targetUrl = "https://github.com/foo/bar",
        )
        assertEquals(BrowserActionPermission.Decision.Allow, decision)
    }

    @Test
    fun form_submit_click_returns_ask_even_when_allowlisted() {
        val policy = BrowserActionPermission(allowlistedDomains = setOf("github.com"))
        val decision = policy.evaluate(
            toolName = BrowserAutomationClient.TOOL_CLICK,
            args = buildJsonObject { put("selector", "button[type='submit']") },
            targetUrl = "https://github.com/login",
        )
        assertEquals(BrowserActionPermission.Decision.Ask, decision)
    }

    @Test
    fun form_submit_url_returns_ask() {
        val policy = BrowserActionPermission()
        val decision = policy.evaluate(
            toolName = BrowserAutomationClient.TOOL_NAVIGATE,
            args = buildJsonObject { put("url", "https://example.com/?q=foo&submit=Go") },
            targetUrl = "https://example.com/?q=foo&submit=Go",
        )
        assertEquals(BrowserActionPermission.Decision.Ask, decision)
    }

    @Test
    fun screenshot_returns_ask_when_no_allowlist() {
        val policy = BrowserActionPermission()
        val decision = policy.evaluate(
            toolName = BrowserAutomationClient.TOOL_SCREENSHOT,
            args = buildJsonObject {},
            targetUrl = "https://example.com/",
        )
        assertEquals(BrowserActionPermission.Decision.Ask, decision)
    }
}
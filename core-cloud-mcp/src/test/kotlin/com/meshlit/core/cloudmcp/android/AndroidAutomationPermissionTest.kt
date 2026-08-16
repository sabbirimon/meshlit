package com.meshlit.core.cloudmcp.android

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [AndroidAutomationPermission] — verifies the
 * allowlist, sensitive-target, and high-risk policy paths.
 */
class AndroidAutomationPermissionTest {

    @Test
    fun allowlist_hit_returns_allow() {
        val policy = AndroidAutomationPermission(allowlist = setOf("com.google.android.gm"))
        val req = AutomationRequest.Snapshot("com.google.android.gm")
        assertEquals(AndroidAutomationPermission.Decision.Allow, policy.evaluate(req))
    }

    @Test
    fun password_field_always_returns_ask_even_when_allowlisted() {
        val policy = AndroidAutomationPermission(
            allowlist = setOf("com.google.android.gm"),
            highRiskPackages = emptySet(),
        )
        val req = AutomationRequest.TypeRequest(
            targetPackage = "com.google.android.gm",
            text = "secret",
            isPasswordField = true,
        )
        assertEquals(AndroidAutomationPermission.Decision.Ask, policy.evaluate(req))
    }

    @Test
    fun high_risk_package_returns_ask_even_when_allowlisted() {
        val policy = AndroidAutomationPermission(
            allowlist = setOf("com.android.settings"),
            highRiskPackages = setOf("com.android.settings"),
        )
        val req = AutomationRequest.Snapshot("com.android.settings")
        assertEquals(AndroidAutomationPermission.Decision.Ask, policy.evaluate(req))
    }

    @Test
    fun unknown_package_defaults_to_ask() {
        val policy = AndroidAutomationPermission()
        val req = AutomationRequest.Snapshot("com.example.unknown")
        assertEquals(AndroidAutomationPermission.Decision.Ask, policy.evaluate(req))
    }

    @Test
    fun click_into_allowlisted_app_returns_allow() {
        val policy = AndroidAutomationPermission(allowlist = setOf("com.google.android.gm"))
        val req = AutomationRequest.ClickRequest(
            targetPackage = "com.google.android.gm",
            text = "Archive",
        )
        assertEquals(AndroidAutomationPermission.Decision.Allow, policy.evaluate(req))
    }

    @Test
    fun type_into_allowlisted_app_returns_allow_when_not_password() {
        val policy = AndroidAutomationPermission(allowlist = setOf("com.google.android.gm"))
        val req = AutomationRequest.TypeRequest(
            targetPackage = "com.google.android.gm",
            text = "hello",
            isPasswordField = false,
        )
        assertEquals(AndroidAutomationPermission.Decision.Allow, policy.evaluate(req))
    }
}